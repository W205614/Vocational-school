package com.tianji.media.cloud;

import com.tianji.common.exceptions.*;
import com.tianji.media.storage.local.LocalObjectStore;
import com.tianji.media.storage.local.FfprobeMediaDurationProbe;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable, content-versioned trial outputs. The cloud API never runs native tools. */
@Component
@ConditionalOnExpression("${tj.local-storage.enabled:false} or ${tj.cloud-media.enabled:false}")
public class TrialMediaJobs {
    private final JdbcTemplate jdbc;
    private final LocalObjectStore local;
    private final CloudObjectStore cloud;
    private final Environment env;
    private final boolean worker;
    private final Path work;
    private final String ffmpeg;
    private final AtomicBoolean localBusy = new AtomicBoolean();
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    r -> {
                        var thread = new Thread(r, "local-media-trial");
                        thread.setDaemon(true);
                        return thread;
                    });

    public TrialMediaJobs(
            JdbcTemplate jdbc,
            ObjectProvider<LocalObjectStore> local,
            ObjectProvider<CloudObjectStore> cloud,
            Environment env) {
        this.jdbc = jdbc;
        this.local = local.getIfAvailable();
        this.cloud = cloud.getIfAvailable();
        this.env = env;
        worker = "worker".equals(env.getProperty("tj.cloud-media.role"));
        work =
                Path.of(
                                env.getProperty(
                                        "tj.media.trial-work-directory",
                                        env.getProperty(
                                                "tj.cloud-media.worker-directory",
                                                worker
                                                        ? "/work/media"
                                                        : System.getProperty("java.io.tmpdir"))))
                        .toAbsolutePath()
                        .normalize();
        ffmpeg =
                env.getProperty(
                        "tj.media.trial-ffmpeg-command",
                        env.getProperty("tj.cloud-media.ffmpeg-command", "ffmpeg"));
        if (ffmpeg.isBlank() || ffmpeg.indexOf('\0') >= 0)
            throw new IllegalArgumentException("A trial transcoder is required");
    }

    public Map<String, Object> source(long media) {
        var rows =
                jdbc.queryForList(
                        "SELECT id,file_id,size,deleted FROM media WHERE id=? AND deleted=0",
                        media);
        if (rows.isEmpty()) throw new BadRequestException("媒资不存在");
        var result = new HashMap<String, Object>(rows.getFirst());
        String file = result.get("file_id").toString();
        if (local != null && file.matches("[0-9a-f]{64}\\.(mp4|webm)")) {
            result.put("source_version", file.substring(0, 64));
        } else if (cloud != null && file.matches("cos:[0-9a-f-]{36}")) {
            var ready =
                    jdbc.queryForList(
                            "SELECT object_key,object_version,object_etag,output_sha256,output_size FROM cloud_media_upload WHERE id=? AND state='READY'",
                            file.substring(4));
            if (ready.isEmpty()) throw new BadRequestException("云媒资尚未就绪");
            result.putAll(ready.getFirst());
            result.put("source_version", result.get("output_sha256"));
            result.put("size", result.get("output_size"));
        } else throw new BadRequestException("当前媒资不支持受控试看");
        return result;
    }

    public Map<String, Object> request(long media, int seconds) {
        if (seconds < 1 || seconds > 1800) throw new BadRequestException("试看时长无效");
        var source = source(media);
        String file = source.get("file_id").toString(),
                revision = source.get("source_version").toString();
        String id = identity(media, file, revision, seconds);
        jdbc.update(
                "INSERT IGNORE INTO media_trial(id,media_id,source_file,source_version,source_key,source_object_version,source_etag,source_size,duration_seconds) VALUES(?,?,?,?,?,?,?,?,?)",
                id,
                media,
                file,
                revision,
                source.get("object_key"),
                source.get("object_version"),
                source.get("object_etag"),
                source.get("size"),
                seconds);
        return task(id);
    }

    public Map<String, Object> task(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new BadRequestException("试看任务无效");
        var rows = jdbc.queryForList("SELECT * FROM media_trial WHERE id=?", id);
        if (rows.isEmpty()) throw new BadRequestException("试看任务不存在");
        return rows.getFirst();
    }

    public void removeDerived(String source) {
        // Revoke leases before storage cleanup so an in-flight job cannot publish a live ticket.
        jdbc.update(
                "UPDATE media_trial SET state='REMOVED',lease_token=NULL,lease_until=NULL,version=version+1 WHERE source_file=? AND state<>'REMOVED'",
                source);
        for (var task :
                jdbc.queryForList(
                        "SELECT id,object_key FROM media_trial WHERE source_file=?", source)) {
            if (local != null) local.delete("trial-" + task.get("id") + ".mp4");
            else if (task.get("object_key") != null)
                cloud.delete(task.get("object_key").toString());
        }
    }

    public static String identity(long media, String file, String revision, int seconds) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (media + "\n" + file + "\n" + revision + "\n" + seconds)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Scheduled(fixedDelayString = "${tj.media.trial-poll-interval-ms:1000}")
    public void pollLocal() {
        if (local == null || !localBusy.compareAndSet(false, true)) return;
        executor.execute(
                () -> {
                    try {
                        processNext();
                    } finally {
                        localBusy.set(false);
                    }
                });
    }

    /** Shares the cloud worker's serial processing loop with uploads. */
    public boolean processNext() {
        if (local == null && !worker) return false;
        var candidates =
                jdbc.queryForList(
                        "SELECT id FROM media_trial WHERE state='PENDING' OR (state='PROCESSING' AND lease_until<NOW(3)) ORDER BY created_at LIMIT 1");
        if (candidates.isEmpty()) return false;
        process(candidates.getFirst().get("id").toString());
        return true;
    }

    void process(String id) {
        String token = UUID.randomUUID().toString();
        if (jdbc.update(
                        "UPDATE media_trial SET state='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=NOW(3)+INTERVAL 15 MINUTE,error_code=NULL,version=version+1 WHERE id=? AND attempts<3 AND (state='PENDING' OR (state='PROCESSING' AND lease_until<NOW(3)))",
                        token,
                        id)
                != 1) {
            jdbc.update(
                    "UPDATE media_trial SET state='FAILED',error_code='TRIAL_RETRY_EXHAUSTED',version=version+1 WHERE id=? AND attempts>=3 AND state IN('PENDING','PROCESSING') AND (lease_until IS NULL OR lease_until<NOW(3))",
                    id);
            return;
        }
        Path directory = null;
        try {
            var task = task(id);
            var current = source(((Number) task.get("media_id")).longValue());
            if (!Objects.equals(current.get("file_id"), task.get("source_file"))
                    || !Objects.equals(current.get("source_version"), task.get("source_version")))
                throw new BadRequestException("TRIAL_SOURCE_CHANGED");
            Files.createDirectories(work);
            directory = Files.createTempDirectory(work, "trial-");
            Path input =
                    directory.resolve(
                            "source"
                                    + (task.get("source_file").toString().endsWith("webm")
                                            ? ".webm"
                                            : ".mp4"));
            long expected = ((Number) task.get("source_size")).longValue();
            try (InputStream stream =
                    local != null
                            ? local.download(task.get("source_file").toString())
                            : cloud.read(
                                    task.get("source_key").toString(),
                                    (String) task.get("source_object_version"),
                                    (String) task.get("source_etag"),
                                    0,
                                    expected - 1)) {
                copySource(stream, input, expected);
            }
            Path clip = directory.resolve("clip.mp4");
            int seconds = ((Number) task.get("duration_seconds")).intValue();
            Runnable lease =
                    () -> {
                        if (Thread.currentThread().isInterrupted()
                                || jdbc.queryForObject(
                                                "SELECT COUNT(*) FROM media_trial WHERE id=? AND state='PROCESSING' AND lease_token=? AND lease_until>NOW(3)",
                                                Integer.class,
                                                id,
                                                token)
                                        != 1) throw new IllegalStateException("TRIAL_LEASE_LOST");
                    };
            createClip(input, clip, seconds, ffmpeg, env, lease);
            long size = Files.size(clip);
            float duration =
                    new FfprobeMediaDurationProbe(env, JsonMapper.builder().build())
                            .seconds(clip, "clip.mp4");
            String sha = CloudMediaProcessor.sha256(clip), object;
            String objectVersion = null, etag = null;
            lease.run();
            if (local != null) {
                local.saveTrial(id, clip);
                if (!CloudMediaProcessor.sha256(local.trialPath(id)).equals(sha))
                    throw new BadRequestException("TRIAL_OUTPUT_CONFLICT");
                object = "trial-" + id + ".mp4";
            } else {
                object = "ready/trials/" + id + "/" + token + ".mp4";
                var uploaded = cloud.putReady(object, clip);
                if (uploaded.size() != size
                        || !"video/mp4".equals(uploaded.contentType())
                        || uploaded.etag() == null)
                    throw new IllegalStateException("TRIAL_UPLOAD_MISMATCH");
                objectVersion = uploaded.version();
                etag = uploaded.etag();
            }
            lease.run();
            jdbc.update(
                    "UPDATE media_trial SET state='READY',object_key=?,object_version=?,object_etag=?,output_size=?,output_sha256=?,output_duration=?,lease_token=NULL,lease_until=NULL,version=version+1 WHERE id=? AND state='PROCESSING' AND lease_token=? AND lease_until>NOW(3)",
                    object,
                    objectVersion,
                    etag,
                    size,
                    sha,
                    duration,
                    id,
                    token);
        } catch (Exception failure) {
            String code =
                    failure instanceof BadRequestException
                            ? failure.getMessage()
                            : "TRIAL_PROCESSING_FAILED";
            if (code == null || !code.matches("[A-Z_]{1,64}")) code = "TRIAL_PROCESSING_FAILED";
            jdbc.update(
                    "UPDATE media_trial SET state='FAILED',error_code=?,lease_token=NULL,lease_until=NULL,version=version+1 WHERE id=? AND lease_token=?",
                    code,
                    id,
                    token);
        } finally {
            if (directory != null) {
                try (var files = Files.walk(directory)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList())
                        Files.deleteIfExists(file);
                } catch (IOException failure) {
                    throw new IllegalStateException(
                            "Trial temporary files could not be released", failure);
                }
            }
        }
    }

    static void copySource(InputStream input, Path file, long size) throws IOException {
        if (size < 1 || size > 1024L * 1024 * 1024)
            throw new BadRequestException("TRIAL_SOURCE_LIMIT");
        long count = 0, deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
            byte[] bytes = new byte[65536];
            int read;
            while ((read = input.read(bytes)) != -1) {
                count += read;
                if (count > size || System.nanoTime() > deadline)
                    throw new BadRequestException("TRIAL_SOURCE_LIMIT");
                output.write(bytes, 0, read);
            }
        }
        if (count != size) throw new BadRequestException("TRIAL_SOURCE_INCOMPLETE");
    }

    static void createClip(
            Path source, Path clip, int seconds, String ffmpeg, Environment env, Runnable lease)
            throws IOException {
        if (seconds < 1 || seconds > 1800) throw new BadRequestException("TRIAL_DURATION_INVALID");
        CloudMediaProcessor.run(
                List.of(
                        ffmpeg,
                        "-nostdin",
                        "-v",
                        "error",
                        "-protocol_whitelist",
                        "file,pipe",
                        "-y",
                        "-i",
                        source.toString(),
                        "-t",
                        Integer.toString(seconds),
                        "-map",
                        "0:v:0",
                        "-map",
                        "0:a:0?",
                        "-sn",
                        "-dn",
                        "-vf",
                        "scale=w='min(1920,iw)':h=-2",
                        "-c:v",
                        "libx264",
                        "-preset",
                        "veryfast",
                        "-crf",
                        "23",
                        "-threads",
                        "2",
                        "-c:a",
                        "aac",
                        "-b:a",
                        "128k",
                        "-movflags",
                        "+faststart",
                        "-fs",
                        "209715200",
                        clip.toString()),
                600,
                lease);
        long size = Files.size(clip);
        float duration =
                new FfprobeMediaDurationProbe(env, JsonMapper.builder().build())
                        .seconds(clip, "clip.mp4");
        if (size < 1 || size >= 209715200L || duration > seconds + 0.1f)
            throw new BadRequestException("TRIAL_OUTPUT_INVALID");
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}

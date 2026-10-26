package com.tianji.media.storage.local;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.ServiceUnavailableException;
import com.tianji.common.exceptions.TooManyRequestsException;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Bounded native probing. ffprobe is supplied by the runtime image, not downloaded at request time. */
public final class FfprobeMediaDurationProbe implements MediaDurationProbe {
    private final String executable;
    private final JsonMapper json;
    private final Semaphore permits;
    private final int timeoutSeconds;
    static final int METADATA_LIMIT = 65536;
    static final int PACKET_LIMIT = 16 * 1024 * 1024;

    public FfprobeMediaDurationProbe(Environment env, JsonMapper json) {
        executable = env.getProperty("tj.local-storage.ffprobe-command", "ffprobe");
        if (executable.isBlank() || executable.indexOf('\0') >= 0)
            throw new IllegalArgumentException("A valid ffprobe executable is required");
        timeoutSeconds = env.getProperty("tj.local-storage.probe-timeout-seconds", Integer.class, 30);
        int concurrency = env.getProperty("tj.local-storage.probe-concurrency", Integer.class, 2);
        if (timeoutSeconds < 1 || timeoutSeconds > 60 || concurrency < 1 || concurrency > 4)
            throw new IllegalArgumentException("Probe timeout/concurrency are outside the supported bounds");
        permits = new Semaphore(concurrency, true);
        this.json = json;
    }

    @Override public float seconds(Path file, String filename) {
        if (!permits.tryAcquire()) throw new TooManyRequestsException("视频时长探测繁忙，请稍后重试");
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            List<String> base = List.of(executable, "-v", "error", "-protocol_whitelist", "file,pipe",
                    "-max_alloc", "67108864", "-probesize", "16777216", "-analyzeduration", "10000000");
            var command = new ArrayList<>(base);
            command.addAll(List.of("-select_streams", "v:0", "-show_entries",
                    "format=format_name,duration:stream=codec_type,codec_name,duration,r_frame_rate", "-of", "json", "-i", file.toString()));
            String output = run(command, remainingMillis(deadline), METADATA_LIMIT);
            Map<?,?> metadata;
            try { metadata = json.readValue(output, Map.class); }
            catch (RuntimeException error) { throw invalid(); }
            ProbeInfo info = inspect(metadata, filename);
            if (info.duration() != null) return valid(info.duration());
            // Chrome MediaRecorder WebM commonly omits both Info.Duration and stream duration.
            // Read packet presentation times instead; no decoding, transcoding or network input.
            command = new ArrayList<>(base);
            command.addAll(List.of("-select_streams", "v:0", "-show_entries", "packet=pts_time,duration_time",
                    "-of", "csv=p=0", "-i", file.toString()));
            return valid(packetDuration(run(command, remainingMillis(deadline), PACKET_LIMIT), info.framePeriod()));
        } finally { permits.release(); }
    }

    private static long remainingMillis(long deadline) {
        long remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
        if(remaining<=0)throw new BadRequestException("视频时长探测超时，请检查文件");
        return remaining;
    }

    record ProbeInfo(Double duration, Double framePeriod) {}
    static ProbeInfo inspect(Map<?,?> value, String filename) {
        Object streamsValue = value.get("streams"), formatValue = value.get("format");
        if (!(streamsValue instanceof List<?> streams) || streams.isEmpty()
                || !(streams.getFirst() instanceof Map<?,?> stream) || !"video".equals(stream.get("codec_type"))
                || !(formatValue instanceof Map<?,?> format)) throw invalid();
        String container = Objects.toString(format.get("format_name"), "");
        String name = Objects.toString(filename, "").toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".mp4") && Arrays.asList(container.split(",")).contains("mp4"))
                && !(name.endsWith(".webm") && Arrays.asList(container.split(",")).contains("webm"))) throw invalid();
        String codec=Objects.toString(stream.get("codec_name"),"");
        if (!(name.endsWith(".mp4") && codec.equals("h264"))
                && !(name.endsWith(".webm") && Set.of("vp8","vp9").contains(codec)))
            throw new BadRequestException("本地播放仅支持 H.264 MP4 或 VP8／VP9 WebM 视频，请先转换格式");
        Double duration = positive(format.get("duration"));
        if (duration == null) duration = positive(stream.get("duration"));
        Double period = null;
        String rate = Objects.toString(stream.get("r_frame_rate"), "");
        String[] parts = rate.split("/");
        if (parts.length == 2) {
            Double numerator = positive(parts[0]), denominator = positive(parts[1]);
            if (numerator != null && denominator != null) {
                double fps = numerator / denominator;
                if (fps >= 0.1 && fps <= 1000) period = 1 / fps;
            }
        }
        return new ProbeInfo(duration, period);
    }

    static double packetDuration(String output, Double framePeriod) {
        double first = Double.POSITIVE_INFINITY, last = Double.NEGATIVE_INFINITY, end = Double.NEGATIVE_INFINITY;
        double prior = Double.NaN;
        List<Double> intervals = new ArrayList<>();
        int packets = 0;
        for (String line : output.split("\\R")) {
            String[] fields = line.split(",", -1);
            Double pts = finite(fields[0]);
            if (pts == null) continue;
            if (++packets > 1_000_000) throw invalid();
            Double duration = fields.length > 1 ? positive(fields[1]) : null;
            first = Math.min(first, pts); last = Math.max(last, pts);
            if (duration != null) end = Math.max(end, pts + duration);
            if (Double.isFinite(prior) && pts > prior && pts - prior <= 10) {
                if (intervals.size() == 63) intervals.removeFirst();
                intervals.add(pts - prior);
            }
            prior = pts;
        }
        if (packets == 0) throw invalid();
        if (end <= last) {
            // Missing packet duration: use the actual observed cadence, then the track rate.
            Double tail = null;
            if (!intervals.isEmpty()) { Collections.sort(intervals); tail = intervals.get(intervals.size() / 2); }
            if (tail == null) tail = framePeriod;
            if (tail == null) throw invalid();
            end = last + tail;
        }
        return end - Math.min(0, first);
    }

    private static Double finite(Object value) {
        try { double number = Double.parseDouble(Objects.toString(value, "")); return Double.isFinite(number) ? number : null; }
        catch (NumberFormatException ignored) { return null; }
    }
    private static Double positive(Object value) { Double number = finite(value); return number != null && number > 0 ? number : null; }
    static float valid(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 86400) throw invalid();
        long millis=Math.round(seconds * 1000);
        if(millis<1)throw invalid();
        return (float)(millis / 1000.0);
    }
    private static BadRequestException invalid() { return new BadRequestException("无法读取有效 MP4／WebM 视频时长，请检查文件"); }

    static String run(List<String> command, long timeoutMillis, int outputLimit) {
        Process process;
        try { process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
        catch (IOException error) { throw new ServiceUnavailableException("视频探测工具不可用，请联系管理员"); }
        FutureTask<byte[]> reader = new FutureTask<>(() -> {
            try (InputStream input = process.getInputStream(); var bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > outputLimit) throw invalid();
                    bytes.write(buffer, 0, count);
                }
                return bytes.toByteArray();
            }
        });
        Thread.ofVirtual().name("media-probe-output").start(reader);
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) throw new BadRequestException("视频时长探测超时，请检查文件");
            byte[] bytes = reader.get(1000, TimeUnit.MILLISECONDS);
            if (process.exitValue() != 0) throw invalid();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new ServiceUnavailableException("视频时长探测已取消");
        } catch (ExecutionException | TimeoutException error) { throw invalid(); }
        finally {
            if (process.isAlive()) process.destroyForcibly();
            reader.cancel(true);
            try { process.getInputStream().close(); } catch (IOException ignored) {}
        }
    }
}

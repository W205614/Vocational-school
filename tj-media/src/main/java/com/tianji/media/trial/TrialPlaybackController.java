package com.tianji.media.trial;

import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import com.tianji.media.cloud.CloudObjectStore;
import com.tianji.media.cloud.TrialMediaJobs;
import com.tianji.media.storage.local.LocalObjectStore;
import com.tianji.media.storage.local.OwnerStreamLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.AbstractResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpRange;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

@RestController
@ConditionalOnExpression(
        "${tj.local-storage.enabled:false} or (${tj.cloud-media.enabled:false} and '${tj.cloud-media.role:api}' == 'api')")
public class TrialPlaybackController {
    private final TrialPlaybackService playback;
    private final TrialMediaJobs jobs;
    private final JdbcTemplate jdbc;
    private final LocalObjectStore local;
    private final CloudObjectStore cloud;
    private final OwnerStreamLimiter streams;

    public TrialPlaybackController(
            TrialPlaybackService playback,
            TrialMediaJobs jobs,
            JdbcTemplate jdbc,
            ObjectProvider<LocalObjectStore> local,
            ObjectProvider<CloudObjectStore> cloud,
            OwnerStreamLimiter streams) {
        this.playback = playback;
        this.jobs = jobs;
        this.jdbc = jdbc;
        this.local = local.getIfAvailable();
        this.cloud = cloud.getIfAvailable();
        this.streams = streams;
    }

    @GetMapping("/trial-content/{id}")
    public void content(
            @PathVariable String id,
            @RequestParam long section,
            @RequestParam long owner,
            @RequestParam long expires,
            @RequestParam String signature,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletResponse response)
            throws IOException {
        var task = playback.authorize(id, section, owner, expires, signature);
        long size = ((Number) task.get("output_size")).longValue();
        long[] selection;
        try {
            selection = range(range, size);
        } catch (IllegalArgumentException invalid) {
            response.setStatus(416);
            response.setHeader("Content-Range", "bytes */" + size);
            return;
        }
        long first = selection[0], last = selection[1], length = last - first + 1;
        var resource =
                new AbstractResource() {
                    public String getDescription() {
                        return "controlled-trial-clip";
                    }

                    public long contentLength() {
                        return length;
                    }

                    public InputStream getInputStream() throws IOException {
                        if (local == null)
                            return cloud.read(
                                    task.get("object_key").toString(),
                                    (String) task.get("object_version"),
                                    (String) task.get("object_etag"),
                                    first,
                                    last);
                        var input = Files.newInputStream(local.trialPath(id));
                        try {
                            input.skipNBytes(first);
                            return input;
                        } catch (IOException failure) {
                            input.close();
                            throw failure;
                        }
                    }
                };
        try (var input = streams.wrap(resource, owner).getInputStream()) {
            response.setStatus(range == null ? 200 : 206);
            response.setContentType("video/mp4");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Accept-Ranges", "bytes");
            response.setContentLengthLong(length);
            if (range != null)
                response.setHeader("Content-Range", "bytes " + first + "-" + last + "/" + size);
            byte[] buffer = new byte[65536];
            long remaining = length;
            var output = response.getOutputStream();
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) throw new EOFException("Trial clip ended early");
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
    }

    static long[] range(String header, long size) {
        if (size < 1) throw new IllegalArgumentException("Empty clip");
        if (header == null) return new long[] {0, size - 1};
        var ranges = HttpRange.parseRanges(header);
        if (ranges.size() != 1) throw new IllegalArgumentException("Single range required");
        long first = ranges.getFirst().getRangeStart(size),
                last = ranges.getFirst().getRangeEnd(size);
        if (first < 0 || first >= size || last < first || last >= size)
            throw new IllegalArgumentException("Range outside clip");
        return new long[] {first, last};
    }

    @GetMapping("/media-trials")
    public Object tasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserContext.requireAdmin();
        if (page < 1 || page > 100000 || size < 1 || size > 100)
            throw new BadRequestException("分页参数无效");
        return Map.of(
                "total",
                jdbc.queryForObject("SELECT COUNT(*) FROM media_trial", Long.class),
                "list",
                jdbc.queryForList(
                        "SELECT id,media_id,duration_seconds,state,error_code,version,created_at FROM media_trial ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
                        size,
                        (page - 1) * size));
    }

    public record Retry(long version) {}

    @PostMapping("/media-trials/{id}/retry")
    public void retry(@PathVariable String id, @RequestBody Retry form) {
        UserContext.requireAdmin();
        jobs.task(id);
        if (jdbc.update(
                        "UPDATE media_trial SET state='PENDING',attempts=0,error_code=NULL,lease_token=NULL,lease_until=NULL,version=version+1 WHERE id=? AND state='FAILED' AND version=?",
                        id,
                        form.version())
                != 1) throw new ConflictException("试看任务已变化或不是失败状态");
    }
}

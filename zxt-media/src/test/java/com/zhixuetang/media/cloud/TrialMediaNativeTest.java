package com.zhixuetang.media.cloud;

import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.media.storage.local.FfprobeMediaDurationProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the real transcoder and probe, rather than trusting ffmpeg command construction. */
class TrialMediaNativeTest {
    @TempDir Path directory;

    @Test
    void physicallyClipsRealVideoToTheTrialLimit() throws Exception {
        Path source = Path.of(getClass().getResource("/media-probe/recorder.webm").toURI());
        Path output = directory.resolve("clip.mp4");
        var environment = new MockEnvironment();
        TrialMediaJobs.createClip(source, output, 2, "ffmpeg", environment, () -> {});
        float duration =
                new FfprobeMediaDurationProbe(environment, JsonMapper.builder().build())
                        .seconds(output, "clip.mp4");
        assertTrue(duration >= 1.8 && duration <= 2.1, "Only the first two seconds may be present");
        assertTrue(Files.size(output) > 0);
        assertNotEquals(source, output);
    }

    @Test
    void refusesFalseSourceSizeAndInvalidTrialDuration() throws Exception {
        assertThrows(
                BadRequestException.class,
                () ->
                        TrialMediaJobs.copySource(
                                new ByteArrayInputStream(new byte[2]),
                                directory.resolve("short"),
                                3));
        assertThrows(
                BadRequestException.class,
                () ->
                        TrialMediaJobs.copySource(
                                new ByteArrayInputStream(new byte[3]),
                                directory.resolve("long"),
                                2));
        assertThrows(
                BadRequestException.class,
                () ->
                        TrialMediaJobs.createClip(
                                directory.resolve("source"),
                                directory.resolve("clip"),
                                0,
                                "ffmpeg",
                                new MockEnvironment(),
                                () -> {}));
    }
}

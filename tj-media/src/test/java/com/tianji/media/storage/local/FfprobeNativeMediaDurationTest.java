package com.tianji.media.storage.local;

import com.tianji.common.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deliberately requires ffprobe: CI installs it; missing native support must fail, not skip. */
class FfprobeNativeMediaDurationTest {
    @TempDir Path directory;
    private final FfprobeMediaDurationProbe probe=new FfprobeMediaDurationProbe(new MockEnvironment(),JsonMapper.builder().build());
    private Path resource(String name)throws Exception {return Path.of(getClass().getResource("/media-probe/"+name).toURI());}
    @Test void readsRealH264Mp4Duration()throws Exception {
        assertEquals(1.28,probe.seconds(resource("tiny.mp4"),"tiny.mp4"),0.05);
    }
    @Test void readsRealMediaRecorderWebmWithoutDurationMetadata()throws Exception {
        String metadata=FfprobeMediaDurationProbe.run(List.of("ffprobe","-v","error","-select_streams","v:0","-show_entries",
                "format=format_name,duration:stream=codec_type,codec_name,duration,r_frame_rate","-of","json",resource("recorder.webm").toString()),3000,65536);
        assertNull(FfprobeMediaDurationProbe.inspect(JsonMapper.builder().build().readValue(metadata,Map.class),"recorder.webm").duration(),"Fixture must exercise the packet fallback");
        assertEquals(6.1,probe.seconds(resource("recorder.webm"),"recorder.webm"),0.2);
    }
    @Test void rejectsFakeBytesAndDisguisedContainers()throws Exception {
        Path fake=Files.writeString(directory.resolve("fake.webm"),"video bytes");
        assertThrows(BadRequestException.class,()->probe.seconds(fake,"fake.webm"));
        assertThrows(BadRequestException.class,()->probe.seconds(resource("tiny.mp4"),"renamed.webm"));
    }
}

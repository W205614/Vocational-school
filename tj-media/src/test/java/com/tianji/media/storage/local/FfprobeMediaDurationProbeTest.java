package com.tianji.media.storage.local;

import com.tianji.common.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FfprobeMediaDurationProbeTest {
    @Test void metadataRequiresActualMatchingContainerAndVideo() {
        var value=Map.of("format",Map.of("format_name","mov,mp4,m4a,3gp,3g2,mj2","duration","1.280"),
                "streams",List.of(Map.of("codec_type","video","codec_name","h264","r_frame_rate","25/1")));
        assertEquals(1.28,FfprobeMediaDurationProbe.inspect(value,"video.mp4").duration(),0.0001);
        assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.inspect(value,"renamed.webm"));
        assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.inspect(Map.of("format",Map.of("format_name","mp4"),"streams",List.of()),"audio.mp4"));
        assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.inspect(Map.of("format",Map.of("format_name","mp4","duration","1"),
                "streams",List.of(Map.of("codec_type","video","codec_name","hevc"))),"hevc.mp4"));
    }
    @Test void webmWithoutDurationUsesPacketsIncludingTheFinalFrame() {
        assertEquals(6.1,FfprobeMediaDurationProbe.packetDuration("0.000,0.100\n3.000,0.100\n6.000,0.100\n",null),0.0001);
        assertEquals(6.1,FfprobeMediaDurationProbe.packetDuration("0.000,N/A\n0.100,N/A\n0.200,N/A\n6.000,N/A\n",null),0.0001);
        assertEquals(1.04,FfprobeMediaDurationProbe.packetDuration("1.000,N/A\n",0.04),0.0001);
        assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.packetDuration("N/A,N/A\n",null));
        assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.packetDuration("1.000,N/A\n",null));
    }
    @Test void invalidDurationAndConfigurationAreRejected() {
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,86400.1})
            assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.valid(invalid));
        assertEquals(1.28f,FfprobeMediaDurationProbe.valid(1.28000001));
        var json=JsonMapper.builder().build();
        assertThrows(IllegalArgumentException.class,()->new FfprobeMediaDurationProbe(new MockEnvironment().withProperty("tj.local-storage.probe-concurrency","5"),json));
        assertThrows(IllegalArgumentException.class,()->new FfprobeMediaDurationProbe(new MockEnvironment().withProperty("tj.local-storage.probe-timeout-seconds","0"),json));
    }
    @Test void processOutputAndTimeAreBoundedAndFailuresAreRejected() {
        assertEquals("ok",FfprobeMediaDurationProbe.run(child("ok"),3000,32));
        assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.run(child("large"),3000,32));
        assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.run(child("fail"),3000,32));
        long started=System.nanoTime();
        assertThrows(BadRequestException.class,()->FfprobeMediaDurationProbe.run(child("sleep"),150,32));
        assertTrue((System.nanoTime()-started)/1_000_000<2000,"Process must be terminated after timeout");
    }
    private static List<String> child(String mode) {
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        return List.of(java,"-cp",System.getProperty("java.class.path"),Child.class.getName(),mode);
    }
    public static class Child {
        public static void main(String[] args)throws Exception {
            switch(args[0]) { case "ok"->System.out.print("ok");case "large"->System.out.print("x".repeat(65536));case "fail"->System.exit(1);case "sleep"->Thread.sleep(10000);default->throw new IllegalArgumentException(); }
        }
    }
}

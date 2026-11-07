package com.zhixuetang.media.trial;

import com.zhixuetang.media.cloud.*;
import com.zhixuetang.media.storage.local.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import java.io.ByteArrayInputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrialPlaybackControllerTest {
    @Test
    void rangesCanOnlyReadThePinnedClipAndNeverOriginalBytes() throws Exception {
        var playback = mock(TrialPlaybackService.class);
        var cloud = mock(CloudObjectStore.class);
        var streams = mock(OwnerStreamLimiter.class);
        when(streams.wrap(any(), anyLong())).thenAnswer(call -> call.getArgument(0));
        var beans = new StaticListableBeanFactory(Map.of("cloud", cloud));
        var controller =
                new TrialPlaybackController(
                        playback,
                        mock(TrialMediaJobs.class),
                        mock(JdbcTemplate.class),
                        beans.getBeanProvider(LocalObjectStore.class),
                        beans.getBeanProvider(CloudObjectStore.class),
                        streams);
        String id = "a".repeat(64);
        when(playback.authorize(id, 3, 7, 123, "signature"))
                .thenReturn(
                        Map.of(
                                "output_size",
                                5L,
                                "object_key",
                                "ready/trials/clip.mp4",
                                "object_version",
                                "v1",
                                "object_etag",
                                "etag"));
        when(cloud.read("ready/trials/clip.mp4", "v1", "etag", 1, 3))
                .thenReturn(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        var response = new MockHttpServletResponse();
        controller.content(id, 3, 7, 123, "signature", "bytes=1-3", response);
        assertEquals(206, response.getStatus());
        assertArrayEquals(new byte[] {2, 3, 4}, response.getContentAsByteArray());
        assertEquals("bytes 1-3/5", response.getHeader("Content-Range"));
        verify(cloud).read("ready/trials/clip.mp4", "v1", "etag", 1, 3);
        var invalid = new MockHttpServletResponse();
        controller.content(id, 3, 7, 123, "signature", "bytes=5-999999", invalid);
        assertEquals(416, invalid.getStatus());
        assertEquals("bytes */5", invalid.getHeader("Content-Range"));
        verifyNoMoreInteractions(cloud);
        assertThrows(
                IllegalArgumentException.class,
                () -> TrialPlaybackController.range("bytes=0-1,3-4", 5));
    }
}

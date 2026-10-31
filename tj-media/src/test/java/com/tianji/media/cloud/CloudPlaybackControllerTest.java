package com.tianji.media.cloud;

import com.tianji.api.client.learning.LearningClient;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.UserContext;
import com.tianji.media.storage.local.OwnerStreamLimiter;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletResponse;
import java.io.ByteArrayInputStream;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudPlaybackControllerTest {
    private OwnerStreamLimiter streams;
    @BeforeEach void streams(){streams=mock(OwnerStreamLimiter.class);when(streams.wrap(any(),anyLong())).thenAnswer(call->call.getArgument(0));}
    @AfterEach void clear(){UserContext.removeUser();}
    @Test void invalidTicketFailsBeforeEntitlementAndObjectRead(){
        var storage=mock(CloudCosMediaStorage.class);var learning=mock(LearningClient.class);when(storage.authorize(any())).thenThrow(new ForbiddenException("invalid"));
        var controller=new CloudPlaybackController(storage,learning,streams);
        assertThrows(ForbiddenException.class,()->controller.content("bad",7,9,100,"bad",null,new MockHttpServletResponse()));verifyNoInteractions(learning);verify(storage,never()).content(any(),anyLong(),anyLong());
    }
    @Test void refundedEntitlementRejectsAnOtherwiseValidTicketAndRestoresContext(){
        var storage=mock(CloudCosMediaStorage.class);var learning=mock(LearningClient.class);when(storage.authorize(any())).thenReturn(Map.of("output_size",64));when(learning.isLessonValid(9L)).thenReturn(null);
        var controller=new CloudPlaybackController(storage,learning,streams);UserContext.setUser(99L);UserContext.setRole(1L);UserContext.setSession("session");UserContext.setCallDepth(2);
        assertThrows(ForbiddenException.class,()->controller.content("task",7,9,100,"valid",null,new MockHttpServletResponse()));verify(storage,never()).content(any(),anyLong(),anyLong());
        assertEquals(99L,UserContext.getUser());assertEquals(1L,UserContext.getRole());assertEquals("session",UserContext.getSession());assertEquals(2,UserContext.getCallDepth());
    }
    @Test void rangeStreamsOnlyRequestedBytesAfterRecheckingEntitlement()throws Exception{
        var storage=mock(CloudCosMediaStorage.class);var learning=mock(LearningClient.class);var row=Map.<String,Object>of("output_size",64);when(storage.authorize(any())).thenReturn(row);when(learning.isLessonValid(9L)).thenReturn(123L);when(storage.content(row,8,11)).thenReturn(new ByteArrayInputStream(new byte[]{8,9,10,11}));
        var controller=new CloudPlaybackController(storage,learning,streams);var response=new MockHttpServletResponse();controller.content("task",7,9,100,"valid","bytes=8-11",response);
        assertEquals(206,response.getStatus());assertEquals("bytes 8-11/64",response.getHeader("Content-Range"));assertEquals("no-store",response.getHeader("Cache-Control"));assertArrayEquals(new byte[]{8,9,10,11},response.getContentAsByteArray());verify(learning).isLessonValid(9L);
    }
    @Test void invalidMultipleAndUnsatisfiableRangesAreRejected(){
        assertArrayEquals(new long[]{0,63},CloudPlaybackController.range(null,64));assertArrayEquals(new long[]{60,63},CloudPlaybackController.range("bytes=-4",64));
        assertThrows(IllegalArgumentException.class,()->CloudPlaybackController.range("bytes=1-2,4-5",64));assertThrows(IllegalArgumentException.class,()->CloudPlaybackController.range("bytes=64-",64));
    }
}

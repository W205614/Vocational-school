package com.tianji.media.controller;

import com.tianji.common.exceptions.*;
import com.tianji.common.utils.InternalAuth;
import com.tianji.media.domain.po.Media;
import com.tianji.media.service.IMediaService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalMediaControllerTest {
    @Test void registeredFractionalDurationKeepsMillisecondPrecision() {
        var service=mock(IMediaService.class);var controller=new InternalMediaController(service);
        when(service.getById(1L)).thenReturn(new Media().setDuration(1.28f));
        try(var internal=mockStatic(InternalAuth.class)) {
            assertEquals(1280L,controller.durationMillis(1L));internal.verify(InternalAuth::requireService);
        }
    }
    @Test void missingAndInvalidMetadataCannotFallBackToCatalogueSeconds() {
        var service=mock(IMediaService.class);var controller=new InternalMediaController(service);
        try(var internal=mockStatic(InternalAuth.class)) {
            assertThrows(BadRequestException.class,()->controller.durationMillis(1L));
            for(float duration:new float[]{0f,-1f,Float.NaN,Float.POSITIVE_INFINITY,86401f}) {
                when(service.getById(1L)).thenReturn(new Media().setDuration(duration));
                assertThrows(BadRequestException.class,()->controller.durationMillis(1L));
            }
            assertThrows(BadRequestException.class,()->controller.durationMillis(0L));
        }
    }
    @Test void serviceAuthenticationPrecedesDatabaseLookup() {
        var service=mock(IMediaService.class);var controller=new InternalMediaController(service);
        try(var internal=mockStatic(InternalAuth.class)) {
            internal.when(InternalAuth::requireService).thenThrow(new ForbiddenException("service required"));
            assertThrows(ForbiddenException.class,()->controller.durationMillis(1L));
        }
        verifyNoInteractions(service);
    }
}

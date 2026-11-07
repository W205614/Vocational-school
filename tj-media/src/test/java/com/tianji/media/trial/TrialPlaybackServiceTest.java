package com.tianji.media.trial;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.*;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import com.tianji.media.cloud.TrialMediaJobs;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrialPlaybackServiceTest {
    TrialMediaJobs jobs;
    CourseClient courses;
    TrialPlaybackService service;
    SectionInfoDTO section;
    CourseFullInfoDTO course;
    String file = "a".repeat(64) + ".mp4", version = "a".repeat(64), id;

    @BeforeEach
    void prepare() {
        jobs = mock(TrialMediaJobs.class);
        courses = mock(CourseClient.class);
        service =
                new TrialPlaybackService(
                        jobs,
                        courses,
                        new MockEnvironment()
                                .withProperty(
                                        "TJ_INTERNAL_TOKEN",
                                        "test-trial-key-with-at-least-thirty-two-bytes"));
        section = new SectionInfoDTO(11L, 22L, true, 1, 600, 2);
        course = new CourseFullInfoDTO();
        course.setStatus(2);
        when(courses.sectionInfo(33L)).thenReturn(section);
        when(courses.getCourseInfoById(11L, false, false)).thenReturn(course);
        id = TrialMediaJobs.identity(22, file, version, 60);
        when(jobs.request(22, 60)).thenReturn(Map.of("id", id, "state", "READY"));
        when(jobs.source(22)).thenReturn(Map.of("file_id", file, "source_version", version));
        when(jobs.task(id)).thenReturn(Map.of("id", id, "state", "READY"));
        UserContext.setUser(77L);
        UserContext.setRole(2L);
    }

    @AfterEach
    void clear() {
        UserContext.removeUser();
    }

    private Map<String, String> params(String url) {
        var result = new HashMap<String, String>();
        for (String item : URI.create(url).getRawQuery().split("&")) {
            var pair = item.split("=", 2);
            result.put(pair[0], pair[1]);
        }
        return result;
    }

    @Test
    void pendingAndFailureReturnExplicitStatesWithoutAnyPlaybackAddress() {
        when(jobs.request(22, 60)).thenReturn(Map.of("id", id, "state", "PENDING"));
        var pending = service.request(33);
        assertEquals("PROCESSING", pending.getState());
        assertTrue(pending.getTrial());
        assertNull(pending.getPlayUrl());
        assertNull(pending.getSignature());
        when(jobs.request(22, 60))
                .thenReturn(
                        Map.of(
                                "id",
                                id,
                                "state",
                                "FAILED",
                                "error_code",
                                "TRIAL_PROCESSING_FAILED"));
        var failure = service.request(33);
        assertEquals("FAILED", failure.getState());
        assertEquals("TRIAL_PROCESSING_FAILED", failure.getErrorCode());
        assertNull(failure.getPlayUrl());
    }

    @Test
    void validTicketsBindClipSectionUserAndCurrentPublishedConfiguration() {
        var result = service.request(33);
        var query = params(result.getPlayUrl());
        long expires = Long.parseLong(query.get("expires"));
        String signature = query.get("signature");
        assertTrue(result.getPlayUrl().startsWith("/api/v2/services/media/trial-content/"));
        assertFalse(result.getPlayUrl().contains(file));
        assertNull(result.getFileId());
        assertNotNull(service.authorize(id, 33, 77, expires, signature));
        assertThrows(
                ForbiddenException.class, () -> service.authorize(id, 34, 77, expires, signature));
        assertThrows(
                ForbiddenException.class, () -> service.authorize(id, 33, 78, expires, signature));
        assertThrows(
                ForbiddenException.class,
                () -> service.authorize("b".repeat(64), 33, 77, expires, signature));
        assertThrows(ForbiddenException.class, () -> service.authorize(id, 33, 77, 1, signature));
        section.setTrailer(false);
        assertThrows(
                ForbiddenException.class, () -> service.authorize(id, 33, 77, expires, signature));
        section.setTrailer(true);
        course.setStatus(3);
        assertThrows(
                ForbiddenException.class, () -> service.authorize(id, 33, 77, expires, signature));
        course.setStatus(2);
        section.setFreeDuration(2);
        assertThrows(
                ForbiddenException.class, () -> service.authorize(id, 33, 77, expires, signature));
        section.setFreeDuration(1);
        when(jobs.source(22))
                .thenReturn(
                        Map.of(
                                "file_id",
                                "b".repeat(64) + ".mp4",
                                "source_version",
                                "b".repeat(64)));
        assertThrows(
                ForbiddenException.class, () -> service.authorize(id, 33, 77, expires, signature));
    }

    @Test
    void missingCourseOrTrialConfigurationNeverCreatesAJob() {
        course.setStatus(null);
        assertThrows(ForbiddenException.class, () -> service.request(33));
        course.setStatus(2);
        section.setFreeDuration(0);
        assertThrows(BadRequestException.class, () -> service.request(33));
        section.setFreeDuration(1);
        UserContext.removeUser();
        assertThrows(UnauthorizedException.class, () -> service.request(33));
        verify(jobs, never()).request(anyLong(), anyInt());
    }
}

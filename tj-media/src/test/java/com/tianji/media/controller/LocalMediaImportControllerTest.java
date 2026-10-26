package com.tianji.media.controller;

import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.InternalAuth;
import com.tianji.common.utils.UserContext;
import com.tianji.media.domain.po.Media;
import com.tianji.media.storage.local.LocalObjectStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalMediaImportControllerTest {
    @TempDir Path directory;
    LocalObjectStore store;OperationStore operations;LocalMediaImportController controller;Path source;
    @BeforeEach void setup()throws Exception {
        store=mock(LocalObjectStore.class);operations=mock(OperationStore.class);
        controller=new LocalMediaImportController(store,operations,new MockEnvironment().withProperty("tj.local-import.directory",directory.toString()));
        source=Files.write(directory.resolve("lesson.mp4"),new byte[]{1,2,3});
        UserContext.setUser(9L);UserContext.setRole(1L);
    }
    @AfterEach void close(){UserContext.removeUser();}
    @Test void resolvesOnlyExistingRegularPathsWithinSourceRoot()throws Exception {
        assertEquals(source.toRealPath(),controller.resolveSource("lesson.mp4"));
        for(String invalid:new String[]{"../outside.mp4","./lesson.mp4","/lesson.mp4","C:/lesson.mp4","folder\\lesson.mp4","missing.mp4",""})
            assertThrows(BadRequestException.class,()->controller.resolveSource(invalid),invalid);
        Files.createDirectory(directory.resolve("folder"));
        assertThrows(BadRequestException.class,()->controller.resolveSource("folder"));
        verifyNoInteractions(store,operations);
    }
    @Test void noFileOrOperationAccessBeforeServiceAndAdminAuthorization() {
        try(var internal=mockStatic(InternalAuth.class)) {
            internal.when(InternalAuth::requireService).thenThrow(new ForbiddenException("service required"));
            assertThrows(ForbiddenException.class,()->controller.importMedia(request(),"key"));
        }
        try(var internal=mockStatic(InternalAuth.class)) {
            UserContext.setRole(2L);
            assertThrows(ForbiddenException.class,()->controller.importMedia(request(),"key"));
            internal.verify(InternalAuth::requireService);
        }
        verifyNoInteractions(store,operations);
    }
    @Test void rejectsUnexpectedBytesBeforeReadingOrSubmitting() {
        try(var internal=mockStatic(InternalAuth.class)) {
            assertThrows(BadRequestException.class,()->controller.importMedia(new LocalMediaImportController.ImportRequest("lesson.mp4","a".repeat(64),4L),"key"));
            assertThrows(BadRequestException.class,()->controller.importMedia(new LocalMediaImportController.ImportRequest("lesson.mp4","a".repeat(64),LocalObjectStore.MAX_IMPORT_BYTES+1),"key"));
        }
        verifyNoInteractions(store,operations);
    }
    @Test void submitsExistingDurableWorkflowWithProbedDuration() {
        Media media=new Media().setFileId("stored.mp4").setFilename("lesson.mp4").setDuration(1.28f);
        when(store.importMedia(source,3,"a".repeat(64),"key")).thenReturn(media);
        var view=new OperationStore.View("op","PENDING",null,null,null);
        when(operations.submit(9L,"LOCAL_MEDIA_CREATE","key",Map.of("fileId","stored.mp4","filename","lesson.mp4","duration",1.28f))).thenReturn(view);
        try(var internal=mockStatic(InternalAuth.class)) {
            var response=controller.importMedia(request(),"key");assertEquals(202,response.getStatusCode().value());assertSame(view,response.getBody());
            internal.verify(InternalAuth::requireService);
        }
        verify(store).importMedia(source,3,"a".repeat(64),"key");
    }
    @Test void rejectsSymlinkFileAndDirectoryComponents()throws Exception {
        // Executed in Linux CI; do not suppress inability to create links as a passing test.
        Files.createSymbolicLink(directory.resolve("linked.mp4"),source);
        assertThrows(BadRequestException.class,()->controller.resolveSource("linked.mp4"));
        Files.createDirectory(directory.resolve("nested"));
        Files.createSymbolicLink(directory.resolve("linked-folder"),directory.resolve("nested"));
        assertThrows(BadRequestException.class,()->controller.resolveSource("linked-folder/lesson.mp4"));
    }
    private LocalMediaImportController.ImportRequest request(){return new LocalMediaImportController.ImportRequest("lesson.mp4","a".repeat(64),3L);}
}

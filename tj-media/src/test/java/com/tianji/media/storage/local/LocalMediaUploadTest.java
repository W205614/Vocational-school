package com.tianji.media.storage.local;

import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalMediaUploadTest {
    @TempDir Path directory;
    LocalObjectStore store;MutableClock clock=new MutableClock();
    byte[] content="real bytes supplied to the separately tested native probe".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @BeforeEach void setup() {UserContext.setUser(7L);store=create(8,(file,name)->1.28f);}
    @AfterEach void close(){UserContext.removeUser();}
    private LocalObjectStore create(int ttl,MediaDurationProbe probe) {
        var env=new MockEnvironment().withProperty("tj.local-storage.directory",directory.resolve("objects").toString()).withProperty("tj.local-storage.signature-ttl-seconds",Integer.toString(ttl)).withProperty("tj.local-storage.upload-concurrency","1");
        env.setActiveProfiles("local-simulator");return new LocalObjectStore(env,JsonMapper.builder().build(),probe,clock);
    }
    @Test void callerDurationIsIgnoredAndExactRetryUsesSameMetadata() {
        var first=store.uploadMedia("video.mp4",new ByteArrayInputStream(content),content.length,999f,"same-key");
        assertEquals(1.28f,first.getDuration());
        var retry=store.uploadMedia("video.mp4",new ByteArrayInputStream(content),content.length,0f,"same-key");
        assertEquals(first.getFileId(),retry.getFileId());assertEquals(first.getRequestId(),retry.getRequestId());
        assertEquals(1.28f,store.metadata(first.getFileId()).getDuration());
    }
    @Test void rejectsDeclaredByteMismatchAndCleansStagingFiles()throws Exception {
        assertThrows(BadRequestException.class,()->store.uploadMedia("video.mp4",new ByteArrayInputStream(content),content.length-1,"key"));
        assertThrows(BadRequestException.class,()->store.uploadMedia("video.mp4",new ByteArrayInputStream(content),content.length+1,"key"));
        assertThrows(BadRequestException.class,()->store.uploadMedia("video.mp4",InputStream.nullInputStream(),LocalObjectStore.MAX_UPLOAD_BYTES+1,"key"));
        try(var files=Files.list(directory.resolve("objects"))){assertEquals(0,files.count());}
    }
    @Test void invalidProbeCannotCreateAnObject()throws Exception {
        store=create(8,(file,name)->Float.NaN);
        assertThrows(BadRequestException.class,()->store.uploadMedia("video.mp4",new ByteArrayInputStream(content),content.length,"key"));
        try(var files=Files.list(directory.resolve("objects"))){assertEquals(0,files.count());}
    }
    @Test void offlineImportRequiresActualChecksumAndBytes()throws Exception {
        Path source=Files.write(directory.resolve("source.mp4"),content);
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        assertThrows(BadRequestException.class,()->store.importMedia(source,content.length,"0".repeat(64),"import"));
        assertThrows(BadRequestException.class,()->store.importMedia(source,LocalObjectStore.MAX_IMPORT_BYTES+1,digest,"import"));
        assertEquals(1.28f,store.importMedia(source,content.length,digest,"import").getDuration());
        assertArrayEquals(content,Files.readAllBytes(source));
    }
    @Test void signatureTtlAndReceiptShareExactBoundary() {
        store.upload("video.mp4",new ByteArrayInputStream(content),content.length);
        String url=store.signedUrl("video.mp4");Map<String,String> params=query(url);
        long expiry=Long.parseLong(params.get("expires"));assertEquals(clock.millis()/1000+8,expiry);assertEquals(expiry*1000,store.expiresAt(url));
        clock.millis=expiry*1000-1;assertDoesNotThrow(()->store.readSigned("video.mp4",expiry,params.get("signature")));
        clock.millis=expiry*1000;assertThrows(ForbiddenException.class,()->store.readSigned("video.mp4",expiry,params.get("signature")));
        assertThrows(IllegalArgumentException.class,()->create(601,(file,name)->1f));
        assertThrows(IllegalArgumentException.class,()->create(0,(file,name)->1f));
    }
    @Test void validatorRejectsTicketMintedWithLongerConfiguredTtl() {
        store.upload("video.mp4",new ByteArrayInputStream(content),content.length);
        var longStore=create(600,(file,name)->1f);String url=longStore.signedCourseUrl("video.mp4",7,2);Map<String,String> params=query(url);
        assertThrows(ForbiddenException.class,()->store.readSignedCourse("video.mp4",Long.parseLong(params.get("expires")),params.get("signature"),7,2));
    }
    @Test void uploadPermissionIsFailFastAndReleasedAfterProbeFailure()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        store=create(8,(file,name)->{
            entered.countDown();
            try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("probe release timed out");}
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
            throw new BadRequestException("invalid video");
        });
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->{
                UserContext.setUser(7L);
                try{assertThrows(BadRequestException.class,()->store.uploadMedia("video.mp4",new ByteArrayInputStream(content),content.length,"one"));}
                finally{UserContext.removeUser();}
            });
            try{
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                assertThrows(TooManyRequestsException.class,()->store.uploadMedia("video.mp4",new ByteArrayInputStream(content),content.length,"two"));
            }finally{release.countDown();}
            first.get(3,TimeUnit.SECONDS);
        }
        assertThrows(BadRequestException.class,()->store.uploadMedia("video.mp4",new ByteArrayInputStream(content),content.length,"three"));
        try(var files=Files.list(directory.resolve("objects"))){assertEquals(0,files.count());}
    }
    private Map<String,String> query(String url){var values=new HashMap<String,String>();for(String pair:URI.create(url).getRawQuery().split("&")){var parts=pair.split("=",2);values.put(parts[0],parts[1]);}return values;}
    static class MutableClock extends Clock {
        long millis=1_800_000_000_000L;
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(millis);}public long millis(){return millis;}
    }
}

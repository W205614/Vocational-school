package com.tianji.media.cloud;

import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real isolated MySQL, with a fake provider/native processor. Not a live COS or scanner acceptance. */
class CloudMediaDatabaseTest {
    static JdbcTemplate jdbc;static JdbcTransactionManager manager;static Path root;
    CloudObjectStore objects;CloudUploadService uploads;CloudMediaSettings settings;
    @BeforeAll static void database()throws Exception{
        String password=System.getenv("ACCEPTANCE_DB_PASSWORD");assertNotNull(password,"Isolated database credentials are required");
        String address="jdbc:mysql://127.0.0.1:"+Objects.requireNonNullElse(System.getenv("ACCEPTANCE_DB_PORT"),"23316")+"/";
        var bootstrap=new JdbcTemplate(new DriverManagerDataSource(address,"root",password));bootstrap.execute("CREATE DATABASE IF NOT EXISTS acceptance_media_cloud");
        var source=new DriverManagerDataSource(address+"acceptance_media_cloud?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",password);jdbc=new JdbcTemplate(source);manager=new JdbcTransactionManager(source);
        Path directory=Path.of("").toAbsolutePath();while(!Files.isRegularFile(directory.resolve("deploy/compact/schema.sql"))){directory=directory.getParent();if(directory==null)throw new IllegalStateException("Repository not found");}root=directory;
        String schema=Files.readString(root.resolve("deploy/compact/schema.sql"));var matcher=java.util.regex.Pattern.compile("CREATE TABLE `media` \\(.*?\\) ENGINE=.*?;",java.util.regex.Pattern.DOTALL).matcher(schema);assertTrue(matcher.find());jdbc.execute(matcher.group().replace("CREATE TABLE `media`","CREATE TABLE IF NOT EXISTS `media`"));
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='cloud_media_upload'",Integer.class)==0)for(String sql:Files.readString(root.resolve("deploy/acceptance/migrations/tj_media/V005__private_cloud_media_tasks.sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
    }
    @BeforeEach void prepare(){jdbc.update("DELETE FROM cloud_media_upload");jdbc.update("DELETE FROM media");objects=mock(CloudObjectStore.class);settings=PrivateCloudMediaTest.settings();uploads=new CloudUploadService(jdbc,manager,objects,settings);when(objects.uploadUrl(anyString(),anyLong(),any())).thenReturn(new CloudObjectStore.Upload("https://private.invalid/put",Map.of()));UserContext.setUser(7001L);UserContext.setRole(1L);}
    @AfterEach void clear(){UserContext.removeUser();}
    CloudUploadService.View pending(){var result=uploads.create(new CloudUploadService.Create("lesson.mp4",4,CloudUploadService.digest(new byte[]{1,2,3,4})),"request");when(objects.head(anyString())).thenReturn(new CloudObjectStore.ObjectInfo(4,"application/octet-stream","source-version","source-etag"));return uploads.complete(result.id());}
    @Test void uploadRequestsAreIdempotentOwnerBoundAndRejectChangedBodies(){
        var form=new CloudUploadService.Create("lesson.mp4",4,"a".repeat(64));var first=uploads.create(form,"request");assertEquals(first.id(),uploads.create(form,"request").id());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cloud_media_upload",Integer.class));
        assertThrows(ConflictException.class,()->uploads.create(new CloudUploadService.Create("other.mp4",4,"a".repeat(64)),"request"));
        UserContext.setUser(7002L);assertThrows(ForbiddenException.class,()->uploads.get(first.id()));
    }
    @Test void completionChecksActualProviderMetadataBeforeQueueing(){
        var result=uploads.create(new CloudUploadService.Create("lesson.mp4",4,"a".repeat(64)),"request");when(objects.head(anyString())).thenReturn(new CloudObjectStore.ObjectInfo(5,"application/octet-stream","v","etag"));
        assertThrows(BadRequestException.class,()->uploads.complete(result.id()));assertEquals("UPLOADING",uploads.get(result.id()).state());
        when(objects.head(anyString())).thenReturn(new CloudObjectStore.ObjectInfo(4,"application/octet-stream","v","etag"));assertEquals("PENDING",uploads.complete(result.id()).state());assertEquals("PENDING",uploads.complete(result.id()).state());
    }
    @Test void oneWorkerLeasePublishesExactlyOneReadyMediaAndImmutableObject()throws Exception{
        var task=pending();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var processor=mock(CloudMediaProcessor.class);
        when(objects.read(anyString(),anyString(),anyString(),anyLong(),anyLong())).thenAnswer(call->new ByteArrayInputStream(new byte[]{1,2,3,4}));when(objects.putReady(anyString(),any())).thenReturn(new CloudObjectStore.ObjectInfo(4,"video/mp4","ready-version","ready-etag"));
        when(processor.process(any(),anyString(),anyLong(),anyString(),any(),any())).thenAnswer(call->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));Path output=((Path)call.getArgument(4)).resolve("ready.mp4");Files.write(output,new byte[]{1,2,3,4});return new CloudMediaProcessor.Output(output,1.28f,4,"b".repeat(64));});
        var worker=worker(processor);var future=CompletableFuture.runAsync(()->worker.process(task.id()));assertTrue(entered.await(10,TimeUnit.SECONDS));worker.process(task.id());release.countDown();future.get(10,TimeUnit.SECONDS);
        assertEquals("READY",uploads.get(task.id()).state());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM media",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT attempts FROM cloud_media_upload WHERE id=?",Integer.class,task.id()));
        verify(objects,times(1)).putReady(matches("ready/"+task.id()+"/[0-9a-f-]{36}\\.mp4"),any());assertEquals("ready-version",jdbc.queryForObject("SELECT object_version FROM cloud_media_upload WHERE id=?",String.class,task.id()));
    }
    @Test void processingFailureNeverRegistersOrPublishesMedia()throws Exception{
        var task=pending();when(objects.read(anyString(),anyString(),anyString(),anyLong(),anyLong())).thenReturn(new ByteArrayInputStream(new byte[]{1,2,3,4}));var processor=mock(CloudMediaProcessor.class);when(processor.process(any(),anyString(),anyLong(),anyString(),any(),any())).thenThrow(new BadRequestException("MALWARE_SCAN_REJECTED"));worker(processor).process(task.id());
        assertEquals("FAILED",uploads.get(task.id()).state());assertEquals("MALWARE_SCAN_REJECTED",uploads.get(task.id()).errorCode());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM media",Integer.class));verify(objects,never()).putReady(anyString(),any());assertEquals("PENDING",uploads.retry(task.id()).state());
    }
    private CloudMediaWorker worker(CloudMediaProcessor processor)throws IOException{
        var taskSettings=new CloudMediaSettings("worker",settings.bucket(),settings.region(),settings.secretId(),settings.secretKey(),"",settings.maximumBytes(),900,600,"clamscan","ffmpeg",Files.createTempDirectory("cloud-media-db-test-"),1800);
        var worker=new CloudMediaWorker(jdbc,manager,objects,taskSettings,new StandardEnvironment());ReflectionTestUtils.setField(worker,"processor",processor);return worker;
    }
}

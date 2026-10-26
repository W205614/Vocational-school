package com.tianji.media.cloud;

import com.qcloud.cos.*;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateCloudMediaTest {
    @TempDir Path directory;
    static CloudMediaSettings settings(){return new CloudMediaSettings("api","private-media-12345678","ap-shanghai","secret-id","secret-key","independent-signing-key-32-bytes-minimum",209715200,900,600,"clamscan","ffmpeg",Path.of("/work/media"),1800);}
    @AfterEach void clear(){UserContext.removeUser();}
    @Test void settingsRejectMissingSigningKeyAndUnsafeResourceLimits(){
        settings().validate();
        var missing=new CloudMediaSettings("api","private-media-12345678","ap-shanghai","id","key","",1024,900,600,"scan","ffmpeg",directory,30);
        assertThrows(IllegalArgumentException.class,missing::validate);
    }
    @Test void presignedPutPinsLengthTypeAndPrivateAclWithoutNetwork(){
        var config=new ClientConfig(new Region("ap-shanghai"));config.setHttpProtocol(HttpProtocol.https);
        var client=new COSClient(new BasicCOSCredentials("synthetic-id","synthetic-key"),config);
        try{
            var store=new TencentPrivateObjectStore(client,"private-media-12345678");
            var upload=store.uploadUrl("quarantine/task/source",1234,Instant.now().plusSeconds(60));
            String decoded=URLDecoder.decode(upload.url(),StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            assertTrue(upload.url().startsWith("https://"));assertTrue(decoded.contains("content-length"));assertTrue(decoded.contains("content-type"));assertTrue(decoded.contains("x-cos-acl"));assertEquals("private",upload.headers().get("x-cos-acl"));
        }finally{client.shutdown();}
    }
    @Test void privateBucketCheckRejectsAnonymousAclAndPublicPolicy(){
        var client=mock(COSClient.class);var acl=new AccessControlList();acl.grantPermission(GroupGrantee.AllUsers,Permission.Read);when(client.getBucketAcl(anyString())).thenReturn(acl);
        assertThrows(IllegalStateException.class,()->new TencentPrivateObjectStore(client,"private-media-12345678").verifyPrivateBucket());verify(client,never()).getBucketPolicy(anyString());
        assertTrue(TencentPrivateObjectStore.publicAllow(Map.of("Statement",List.of(Map.of("Effect","Allow","Principal",Map.of("qcs",List.of("*")))))));
        assertFalse(TencentPrivateObjectStore.publicAllow(Map.of("Statement",List.of(Map.of("Effect","Allow","Principal",Map.of("qcs",List.of("qcs::cam::uin/123:uin/456")))))));
        assertTrue(TencentPrivateObjectStore.publicAllow(Map.of("Statement",List.of(Map.of("Effect","Allow","Principal","http://cam.qcloud.com/groups/global/AuthenticatedUsers")))));
        assertTrue(TencentPrivateObjectStore.publicAllow(Map.of("Statement",List.of(Map.of("Effect","Allow","NotPrincipal",Map.of("qcs",List.of("qcs::cam::uin/123:uin/456")))))));
    }
    @Test void privateBucketCheckRejectsEveryGlobalGroupEvenIfSdkEnumLacksIt(){
        for(String identifier:List.of("http://cam.qcloud.com/groups/global/AllUsers","http://cam.qcloud.com/groups/global/AuthenticatedUsers","http://cam.qcloud.com/groups/global/FutureGroup")){
            var group=mock(Grantee.class);when(group.getTypeIdentifier()).thenReturn("Group");when(group.getIdentifier()).thenReturn(identifier);
            var acl=new AccessControlList();acl.grantPermission(group,Permission.Read);var client=mock(COSClient.class);when(client.getBucketAcl(anyString())).thenReturn(acl);
            assertThrows(IllegalStateException.class,()->new TencentPrivateObjectStore(client,"private-media-12345678").verifyPrivateBucket());verify(client,never()).getBucketPolicy(anyString());
        }
        var acl=new AccessControlList();acl.grantPermission(new UinGrantee("qcs::cam::uin/123:uin/456"),Permission.Read);var client=mock(COSClient.class);when(client.getBucketAcl(anyString())).thenReturn(acl);when(client.getBucketPolicy(anyString())).thenReturn(new BucketPolicy());
        assertDoesNotThrow(()->new TencentPrivateObjectStore(client,"private-media-12345678").verifyPrivateBucket());
    }
    @Test void outputIdentityComesFromPutResponseAndCannotRaceWithAHeadOfAnotherVersion()throws Exception{
        var client=mock(COSClient.class);var result=new PutObjectResult();result.setVersionId("uploaded-version");result.setETag("uploaded-etag");when(client.putObject(any(PutObjectRequest.class))).thenReturn(result);
        Path output=directory.resolve("ready.mp4");Files.write(output,new byte[]{1,2,3,4});var store=new TencentPrivateObjectStore(client,"private-media-12345678");
        var stored=store.putReady("ready/task/lease.mp4",output);assertEquals("uploaded-version",stored.version());assertEquals("uploaded-etag",stored.etag());assertEquals(4,stored.size());
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);verify(client).putObject(request.capture());assertEquals("true",request.getValue().getCustomRequestHeaders().get("x-cos-forbid-overwrite"));assertNotNull(request.getValue().getMetadata().getContentMD5());verify(client,never()).getObjectMetadata(anyString(),anyString());
    }
    @Test void readsPinThePublishedVersionAndETagAndRejectUnpinnedBytes()throws Exception{
        var client=mock(COSClient.class);var object=new COSObject();object.setObjectContent(new ByteArrayInputStream(new byte[]{1,2,3,4}));when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);
        var store=new TencentPrivateObjectStore(client,"private-media-12345678");try(var input=store.read("ready/task/lease.mp4","published-version","published-etag",1,2)){assertNotNull(input);}
        var request=org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class);verify(client).getObject(request.capture());assertEquals("published-version",request.getValue().getVersionId());assertEquals(List.of("published-etag"),request.getValue().getMatchingETagConstraints());assertArrayEquals(new long[]{1,2},request.getValue().getRange());
        assertThrows(IllegalArgumentException.class,()->store.read("ready/task/lease.mp4",null,null,0,3));
    }
    @Test void uploadValidationRejectsPathSizeHashAndWrongType(){
        var valid=new CloudUploadService.Create("lesson.mp4",10,"a".repeat(64));CloudUploadService.validate(valid,"request",20);
        for(var form:List.of(new CloudUploadService.Create("../lesson.mp4",10,"a".repeat(64)),new CloudUploadService.Create("lesson.exe",10,"a".repeat(64)),new CloudUploadService.Create("lesson.mp4",21,"a".repeat(64)),new CloudUploadService.Create("lesson.mp4",10,"bad")))assertThrows(BadRequestException.class,()->CloudUploadService.validate(form,"request",20));
    }
    @Test void copiedSourceMustMatchBothDeclaredBytesAndSha256()throws Exception{
        byte[] bytes="real media source".getBytes(StandardCharsets.UTF_8);String hash=CloudUploadService.digest(bytes);
        CloudMediaProcessor.copy(new ByteArrayInputStream(bytes),directory.resolve("valid"),bytes.length,hash,bytes.length);
        assertArrayEquals(bytes,Files.readAllBytes(directory.resolve("valid")));
        assertThrows(BadRequestException.class,()->CloudMediaProcessor.copy(new ByteArrayInputStream(bytes),directory.resolve("short"),bytes.length-1,hash,bytes.length));
        assertThrows(BadRequestException.class,()->CloudMediaProcessor.copy(new ByteArrayInputStream(bytes),directory.resolve("wrong-hash"),bytes.length,"0".repeat(64),bytes.length));
    }
    @Test void ticketsBindOwnerCourseExpiryAndNeverExposeProviderGetUrl(){
        var jdbc=mock(JdbcTemplate.class);var store=mock(CloudObjectStore.class);var storage=new CloudCosMediaStorage(jdbc,store,settings());
        String task=UUID.randomUUID().toString();when(jdbc.queryForList(anyString(),eq(task))).thenReturn(List.of(new HashMap<>(Map.of("id",task,"output_size",64))));
        String ticket=storage.courseTicket("cos:"+task,7,9);assertTrue(ticket.startsWith("/api/v2/services/media/cloud-content/"));assertFalse(ticket.contains("myqcloud"));
        var params=Arrays.stream(ticket.substring(ticket.indexOf('?')+1).split("&")).map(part->part.split("=",2)).collect(java.util.stream.Collectors.toMap(part->part[0],part->part[1]));long expires=Long.parseLong(params.get("expires"));
        assertTrue(storage.expiresAt(ticket)>System.currentTimeMillis());assertNotNull(storage.authorize(new CloudCosMediaStorage.Ticket(task,7,9,expires,params.get("signature"))));
        assertThrows(ForbiddenException.class,()->storage.authorize(new CloudCosMediaStorage.Ticket(task,8,9,expires,params.get("signature"))));
        assertThrows(ForbiddenException.class,()->storage.authorize(new CloudCosMediaStorage.Ticket(task,7,10,expires,params.get("signature"))));
        assertThrows(ForbiddenException.class,()->storage.authorize(new CloudCosMediaStorage.Ticket(task,7,0,expires,params.get("signature"))));
        assertThrows(ForbiddenException.class,()->storage.authorize(new CloudCosMediaStorage.Ticket(task,7,9,Instant.now().getEpochSecond()-1,params.get("signature"))));
        verifyNoInteractions(store);
    }
    @Test void cloudPreviewCannotBeIssuedByStudent(){
        var storage=new CloudCosMediaStorage(mock(JdbcTemplate.class),mock(CloudObjectStore.class),settings());UserContext.setUser(7L);UserContext.setRole(2L);
        assertThrows(ForbiddenException.class,()->storage.getPlaySignature("cos:"+UUID.randomUUID(),7L,null));
    }
    @Test void workerRejectsMissingAndStaleVirusSignatureDatabase()throws Exception{
        assertFalse(CloudWorkerHealth.fresh(directory,172800));Path daily=directory.resolve("daily.cvd");Files.writeString(daily,"synthetic-signature-marker");assertTrue(CloudWorkerHealth.fresh(directory,172800));
        Files.setLastModifiedTime(daily,java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(172801)));assertFalse(CloudWorkerHealth.fresh(directory,172800));
    }
    @Test void legacyVodPlayerSignatureAlwaysHasABoundedExpiry(){
        var properties=new com.tianji.media.config.TencentProperties();properties.setAppId(12345678L);var vod=new com.tianji.media.config.TencentProperties.VodProperties();vod.setVodValidSeconds(7776000);vod.setUrlKey("synthetic-player-key-longer-than-thirty-two-bytes");vod.setPfcg("default");properties.setVod(vod);
        var storage=new com.tianji.media.storage.tencent.TencentMediaStorage(mock(com.tencentcloudapi.vod.v20180717.VodClient.class),properties);
        var jwt=cn.hutool.jwt.JWT.of(storage.getPlaySignature("file",7L,null));long created=((Number)jwt.getPayload("currentTimeStamp")).longValue(),expiry=((Number)jwt.getPayload("expireTimeStamp")).longValue();assertEquals(600,expiry-created);
    }
}

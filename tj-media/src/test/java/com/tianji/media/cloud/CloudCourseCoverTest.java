package com.tianji.media.cloud;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline image/controller/COS contract; never contacts an object storage supplier. */
class CloudCourseCoverTest {
    @AfterEach void clear(){UserContext.removeUser();}

    private static byte[] image(String format)throws IOException{
        var image=new BufferedImage(2,3,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image,format,bytes));return bytes.toByteArray();
    }
    private static CloudObjectStore objects(byte[] bytes,String suffix){
        var objects=mock(CloudObjectStore.class);String key="covers/"+CloudCourseCoverStore.digest(bytes)+"."+suffix;
        var info=new CloudObjectStore.ObjectInfo(bytes.length,CloudCourseCoverStore.contentType(key),"pinned-version","pinned-etag");
        when(objects.putImage(eq(key),any(),eq(info.contentType()))).thenReturn(info);when(objects.head(key)).thenReturn(info);
        when(objects.read(key,"pinned-version","pinned-etag",-1,-1)).thenAnswer(call->new ByteArrayInputStream(bytes));return objects;
    }

    @Test void productionCloudModeRegistersCoverRoutesButLocalAndWorkerModesDoNot(){
        for(var mode:List.of(Map.of("tj.cloud-media.enabled","true","tj.cloud-media.role","api","tj.local-storage.enabled","false"),
                Map.of("tj.cloud-media.enabled","true","tj.cloud-media.role","api","tj.local-storage.enabled","true"),
                Map.of("tj.cloud-media.enabled","true","tj.cloud-media.role","worker","tj.local-storage.enabled","false"),
                Map.of("tj.cloud-media.enabled","false","tj.local-storage.enabled","false"))){
            try(var context=new AnnotationConfigApplicationContext()){
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("mode",new HashMap<>(mode)));
                context.registerBean(CloudObjectStore.class,()->mock(CloudObjectStore.class));
                context.register(CloudCourseCoverStore.class,CloudCourseCoverController.class);context.refresh();
                boolean enabled=mode.get("tj.cloud-media.enabled").equals("true")&&mode.get("tj.local-storage.enabled").equals("false")&&mode.get("tj.cloud-media.role").equals("api");
                assertEquals(enabled?1:0,context.getBeansOfType(CloudCourseCoverController.class).size());
            }
        }
    }

    @Test void administratorUploadDerivesActualTypeAndPublicGetReturnsOnlyValidatedBytes()throws Exception{
        for(String suffix:List.of("png","jpg")){
            byte[] bytes=image(suffix);var objects=objects(bytes,suffix);var controller=new CloudCourseCoverController(new CloudCourseCoverStore(objects));
            UserContext.setUser(7L);UserContext.setRole(1L);
            var result=controller.upload(new MockMultipartFile("file","../../pretend.exe","text/html",bytes));
            String key=CloudCourseCoverStore.digest(bytes)+"."+suffix;
            assertEquals(Map.of("path","/api/v2/services/media/course-covers/"+key),result);
            UserContext.removeUser();var response=controller.cover(key);
            assertEquals(CloudCourseCoverStore.contentType(key),response.getHeaders().getContentType().toString());
            assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));assertTrue(response.getHeaders().getCacheControl().contains("immutable"));
            assertArrayEquals(bytes,response.getBody().getInputStream().readAllBytes());
            verify(objects,times(2)).read("covers/"+key,"pinned-version","pinned-etag",-1,-1);
        }
    }

    @Test void studentCannotOpenOrUploadMultipartContent()throws Exception{
        var objects=mock(CloudObjectStore.class);var controller=new CloudCourseCoverController(new CloudCourseCoverStore(objects));
        var file=mock(org.springframework.web.multipart.MultipartFile.class);UserContext.setUser(7L);UserContext.setRole(2L);
        assertThrows(ForbiddenException.class,()->controller.upload(file));verifyNoInteractions(file,objects);
    }

    @Test void invalidImagesAndDeclaredLengthsFailBeforeAnyObjectWrite()throws Exception{
        var objects=mock(CloudObjectStore.class);var covers=new CloudCourseCoverStore(objects);byte[] png=image("png");
        assertThrows(BadRequestException.class,()->covers.upload(new ByteArrayInputStream("<svg onload=alert(1)>".getBytes()),21));
        assertThrows(BadRequestException.class,()->covers.upload(new ByteArrayInputStream(png),png.length+1));
        assertThrows(BadRequestException.class,()->covers.upload(new ByteArrayInputStream(png),CloudCourseCoverStore.MAX_BYTES+1L));
        var huge=new BufferedImage(8193,1,BufferedImage.TYPE_INT_RGB);var buffer=new ByteArrayOutputStream();ImageIO.write(huge,"png",buffer);
        assertThrows(BadRequestException.class,()->covers.upload(new ByteArrayInputStream(buffer.toByteArray()),buffer.size()));
        verifyNoInteractions(objects);
    }

    @Test void publicGetCannotAddressVideoPathsOrReadOversizedOrUnpinnedObjects()throws Exception{
        var objects=mock(CloudObjectStore.class);var covers=new CloudCourseCoverStore(objects);
        for(String key:List.of("../ready/task/output.mp4","ready/"+"a".repeat(64)+".png","a".repeat(64)+".svg","%2e%2e"))
            assertThrows(BadRequestException.class,()->covers.read(key));
        verifyNoInteractions(objects);String key="a".repeat(64)+".png";
        when(objects.head("covers/"+key)).thenReturn(new CloudObjectStore.ObjectInfo(CloudCourseCoverStore.MAX_BYTES+1L,"image/png","v","etag"));
        assertThrows(BadRequestException.class,()->covers.read(key));
        when(objects.head("covers/"+key)).thenReturn(new CloudObjectStore.ObjectInfo(10,"image/png","null",null));
        assertThrows(BadRequestException.class,()->covers.read(key));verify(objects,never()).read(anyString(),any(),any(),anyLong(),anyLong());
    }

    @Test void pinnedReadRejectsChangedBytesAndNonImageContentEvenAtAValidHash()throws Exception{
        byte[] bytes=image("png");String key=CloudCourseCoverStore.digest(bytes)+".png";var objects=objects(bytes,"png");
        when(objects.read("covers/"+key,"pinned-version","pinned-etag",-1,-1)).thenReturn(new ByteArrayInputStream(new byte[bytes.length]));
        assertThrows(BadRequestException.class,()->new CloudCourseCoverStore(objects).read(key));
        byte[] html="<script>alert(1)</script>".getBytes();var invalid=objects(html,"png");
        assertThrows(BadRequestException.class,()->new CloudCourseCoverStore(invalid).read(CloudCourseCoverStore.digest(html)+".png"));
    }

    @Test void privateImagePutPinsItsOwnResponseAndTransportChecksumsWithoutHead()throws Exception{
        byte[] bytes=image("png");String key="covers/"+CloudCourseCoverStore.digest(bytes)+".png";var client=mock(COSClient.class);
        var result=new PutObjectResult();result.setVersionId("put-version");result.setETag("put-etag");when(client.putObject(any(PutObjectRequest.class))).thenReturn(result);
        var info=new TencentPrivateObjectStore(client,"private-media-12345678").putImage(key,bytes,"image/png");
        assertEquals("put-version",info.version());assertEquals("put-etag",info.etag());
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);verify(client).putObject(request.capture());
        assertEquals(CannedAccessControlList.Private,request.getValue().getCannedAcl());assertEquals("true",request.getValue().getCustomRequestHeaders().get("x-cos-forbid-overwrite"));
        assertEquals("image/png",request.getValue().getMetadata().getContentType());assertEquals(bytes.length,request.getValue().getMetadata().getContentLength());assertNotNull(request.getValue().getMetadata().getContentMD5());
        assertArrayEquals(bytes,request.getValue().getInputStream().readAllBytes());verify(client,never()).getObjectMetadata(anyString(),anyString());
    }

    @Test void duplicateImageUsesPinnedExistingObjectAndStillRejectsCorruptContent()throws Exception{
        byte[] bytes=image("png");String key="covers/"+CloudCourseCoverStore.digest(bytes)+".png";var client=mock(COSClient.class);var conflict=new CosServiceException("exists");conflict.setStatusCode(409);
        when(client.putObject(any(PutObjectRequest.class))).thenThrow(conflict);var metadata=new ObjectMetadata();metadata.setContentLength(bytes.length);metadata.setContentType("image/png");metadata.setHeader("ETag","existing-etag");
        when(client.getObjectMetadata("private-media-12345678",key)).thenReturn(metadata);var object=new COSObject();object.setObjectContent(new ByteArrayInputStream(new byte[bytes.length]));when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);
        var covers=new CloudCourseCoverStore(new TencentPrivateObjectStore(client,"private-media-12345678"));
        assertThrows(BadRequestException.class,()->covers.upload(new ByteArrayInputStream(bytes),bytes.length));
        var request=org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class);verify(client).getObject(request.capture());assertEquals(List.of("existing-etag"),request.getValue().getMatchingETagConstraints());
    }
}

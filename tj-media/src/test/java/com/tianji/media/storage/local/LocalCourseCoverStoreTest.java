package com.tianji.media.storage.local;
import com.tianji.common.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;import java.io.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class LocalCourseCoverStoreTest {
 @TempDir Path directory;
 LocalCourseCoverStore store(){var env=new MockEnvironment().withProperty("tj.local-storage.directory",directory.toString());env.setActiveProfiles("acceptance");return new LocalCourseCoverStore(env);}
 byte[] image(int width,int height,String format)throws IOException{var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),format,output);return output.toByteArray();}
 @Test void validCoverSurvivesRecreationAndDuplicateUpload()throws IOException{var bytes=image(640,360,"png");String url=store().upload(new ByteArrayInputStream(bytes),bytes.length);assertFalse(url.contains("signature"));assertEquals(url,store().upload(new ByteArrayInputStream(bytes),bytes.length));String key=url.substring(url.lastIndexOf('/')+1);assertArrayEquals(bytes,store().read(key).getContentAsByteArray());}
 @Test void malformedUnsupportedAndTruncatedImagesFail()throws IOException{assertThrows(BadRequestException.class,()->store().upload(new ByteArrayInputStream("<svg/>".getBytes()),6));var gif=image(10,10,"gif");assertThrows(BadRequestException.class,()->store().upload(new ByteArrayInputStream(gif),gif.length));var png=image(10,10,"png");assertThrows(BadRequestException.class,()->store().upload(new ByteArrayInputStream(png),png.length+1));}
 @Test void oversizedDimensionsAndPrivateObjectKeysFail()throws IOException{var bytes=image(8193,1,"png");assertThrows(BadRequestException.class,()->store().upload(new ByteArrayInputStream(bytes),bytes.length));assertThrows(BadRequestException.class,()->store().read("../secret.png"));assertThrows(BadRequestException.class,()->store().read("private.mp4"));assertThrows(BadRequestException.class,()->store().upload(InputStream.nullInputStream(),5*1024*1024+1));}
}

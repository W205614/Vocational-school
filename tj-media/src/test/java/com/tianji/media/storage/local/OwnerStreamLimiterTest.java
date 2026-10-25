package com.tianji.media.storage.local;
import com.tianji.common.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.AbstractResource;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class OwnerStreamLimiterTest {
 @Test void ownersAreIndependentAndClosedOrCancelledStreamsReleaseExactlyOnce()throws Exception{
  var limiter=new OwnerStreamLimiter(2);var first=limiter.wrap(new ByteArrayResource(new byte[]{1,2}),7);var second=limiter.wrap(new ByteArrayResource(new byte[]{3}),8);
  try(var one=first.getInputStream();var two=first.getInputStream();var other=second.getInputStream()){
   assertThrows(TooManyRequestsException.class,first::getInputStream);one.close();one.close();try(var replacement=first.getInputStream()){assertEquals(1,replacement.read());}
  }
  try(var a=first.getInputStream();var b=first.getInputStream()){assertNotNull(a);assertNotNull(b);}
 }
 @Test void openingFailureDoesNotLeakCapacity()throws Exception{
  var limiter=new OwnerStreamLimiter(1);var failing=limiter.wrap(new AbstractResource(){public String getDescription(){return "failed";}public InputStream getInputStream()throws IOException{throw new IOException("fixture");}},7);
  assertThrows(IOException.class,failing::getInputStream);assertThrows(IOException.class,failing::getInputStream);
  try(var input=limiter.wrap(new ByteArrayResource(new byte[]{4}),7).getInputStream()){assertEquals(4,input.read());}
 }
}

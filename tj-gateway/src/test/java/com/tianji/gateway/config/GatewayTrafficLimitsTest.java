package com.tianji.gateway.config;
import com.tianji.common.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GatewayTrafficLimitsTest {
 @Test void mediaCannotConsumeApiCapacityAndIpEntriesDisappearAfterClosing(){
  var limits=new GatewayTrafficLimits(null,2,3,1,300,60,120,30);
  try(var media=limits.acquire(true,"ip1");var media2=limits.acquire(true,"ip2");var api=limits.acquire(false,"ip1")){
   assertEquals(1,limits.apiCapacity().availablePermits());assertThrows(TooManyRequestsException.class,()->limits.acquire(true,"ip1"));
   media.close();media.close();try(var reopened=limits.acquire(true,"ip1")){assertNotNull(reopened);}
  }
  assertEquals(2,limits.apiCapacity().availablePermits());
 }
 @Test void saturatedMediaFailsFastWhileOrdinaryRequestsContinue(){
  var limits=new GatewayTrafficLimits(null,1,1,1,300,60,120,30);
  try(var media=limits.acquire(true,"one")){
   assertThrows(TooManyRequestsException.class,()->limits.acquire(true,"two"));try(var api=limits.acquire(false,"one")){assertEquals(0,limits.apiCapacity().availablePermits());}
  }
  try(var reopened=limits.acquire(true,"two")){assertNotNull(reopened);}
 }
}

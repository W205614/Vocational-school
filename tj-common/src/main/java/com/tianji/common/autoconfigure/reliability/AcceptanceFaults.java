package com.tianji.common.autoconfigure.reliability;
import org.springframework.core.env.Environment;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Explicit crash boundary for isolated recovery acceptance, disabled by default. */
public final class AcceptanceFaults {
 private final String point,key;private final AtomicBoolean fired=new AtomicBoolean();
 public AcceptanceFaults(Environment environment){
  point=environment.getProperty("tj.acceptance.fault.point","");key=environment.getProperty("tj.acceptance.fault.business-key","");
  if(!point.isBlank() && (!Set.of("after-publish","after-consume").contains(point) || key.isBlank() ||
      Arrays.stream(environment.getActiveProfiles()).noneMatch("acceptance"::equals)))
   throw new IllegalStateException("Fault injection requires the isolated acceptance profile, exact business key and supported point");
 }
 public void afterPublish(String businessKey){crash("after-publish",businessKey);}
 public void afterConsume(String businessKey){crash("after-consume",businessKey);}
 private void crash(String expected,String businessKey){
  if(expected.equals(point) && key.equals(businessKey) && fired.compareAndSet(false,true)){
   System.err.println("ACCEPTANCE_FAULT "+point+" reached after durable commit/confirmation");System.err.flush();
   Runtime.getRuntime().halt(77);
  }
 }
}

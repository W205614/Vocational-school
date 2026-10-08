package com.tianji.media.storage.local;

import com.tianji.api.client.learning.LearningClient;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.UserContext;
import com.tianji.media.controller.LocalMediaController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="TJ_INTERNAL_TOKEN",matches=".+")
class LocalMediaPlaybackTest {
 @TempDir Path directory;
 LocalObjectStore store;LearningClient learning;LocalMediaController controller;
 byte[] bytes="video bytes".getBytes(StandardCharsets.UTF_8);
 @BeforeEach void setup(){
  var env=new MockEnvironment().withProperty("tj.local-storage.directory",directory.toString());env.setActiveProfiles("acceptance");
  store=new LocalObjectStore(env,JsonMapper.builder().build());store.upload("video.webm",new ByteArrayInputStream(bytes),bytes.length);
  learning=mock(LearningClient.class);controller=new LocalMediaController(store,mock(OperationStore.class),mock(LocalCourseCoverStore.class),learning);
  UserContext.setUser(99L);UserContext.setRole(1L);UserContext.setSession("editor-session");UserContext.setCallDepth(2);
 }
 @AfterEach void close(){UserContext.removeUser();}
 private Map<String,String> parameters(String url){
  var values=new HashMap<String,String>();for(String pair:URI.create(url).getRawQuery().split("&")){var parts=pair.split("=",2);values.put(parts[0],parts[1]);}return values;
 }
 private void restored(){assertEquals(99L,UserContext.getUser());assertEquals(1L,UserContext.getRole());assertEquals("editor-session",UserContext.getSession());assertEquals(2,UserContext.getCallDepth());}
 @Test void courseTicketFetchChecksCurrentRightsAndRestoresCallerIdentity()throws Exception {
  when(learning.isLessonValid(23L)).thenAnswer(call->{assertEquals(7L,UserContext.getUser());assertEquals(2L,UserContext.getRole());assertNull(UserContext.getSession());assertEquals(2,UserContext.getCallDepth());return 31L;});
  var p=parameters(store.signedCourseUrl("video.webm",7L,23L));
  var response=controller.content("video.webm",Long.parseLong(p.get("expires")),p.get("signature"),7L,23L);
  assertArrayEquals(bytes,response.getBody().getContentAsByteArray());restored();verify(learning).isLessonValid(23L);
 }
 @Test void refundMakesAnAlreadyIssuedCourseTicketUnusable(){
  var active=new AtomicBoolean(true);when(learning.isLessonValid(23L)).thenAnswer(call->active.get()?31L:null);
  var p=parameters(store.signedCourseUrl("video.webm",7L,23L));long expiry=Long.parseLong(p.get("expires"));
  assertDoesNotThrow(()->controller.content("video.webm",expiry,p.get("signature"),7L,23L));active.set(false);
  assertThrows(ForbiddenException.class,()->controller.content("video.webm",expiry,p.get("signature"),7L,23L));restored();
 }
 @Test void alteringOrRemovingTicketScopeCannotBypassValidation(){
  var p=parameters(store.signedCourseUrl("video.webm",7L,23L));long expiry=Long.parseLong(p.get("expires"));String signature=p.get("signature");
  assertThrows(ForbiddenException.class,()->controller.content("video.webm",expiry,signature,8L,23L));
  assertThrows(ForbiddenException.class,()->controller.content("video.webm",expiry,signature,7L,24L));
  assertThrows(ForbiddenException.class,()->controller.content("video.webm",expiry,signature,null,null));
  assertThrows(ForbiddenException.class,()->controller.content("video.webm",expiry,signature,7L,null));
  verifyNoInteractions(learning);restored();
 }
 @Test void privilegedPreviewTicketRemainsSeparateFromCourseRights()throws Exception {
  var p=parameters(store.signedUrl("video.webm"));
  var response=controller.content("video.webm",Long.parseLong(p.get("expires")),p.get("signature"),null,null);
  assertArrayEquals(bytes,response.getBody().getContentAsByteArray());verifyNoInteractions(learning);restored();
 }
 @Test void preUpgradeUnscopedSignaturesAreInvalidated()throws Exception {
  long expiry=System.currentTimeMillis()/1000+600;
  var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(System.getenv("TJ_INTERNAL_TOKEN").getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  String legacy=HexFormat.of().formatHex(mac.doFinal(("video.webm:"+expiry).getBytes(StandardCharsets.UTF_8)));
  assertThrows(ForbiddenException.class,()->controller.content("video.webm",expiry,legacy,null,null));verifyNoInteractions(learning);
 }
 @Test void failedRightsLookupRestoresIdentityAndCallDepth(){
  var p=parameters(store.signedCourseUrl("video.webm",7L,23L));when(learning.isLessonValid(23L)).thenThrow(new IllegalStateException("unavailable"));
  assertThrows(IllegalStateException.class,()->controller.content("video.webm",Long.parseLong(p.get("expires")),p.get("signature"),7L,23L));restored();
 }
}

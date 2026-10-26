package com.tianji.user.reliability;
import com.tianji.user.service.impl.AccountVerificationService;
import com.tianji.message.api.client.AsyncSmsClient;
import com.tianji.message.domain.dto.SmsInfoDTO;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_REDIS_PORT",matches="[0-9]+")
class AccountVerificationRedisTest {
    private LettuceConnectionFactory connection;private StringRedisTemplate redis;private AsyncSmsClient sms;private AccountVerificationService service;private String phone,ip,tag;
    @BeforeEach void setup() throws Exception{
        var config=new RedisStandaloneConfiguration("127.0.0.1",Integer.parseInt(System.getenv("ACCEPTANCE_REDIS_PORT")));connection=new LettuceConnectionFactory(config);connection.afterPropertiesSet();connection.start();redis=new StringRedisTemplate(connection);
        sms=mock(AsyncSmsClient.class);service=new AccountVerificationService(redis,sms);phone="139"+String.format(Locale.ROOT,"%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100_000_000L));ip="fixture-"+UUID.randomUUID();tag="{account:"+digest(phone)+"}";
    }
    @AfterEach void cleanup()throws Exception{if(redis!=null)redis.delete(List.of(tag+":REGISTER",tag+":RECOVER",tag+":cooldown",tag+":hour","account:code:ip:"+digest(ip)));if(connection!=null)connection.destroy();}
    private String issued(){service.issue(phone,"REGISTER",ip);var capture=ArgumentCaptor.forClass(SmsInfoDTO.class);verify(sms).sendMessage(capture.capture());return capture.getValue().getTemplateParams().get("code");}
    @Test void singleUsePurposeBoundProofNeverStoresPlainCode(){String code=issued();assertTrue(code.matches("[0-9]{6}"));assertFalse(redis.opsForHash().entries(tag+":REGISTER").containsValue(code));assertThrows(BadRequestException.class,()->service.consume(phone,"RECOVER",code));service.consume(phone,"REGISTER",code);assertThrows(BadRequestException.class,()->service.consume(phone,"REGISTER",code));}
    @Test void fifthWrongAttemptPermanentlyConsumesChallenge(){String code=issued(),wrong=String.format(Locale.ROOT,"%06d",(Integer.parseInt(code)+1)%1_000_000);for(int n=0;n<5;n++)assertThrows(BadRequestException.class,()->service.consume(phone,"REGISTER",wrong));assertThrows(BadRequestException.class,()->service.consume(phone,"REGISTER",code));}
    @Test void cooldownCannotBeBypassedByChangingPurpose(){issued();assertThrows(TooManyRequestsException.class,()->service.issue(phone,"RECOVER",ip));verify(sms,times(1)).sendMessage(any());}
    @Test void durableAcceptanceFailureLeavesNoUsableProof(){doThrow(new IllegalStateException("fixture transport unavailable")).when(sms).sendMessage(any());assertThrows(ServiceUnavailableException.class,()->service.issue(phone,"REGISTER",ip));assertFalse(Boolean.TRUE.equals(redis.hasKey(tag+":REGISTER")));}
    @Test void expiredChallengeCannotBeConsumed(){String code=issued();redis.expire(tag+":REGISTER",java.time.Duration.ZERO);assertThrows(BadRequestException.class,()->service.consume(phone,"REGISTER",code));}
    @Test void concurrentReplicasConsumeProofOnlyOnce()throws Exception{
        String code=issued();var second=new AccountVerificationService(redis,sms);var gate=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            List<java.util.concurrent.Future<Boolean>> results=new ArrayList<>();for(var instance:List.of(service,second))results.add(pool.submit(()->{gate.await();try{instance.consume(phone,"REGISTER",code);return true;}catch(BadRequestException rejected){return false;}}));gate.countDown();int accepted=0;for(var result:results)if(result.get(10,java.util.concurrent.TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);
        }
    }
    private static String digest(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
}

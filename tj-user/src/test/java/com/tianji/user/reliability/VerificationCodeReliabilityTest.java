package com.tianji.user.reliability;

import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.message.api.client.AsyncSmsClient;
import com.tianji.user.service.impl.CodeServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.tianji.user.constants.UserConstants.USER_VERIFY_CODE_KEY;
import static com.tianji.api.constants.SmsConstants.VERIFY_CODE_PARAM_NAME;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class VerificationCodeReliabilityTest {
    @Test void concurrentRequestsKeepOneCodeAndDurablePayloadsAgree() throws Exception {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_common?serverTimezone=Asia/Shanghai","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        var jdbc=new JdbcTemplate(source);jdbc.update("DELETE FROM reliability_outbox");
        var mapper=JsonMapper.builder().build();
        var sms=new AsyncSmsClient(new OutboxStore(jdbc,mapper),new DataSourceTransactionManager(source));
        var factory=new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv().getOrDefault("ACCEPTANCE_REDIS_PORT","23379")));factory.afterPropertiesSet();factory.start();
        var redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();
        String phone="acceptance-"+UUID.randomUUID(),key=USER_VERIFY_CODE_KEY+phone;
        var pool=Executors.newFixedThreadPool(10);
        try {
            var service=new CodeServiceImpl();
            ReflectionTestUtils.setField(service,"stringRedisTemplate",redis);
            ReflectionTestUtils.setField(service,"asyncSmsClient",sms);
            var start=new CountDownLatch(1);var futures=new ArrayList<Future<?>>();
            for(int i=0;i<100;i++)futures.add(pool.submit(()->{start.await();service.sendVerifyCode(phone);return null;}));
            start.countDown();for(var future:futures)future.get(30,TimeUnit.SECONDS);
            String code=redis.opsForValue().get(key);
            assertNotNull(code);assertTrue(redis.getExpire(key)>0);
            var codes=new HashSet<String>();
            jdbc.queryForList("SELECT payload FROM reliability_outbox",String.class).forEach(payload->
                    codes.add(mapper.readTree(payload).get("payload").get("templateParams").get(VERIFY_CODE_PARAM_NAME).asString()));
            assertEquals(Set.of(code),codes);
            assertEquals(100,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE status='PENDING'",Integer.class));
        } finally {
            pool.shutdownNow();redis.delete(key);factory.destroy();
        }
    }
}

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
import static com.tianji.user.constants.UserConstants.USER_VERIFY_CODE_TTL;
import static com.tianji.api.constants.SmsConstants.VERIFY_CODE_PARAM_NAME;

@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB_PASSWORD", matches = ".+")
class VerificationCodeReliabilityTest {
    private record ProofFixture(
            LettuceConnectionFactory factory,
            StringRedisTemplate redis,
            CodeServiceImpl service,
            AsyncSmsClient sms,
            String phone,
            String key)
            implements AutoCloseable {
        @Override
        public void close() {
            redis.delete(key);
            factory.destroy();
        }
    }

    private ProofFixture proof() {
        var factory =
                new LettuceConnectionFactory(
                        "127.0.0.1",
                        Integer.parseInt(
                                System.getenv().getOrDefault("ACCEPTANCE_REDIS_PORT", "23379")));
        factory.afterPropertiesSet();
        factory.start();
        var redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        var service = new CodeServiceImpl();
        var sms = org.mockito.Mockito.mock(AsyncSmsClient.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "asyncSmsClient", sms);
        String phone = "legacy-proof-" + UUID.randomUUID();
        return new ProofFixture(factory, redis, service, sms, phone, USER_VERIFY_CODE_KEY + phone);
    }

    @Test
    void missingOrMalformedProofCannotPassAsTwoNullValues() {
        var redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        var service = new CodeServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        for (String code : new String[] {null, "", " ", "123", "12345", "abcd"})
            assertThrows(
                    com.tianji.common.exceptions.BadRequestException.class,
                    () -> service.verifyCode("13800010001", code));
        assertThrows(
                com.tianji.common.exceptions.BadRequestException.class,
                () -> service.verifyCode(null, "1234"));
        org.mockito.Mockito.verifyNoInteractions(redis);
    }

    @Test
    void missingExpiredAndConsumedCodesFailClosed() {
        try (var f = proof()) {
            assertThrows(
                    com.tianji.common.exceptions.BadRequestException.class,
                    () -> f.service().verifyCode(f.phone(), "1234"));
            f.redis().opsForValue().set(f.key(), "1234", java.time.Duration.ofSeconds(30));
            f.redis().expire(f.key(), java.time.Duration.ZERO);
            assertThrows(
                    com.tianji.common.exceptions.BadRequestException.class,
                    () -> f.service().verifyCode(f.phone(), "1234"));
            f.redis().opsForValue().set(f.key(), "1234", java.time.Duration.ofSeconds(30));
            f.service().verifyCode(f.phone(), "1234");
            assertFalse(Boolean.TRUE.equals(f.redis().hasKey(f.key())));
            assertThrows(
                    com.tianji.common.exceptions.BadRequestException.class,
                    () -> f.service().verifyCode(f.phone(), "1234"));
        }
    }

    @Test
    void fiveWrongAttemptsInvalidateTheCodeWithoutExtendingItsExpiryOrChangingResentSms() {
        try (var f = proof()) {
            f.redis().opsForValue().set(f.key(), "1234", java.time.Duration.ofSeconds(30));
            long expiry = expiry(f.redis(), f.key());
            Long ttl = f.redis().getExpire(f.key());
            assertThrows(
                    com.tianji.common.exceptions.BadRequestException.class,
                    () -> f.service().verifyCode(f.phone(), "9999"));
            assertEquals("1234:1", f.redis().opsForValue().get(f.key()));
            assertTrue(f.redis().getExpire(f.key()) <= ttl);
            f.service().sendVerifyCode(f.phone());
            var sent =
                    org.mockito.ArgumentCaptor.forClass(
                            com.tianji.message.domain.dto.SmsInfoDTO.class);
            org.mockito.Mockito.verify(f.sms()).sendMessage(sent.capture());
            assertEquals("1234", sent.getValue().getTemplateParams().get(VERIFY_CODE_PARAM_NAME));
            assertTrue(Math.abs(expiry - sent.getValue().getExpiresAt()) <= 1);
            assertEquals(expiry, expiry(f.redis(), f.key()));
            for (int n = 1; n < 5; n++)
                assertThrows(
                        com.tianji.common.exceptions.BadRequestException.class,
                        () -> f.service().verifyCode(f.phone(), "9999"));
            assertFalse(Boolean.TRUE.equals(f.redis().hasKey(f.key())));
            assertThrows(
                    com.tianji.common.exceptions.BadRequestException.class,
                    () -> f.service().verifyCode(f.phone(), "1234"));
        }
    }

    @Test
    void concurrentVerificationConsumesTheProofExactlyOnce() throws Exception {
        try (var f = proof()) {
            f.redis().opsForValue().set(f.key(), "1234", java.time.Duration.ofSeconds(30));
            var pool = Executors.newFixedThreadPool(10);
            try {
                var start = new CountDownLatch(1);
                var work = new ArrayList<Future<Boolean>>();
                for (int n = 0; n < 10; n++)
                    work.add(
                            pool.submit(
                                    () -> {
                                        start.await();
                                        try {
                                            f.service().verifyCode(f.phone(), "1234");
                                            return true;
                                        } catch (
                                                com.tianji.common.exceptions.BadRequestException
                                                        rejected) {
                                            return false;
                                        }
                                    }));
                start.countDown();
                int accepted = 0;
                for (var result : work) if (result.get(10, TimeUnit.SECONDS)) accepted++;
                assertEquals(1, accepted);
                assertFalse(Boolean.TRUE.equals(f.redis().hasKey(f.key())));
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void concurrentRequestsKeepOneCodeAndDurablePayloadsAgree() throws Exception {
        var source =
                new DriverManagerDataSource(
                        "jdbc:mysql://127.0.0.1:"
                                + System.getenv().getOrDefault("ACCEPTANCE_DB_PORT", "23316")
                                + "/acceptance_common?serverTimezone=Asia/Shanghai",
                        "root",
                        System.getenv("ACCEPTANCE_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        jdbc.update("DELETE FROM reliability_outbox");
        var mapper = JsonMapper.builder().build();
        var sms =
                new AsyncSmsClient(
                        new OutboxStore(jdbc, mapper), new DataSourceTransactionManager(source));
        var factory =
                new LettuceConnectionFactory(
                        "127.0.0.1",
                        Integer.parseInt(
                                System.getenv().getOrDefault("ACCEPTANCE_REDIS_PORT", "23379")));
        factory.afterPropertiesSet();
        factory.start();
        var redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        String phone = "acceptance-" + UUID.randomUUID(), key = USER_VERIFY_CODE_KEY + phone;
        var pool = Executors.newFixedThreadPool(10);
        try {
            var service = new CodeServiceImpl();
            ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
            ReflectionTestUtils.setField(service, "asyncSmsClient", sms);
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<?>>();
            long startedAt = System.currentTimeMillis();
            for (int i = 0; i < 100; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    service.sendVerifyCode(phone);
                                    return null;
                                }));
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
            String code = redis.opsForValue().get(key);
            assertNotNull(code);
            assertTrue(redis.getExpire(key) > 0);
            long expiresAt = expiry(redis, key);
            assertTrue(expiresAt > startedAt);
            assertTrue(expiresAt <= System.currentTimeMillis() + USER_VERIFY_CODE_TTL.toMillis());
            var codes = new HashSet<String>();
            var expiries = new HashSet<Long>();
            jdbc.queryForList("SELECT payload FROM reliability_outbox", String.class)
                    .forEach(
                            payload -> {
                                var message = mapper.readTree(payload).get("payload");
                                codes.add(
                                        message.get("templateParams")
                                                .get(VERIFY_CODE_PARAM_NAME)
                                                .asString());
                                expiries.add(message.get("expiresAt").asLong());
                            });
            assertEquals(Set.of(code), codes);
            assertTrue(expiries.stream().allMatch(value -> Math.abs(expiresAt - value) <= 1));
            assertEquals(
                    100,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM reliability_outbox WHERE status='PENDING'",
                            Integer.class));
            // Reusing a short-lived proof must carry its original expiry, rather than a
            // fresh five-minute sending window. All one hundred durable payloads agree.
            jdbc.update("DELETE FROM reliability_outbox");
            redis.opsForValue().set(key, code + ":1", java.time.Duration.ofSeconds(30));
            long originalExpiry = expiry(redis, key);
            var repeated = new ArrayList<Future<?>>();
            for (int i = 0; i < 100; i++)
                repeated.add(pool.submit(() -> service.sendVerifyCode(phone)));
            for (var future : repeated) future.get(30, TimeUnit.SECONDS);
            assertEquals(originalExpiry, expiry(redis, key));
            assertEquals(code + ":1", redis.opsForValue().get(key));
            var resent = jdbc.queryForList("SELECT payload FROM reliability_outbox", String.class);
            assertEquals(100, resent.size());
            for (String payload : resent) {
                var message = mapper.readTree(payload).get("payload");
                assertEquals(
                        code, message.get("templateParams").get(VERIFY_CODE_PARAM_NAME).asString());
                assertTrue(Math.abs(originalExpiry - message.get("expiresAt").asLong()) <= 1);
            }
        } finally {
            pool.shutdownNow();
            redis.delete(key);
            factory.destroy();
        }
    }

    private static long expiry(StringRedisTemplate redis, String key) {
        // The repository's Redis 7.4 test fixture exposes an independent exact expiry
        // observation. Runtime issuance only uses TIME/PTTL, preserving older managed APIs.
        return java.util.Objects.requireNonNull(
                redis.execute(
                        new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                                "return redis.call('PEXPIRETIME',KEYS[1])", Long.class),
                        List.of(key)));
    }
}

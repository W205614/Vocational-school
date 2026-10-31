package com.tianji.media.storage.local;

import com.tianji.common.exceptions.ServiceUnavailableException;
import com.tianji.common.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_REDIS_PORT", matches="[0-9]+")
class OwnerStreamLimiterTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private long owner, otherOwner;
    private final List<OwnerStreamLimiter> limiters = new ArrayList<>();

    @BeforeEach void connect() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("ACCEPTANCE_REDIS_PORT")));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        owner = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE - 1);
        otherOwner = owner + 1;
    }

    @AfterEach void close() {
        limiters.forEach(OwnerStreamLimiter::close);
        redis.delete(List.of("media:streams:owner:" + owner, "media:streams:owner:" + otherOwner));
        factory.destroy();
    }

    private OwnerStreamLimiter limiter(int maximum) {
        var limiter = new OwnerStreamLimiter(redis, maximum);
        limiters.add(limiter);
        return limiter;
    }

    @Test void mediaRedisAutoConfigurationInjectsSharedLimiter() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
                .withBean(OwnerStreamLimiter.class)
                .withPropertyValues("spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=" + System.getenv("ACCEPTANCE_REDIS_PORT"))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.containsBean("stringRedisTemplate"));
                    try (var input = context.getBean(OwnerStreamLimiter.class)
                            .wrap(new ByteArrayResource(new byte[]{4}), owner).getInputStream()) {
                        assertEquals(4, input.read());
                        assertEquals(1L, redis.opsForZSet().zCard("media:streams:owner:" + owner));
                    }
                });
    }

    @Test void twoReplicasShareOwnerQuotaAndDoubleCloseReleasesOnlyItsOwnPermit() throws Exception {
        var first = limiter(2).wrap(new ByteArrayResource(new byte[]{1, 2}), owner);
        var second = limiter(2).wrap(new ByteArrayResource(new byte[]{3}), owner);
        try (var one = first.getInputStream(); var two = second.getInputStream();
             var other = limiter(2).wrap(new ByteArrayResource(new byte[]{4}), otherOwner).getInputStream()) {
            assertThrows(TooManyRequestsException.class, first::getInputStream);
            assertThrows(TooManyRequestsException.class, second::getInputStream);
            one.close();
            one.close();
            try (var replacement = first.getInputStream()) {
                assertEquals(1, replacement.read());
                assertThrows(TooManyRequestsException.class, second::getInputStream);
            }
            assertEquals(4, other.read());
        }
        try (var a = first.getInputStream(); var b = second.getInputStream()) {
            assertNotNull(a);
            assertNotNull(b);
        }
    }

    @Test void concurrentReplicasCannotOverspendSharedPermits() throws Exception {
        var first = limiter(2).wrap(new ByteArrayResource(new byte[]{1}), owner);
        var second = limiter(2).wrap(new ByteArrayResource(new byte[]{1}), owner);
        var opened = new CopyOnWriteArrayList<InputStream>();
        var rejected = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Future<?>>();
            for (int n = 0; n < 24; n++) {
                var resource = n % 2 == 0 ? first : second;
                tasks.add(pool.submit(() -> {
                    try { opened.add(resource.getInputStream()); }
                    catch (TooManyRequestsException error) { rejected.incrementAndGet(); }
                    catch (IOException error) { throw new RuntimeException(error); }
                }));
            }
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            assertEquals(2, opened.size());
            assertEquals(22, rejected.get());
        } finally {
            for (var stream : opened) stream.close();
        }
    }

    @Test void openingFailureDoesNotLeakCapacity() throws Exception {
        var limiter = limiter(1);
        var failing = limiter.wrap(new AbstractResource() {
            @Override public String getDescription() { return "failed"; }
            @Override public InputStream getInputStream() throws IOException { throw new IOException("fixture"); }
        }, owner);
        assertThrows(IOException.class, failing::getInputStream);
        assertThrows(IOException.class, failing::getInputStream);
        try (var input = limiter.wrap(new ByteArrayResource(new byte[]{4}), owner).getInputStream()) {
            assertEquals(4, input.read());
        }
    }

    @Test void activeStreamRenewalRetainsQuotaAcrossMultipleLeasePeriods() throws Exception {
        var limiter = new OwnerStreamLimiter(redis, 1, 2000, 100, Executors.newSingleThreadScheduledExecutor());
        limiters.add(limiter);
        var source = limiter.wrap(new ByteArrayResource(new byte[]{1, 2}), owner);
        try (var input = source.getInputStream()) {
            Thread.sleep(4500);
            assertEquals(1, input.read());
            assertThrows(TooManyRequestsException.class, limiter(1).wrap(new ByteArrayResource(new byte[]{3}), owner)::getInputStream);
        }
    }

    @Test void stoppedProcessPermitsExpireAndOldStreamCannotResumeOrResurrectItsPermit() throws Exception {
        var timer = Executors.newSingleThreadScheduledExecutor();
        var stopped = new OwnerStreamLimiter(redis, 1, 300, 30, timer);
        limiters.add(stopped);
        var old = stopped.wrap(new ByteArrayResource(new byte[]{1}), owner).getInputStream();
        timer.shutdownNow();
        await(() -> !Boolean.TRUE.equals(redis.hasKey("media:streams:owner:" + owner)));
        var replacement = limiter(1).wrap(new ByteArrayResource(new byte[]{2}), owner);
        try (var current = replacement.getInputStream()) {
            assertThrows(IOException.class, old::read);
            assertEquals(2, current.read());
            assertThrows(TooManyRequestsException.class, replacement::getInputStream);
        } finally { old.close(); }
    }

    @Test void missingRedisPermitClosesSourceAndPreventsFurtherReads() throws Exception {
        var timer = Executors.newSingleThreadScheduledExecutor();
        var limiter = new OwnerStreamLimiter(redis, 1, 1000, 20, timer);
        limiters.add(limiter);
        var closed = new AtomicBoolean();
        var source = new AbstractResource() {
            @Override public String getDescription() { return "observable-close"; }
            @Override public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[]{1}) {
                    @Override public void close() { closed.set(true); }
                };
            }
        };
        try (var input = limiter.wrap(source, owner).getInputStream()) {
            redis.delete("media:streams:owner:" + owner);
            await(closed::get);
            assertThrows(IOException.class, input::read);
        }
    }

    @Test void renewalFailureClosesSourceInsteadOfContinuingWithoutQuota() throws Exception {
        var unavailable = new AtomicBoolean();
        var transport = spy(redis);
        doAnswer(call -> {
            if (unavailable.get()) throw new org.springframework.data.redis.RedisConnectionFailureException("fixture");
            return call.callRealMethod();
        }).when(transport).execute(any(RedisScript.class), anyList(), any(Object[].class));
        var limiter = new OwnerStreamLimiter(transport, 1, 2000, 20, Executors.newSingleThreadScheduledExecutor());
        limiters.add(limiter);
        var closed = new AtomicBoolean();
        var source = new AbstractResource() {
            @Override public String getDescription() { return "observable-close"; }
            @Override public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[]{1}) {
                    @Override public void close() { closed.set(true); }
                };
            }
        };
        try (var input = limiter.wrap(source, owner).getInputStream()) {
            unavailable.set(true);
            await(closed::get);
            assertThrows(IOException.class, input::read);
            assertFalse(Boolean.TRUE.equals(redis.hasKey("media:streams:owner:" + owner)));
        }
    }

    @Test void unavailableQuotaFailsClosedBeforeOpeningSource() throws Exception {
        var unavailable = mock(StringRedisTemplate.class);
        when(unavailable.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("fixture"));
        try (var limiter = new OwnerStreamLimiter(unavailable, 1)) {
            var source = mock(org.springframework.core.io.Resource.class);
            assertThrows(ServiceUnavailableException.class, limiter.wrap(source, owner)::getInputStream);
            verify(source, never()).getInputStream();
        }
    }

    @Test void renewalFailureUnblocksSocketReadAndCloseFailureStillReleasesPermit() throws Exception {
        var unavailable = new AtomicBoolean();
        var transport = spy(redis);
        doAnswer(call -> {
            if (unavailable.get()) throw new org.springframework.data.redis.RedisConnectionFailureException("fixture");
            return call.callRealMethod();
        }).when(transport).execute(any(RedisScript.class), anyList(), any(Object[].class));
        var limiter = new OwnerStreamLimiter(transport, 1, 2000, 20, Executors.newSingleThreadScheduledExecutor());
        limiters.add(limiter);
        var entered = new CountDownLatch(1);
        var closes = new AtomicInteger();
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var client = new Socket(server.getInetAddress(), server.getLocalPort());
             var peer = server.accept(); var pool = Executors.newSingleThreadExecutor()) {
            client.setSoTimeout(4000);
            var source = new AbstractResource() {
                @Override public String getDescription() { return "blocked-loopback-socket"; }
                @Override public InputStream getInputStream() throws IOException {
                    return new FilterInputStream(client.getInputStream()) {
                        @Override public int read() throws IOException { entered.countDown(); return super.read(); }
                        @Override public void close() throws IOException {
                            closes.incrementAndGet();
                            super.close();
                            throw new IOException("fixture source-close failure");
                        }
                    };
                }
            };
            try (var input = limiter.wrap(source, owner).getInputStream()) {
                var read = pool.submit(() -> assertThrows(IOException.class, input::read));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                unavailable.set(true);
                assertNotNull(read.get(5, TimeUnit.SECONDS));
                await(() -> !Boolean.TRUE.equals(redis.hasKey("media:streams:owner:" + owner)));
                input.close();
                assertEquals(1, closes.get());
            }
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "condition did not converge within five seconds");
    }
}

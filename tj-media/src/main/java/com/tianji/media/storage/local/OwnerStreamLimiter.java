package com.tianji.media.storage.local;

import com.tianji.common.exceptions.ServiceUnavailableException;
import com.tianji.common.exceptions.TooManyRequestsException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared owner quotas. Expiring permits reclaim capacity when a media process exits. */
@Component
@Slf4j
public class OwnerStreamLimiter implements AutoCloseable {
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
        local clock=redis.call('TIME')
        local now=tonumber(clock[1])*1000+math.floor(tonumber(clock[2])/1000)
        redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',now)
        if redis.call('ZCARD',KEYS[1])>=tonumber(ARGV[1]) then return 0 end
        redis.call('ZADD',KEYS[1],now+tonumber(ARGV[2]),ARGV[3])
        redis.call('PEXPIRE',KEYS[1],ARGV[2])
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
        local clock=redis.call('TIME')
        local now=tonumber(clock[1])*1000+math.floor(tonumber(clock[2])/1000)
        local expires=redis.call('ZSCORE',KEYS[1],ARGV[1])
        if not expires or tonumber(expires)<=now then return 0 end
        redis.call('ZADD',KEYS[1],now+tonumber(ARGV[2]),ARGV[1])
        redis.call('PEXPIRE',KEYS[1],ARGV[2])
        return 1
        """, Long.class);

    private final StringRedisTemplate redis;
    private final int maximum;
    private final long leaseMillis;
    private final ScheduledExecutorService renewal;
    private final ConcurrentHashMap<String,LeasedInputStream> active = new ConcurrentHashMap<>();

    @Autowired
    public OwnerStreamLimiter(StringRedisTemplate redis, @Value("${tj.media.streams-per-owner:2}") int maximum) {
        this(redis, maximum, 60_000, 10_000, Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "media-stream-leases");
            thread.setDaemon(true);
            return thread;
        }));
    }

    OwnerStreamLimiter(StringRedisTemplate redis, int maximum, long leaseMillis, long renewalMillis,
                       ScheduledExecutorService renewal) {
        if (maximum < 1 || leaseMillis < 1 || renewalMillis < 1 || renewalMillis >= leaseMillis) {
            throw new IllegalArgumentException("Positive owner limit and renewal shorter than lease required");
        }
        this.redis = redis;
        this.maximum = maximum;
        this.leaseMillis = leaseMillis;
        this.renewal = renewal;
        renewal.scheduleWithFixedDelay(this::renew, renewalMillis, renewalMillis, TimeUnit.MILLISECONDS);
    }

    public Resource wrap(Resource source, long owner) {
        return new AbstractResource() {
            @Override public String getDescription() { return source.getDescription(); }
            @Override public String getFilename() { return source.getFilename(); }
            @Override public long contentLength() throws IOException { return source.contentLength(); }
            @Override public long lastModified() throws IOException { return source.lastModified(); }
            @Override public InputStream getInputStream() throws IOException {
                String key = "media:streams:owner:" + owner, token = UUID.randomUUID().toString();
                long started = System.nanoTime();
                Long accepted;
                try {
                    accepted = redis.execute(ACQUIRE, List.of(key), Integer.toString(maximum),
                            Long.toString(leaseMillis), token);
                } catch (DataAccessException error) {
                    throw new ServiceUnavailableException("视频播放连接额度暂时无法核验，请稍后重试");
                }
                if (accepted == null) throw new ServiceUnavailableException("视频播放连接额度暂时无法核验，请稍后重试");
                if (accepted != 1) throw new TooManyRequestsException("视频播放连接过多，请关闭其他播放窗口后重试");
                InputStream input;
                try {
                    input = source.getInputStream();
                } catch (IOException | RuntimeException error) {
                    release(key, token);
                    throw error;
                }
                var stream = new LeasedInputStream(input, key, token, started);
                active.put(token, stream);
                stream.requireActive();
                return stream;
            }
        };
    }

    private void renew() {
        for (var stream : active.values()) {
            if (stream.closed.get()) continue;
            long started = System.nanoTime();
            Long renewed;
            try {
                renewed = redis.execute(RENEW, List.of(stream.key), stream.token, Long.toString(leaseMillis));
            } catch (DataAccessException error) {
                log.warn("Media stream lease could not be renewed: {}", error.getClass().getSimpleName());
                stream.abort();
                continue;
            }
            if (renewed == null || renewed != 1) {
                log.warn("Media stream lease was lost; closing its source");
                stream.abort();
            } else {
                // Start before the Redis round trip so a delayed response cannot extend a local lease.
                stream.deadline = started + TimeUnit.MILLISECONDS.toNanos(leaseMillis);
            }
        }
    }

    private void release(String key, String token) {
        try {
            redis.opsForZSet().remove(key, token);
        } catch (DataAccessException error) {
            log.warn("Media stream permit awaits lease expiry: {}", error.getClass().getSimpleName());
        }
    }

    @PreDestroy
    @Override public void close() {
        renewal.shutdownNow();
        active.values().forEach(LeasedInputStream::abort);
    }

    private final class LeasedInputStream extends FilterInputStream {
        private final String key, token;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile long deadline;

        private LeasedInputStream(InputStream source, String key, String token, long started) {
            super(source);
            this.key = key;
            this.token = token;
            deadline = started + TimeUnit.MILLISECONDS.toNanos(leaseMillis);
        }

        private void requireActive() throws IOException {
            if (closed.get() || System.nanoTime() - deadline >= 0) {
                close();
                throw new IOException("Video stream lease expired or was closed");
            }
        }

        @Override public int read() throws IOException {
            requireActive();
            int value = in.read();
            requireActive();
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            requireActive();
            int count = in.read(bytes, offset, length);
            requireActive();
            return count;
        }

        @Override public long skip(long count) throws IOException {
            requireActive();
            long skipped = in.skip(count);
            requireActive();
            return skipped;
        }

        @Override public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                active.remove(token, this);
                try { super.close(); } finally { release(key, token); }
            }
        }

        private void abort() {
            try {
                close();
            } catch (IOException error) {
                log.warn("Closing a media stream source failed: {}", error.getClass().getSimpleName());
            }
        }
    }
}

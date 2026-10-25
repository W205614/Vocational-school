package com.tianji.gateway.config;

import com.tianji.common.exceptions.ServiceUnavailableException;
import com.tianji.common.exceptions.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-gateway concurrency bounds and shared, expiring Redis token buckets. */
@Component
public class GatewayTrafficLimits {
    private static final DefaultRedisScript<Long> BUCKET=new DefaultRedisScript<>("""
        local clock=redis.call('TIME')
        local now=tonumber(clock[1])+tonumber(clock[2])/1000000
        local capacity=tonumber(ARGV[1])
        local rate=tonumber(ARGV[2])/60
        local row=redis.call('HMGET',KEYS[1],'tokens','at')
        local tokens=tonumber(row[1]) or capacity
        local at=tonumber(row[2]) or now
        tokens=math.min(capacity,tokens+math.max(0,now-at)*rate)
        local retry=0
        if tokens>=1 then tokens=tokens-1 else retry=math.max(1,math.ceil((1-tokens)/rate)) end
        redis.call('HSET',KEYS[1],'tokens',tokens,'at',now)
        redis.call('EXPIRE',KEYS[1],math.ceil(capacity/rate)+60)
        return retry
        """,Long.class);
    private final StringRedisTemplate redis;
    private final int apiConcurrency,mediaConcurrency,mediaPerIp,userRate,userBurst,publicRate,publicBurst;
    private final Semaphore api,media;
    private final ConcurrentHashMap<String,Integer> mediaIps=new ConcurrentHashMap<>();

    @Autowired public GatewayTrafficLimits(StringRedisTemplate redis,Environment env){
        this(redis,value(env,"api-concurrency",160),value(env,"media-concurrency",16),value(env,"media-per-ip",4),
                value(env,"user-per-minute",300),value(env,"user-burst",60),value(env,"public-per-minute",120),value(env,"public-burst",30));
    }
    /** Constructor for standalone filter unit tests; production always injects Redis. */
    public GatewayTrafficLimits(){this(null,160,16,4,300,60,120,30);}
    GatewayTrafficLimits(StringRedisTemplate redis,int apiCount,int mediaCount,int perIp,int userRate,int userBurst,int publicRate,int publicBurst){
        if(java.util.stream.IntStream.of(apiCount,mediaCount,perIp,userRate,userBurst,publicRate,publicBurst).anyMatch(x->x<1))throw new IllegalArgumentException("Traffic limits must be positive");
        this.redis=redis;this.apiConcurrency=apiCount;this.mediaConcurrency=mediaCount;this.mediaPerIp=perIp;
        this.userRate=userRate;this.userBurst=userBurst;this.publicRate=publicRate;this.publicBurst=publicBurst;
        api=new Semaphore(apiCount,true);media=new Semaphore(mediaCount,true);
    }
    private static int value(Environment env,String key,int fallback){return env.getProperty("tj.gateway.traffic."+key,Integer.class,fallback);}
    public Semaphore apiCapacity(){return api;}
    public Lease acquire(boolean stream,String address){
        Semaphore pool=stream?media:api;
        try{
            // Stream requests fail fast so slow clients cannot occupy authorization workers waiting for a slot.
            if(!(stream?pool.tryAcquire():pool.tryAcquire(1500,TimeUnit.MILLISECONDS)))throw new TooManyRequestsException("服务繁忙，请稍后重试");
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new TooManyRequestsException("请求等待已取消");}
        String ip=stream?address:null;
        if(stream){
            var accepted=new AtomicBoolean();
            mediaIps.compute(ip,(key,count)->{if(count!=null && count>=mediaPerIp)return count;accepted.set(true);return count==null?1:count+1;});
            if(!accepted.get()){pool.release();throw new TooManyRequestsException("当前地址的视频连接过多，请关闭其他播放窗口");}
        }
        return new Lease(pool,ip);
    }
    public final class Lease implements AutoCloseable {
        private final Semaphore pool;private final String ip;private final AtomicBoolean closed=new AtomicBoolean();
        private Lease(Semaphore pool,String ip){this.pool=pool;this.ip=ip;}
        @Override public void close(){if(closed.compareAndSet(false,true)){if(ip!=null)mediaIps.computeIfPresent(ip,(key,count)->count==1?null:count-1);pool.release();}}
    }
    public void publicQuery(String ip){check("ip:"+hash(ip),publicRate,publicBurst);}
    public void authenticated(long user){check("user:"+user,userRate,userBurst);}
    private void check(String identity,int rate,int burst){
        if(redis==null)return;
        Long retry;
        try{retry=redis.execute(BUCKET,List.of("gateway:quota:"+identity),Integer.toString(burst),Integer.toString(rate));}
        catch(org.springframework.dao.DataAccessException error){throw new ServiceUnavailableException("请求额度暂时无法核验，请稍后重试");}
        if(retry==null)throw new ServiceUnavailableException("请求额度暂时无法核验，请稍后重试");
        if(retry>0)throw new TooManyRequestsException("请求频繁，请在 "+retry+" 秒后重试");
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}

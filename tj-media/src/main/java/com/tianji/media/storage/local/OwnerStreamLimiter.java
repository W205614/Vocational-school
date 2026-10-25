package com.tianji.media.storage.local;

import com.tianji.common.exceptions.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only verified course-ticket owners reach this limiter. Entries live only while a stream is open. */
@Component
public class OwnerStreamLimiter {
    private final int maximum;
    private final ConcurrentHashMap<Long,Integer> active=new ConcurrentHashMap<>();
    public OwnerStreamLimiter(@Value("${tj.media.streams-per-owner:2}") int maximum){
        if(maximum<1)throw new IllegalArgumentException("Positive owner stream limit required");this.maximum=maximum;
    }
    public Resource wrap(Resource source,long owner){
        return new AbstractResource(){
            @Override public String getDescription(){return source.getDescription();}
            @Override public String getFilename(){return source.getFilename();}
            @Override public long contentLength()throws IOException{return source.contentLength();}
            @Override public long lastModified()throws IOException{return source.lastModified();}
            @Override public InputStream getInputStream()throws IOException{
                var accepted=new AtomicBoolean();
                active.compute(owner,(key,count)->{if(count!=null && count>=maximum)return count;accepted.set(true);return count==null?1:count+1;});
                if(!accepted.get())throw new TooManyRequestsException("视频播放连接过多，请关闭其他播放窗口后重试");
                InputStream input;
                try{input=source.getInputStream();}catch(IOException|RuntimeException error){release(owner);throw error;}
                return new FilterInputStream(input){
                    private final AtomicBoolean closed=new AtomicBoolean();
                    @Override public void close()throws IOException{if(closed.compareAndSet(false,true)){try{super.close();}finally{release(owner);}}}
                };
            }
        };
    }
    private void release(long owner){active.computeIfPresent(owner,(key,count)->count==1?null:count-1);}
}

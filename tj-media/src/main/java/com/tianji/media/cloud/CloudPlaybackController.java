package com.tianji.media.cloud;

import com.tianji.api.client.learning.LearningClient;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.UserContext;
import com.tianji.media.storage.local.OwnerStreamLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpRange;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.util.*;

/** Range bytes cross this gate; issuing a ticket never exposes an unrevocable COS GET URL. */
@RestController
@ConditionalOnProperty(name="tj.cloud-media.enabled",havingValue="true")
@ConditionalOnProperty(name="tj.cloud-media.role",havingValue="api",matchIfMissing=true)
public class CloudPlaybackController {
    private final CloudCosMediaStorage storage;private final LearningClient learning;private final OwnerStreamLimiter streams;
    public CloudPlaybackController(CloudCosMediaStorage storage,LearningClient learning,OwnerStreamLimiter streams){this.storage=storage;this.learning=learning;this.streams=streams;}
    @GetMapping("/cloud-content/{id}")
    public void content(@PathVariable String id,@RequestParam long owner,@RequestParam long course,@RequestParam long expires,@RequestParam String signature,@RequestHeader(value="Range",required=false) String range,HttpServletResponse response)throws IOException{
        var row=storage.authorize(new CloudCosMediaStorage.Ticket(id,owner,course,expires,signature));
        if(course>0){
            Long originalUser=UserContext.getUser(),originalRole=UserContext.getRole();String session=UserContext.getSession();int depth=UserContext.getCallDepth();
            try{UserContext.setUser(owner);UserContext.setRole(2L);UserContext.setSession(null);if(learning.isLessonValid(course)==null)throw new ForbiddenException("课程权益已失效，请重新报名后播放");}
            finally{UserContext.removeUser();if(originalUser!=null)UserContext.setUser(originalUser);if(originalRole!=null)UserContext.setRole(originalRole);if(session!=null)UserContext.setSession(session);UserContext.setCallDepth(depth);}
        }
        long size=((Number)row.get("output_size")).longValue();long[] selected;
        try{selected=range(range,size);}catch(IllegalArgumentException error){response.setStatus(416);response.setHeader("Content-Range","bytes */"+size);return;}
        long first=selected[0],last=selected[1],length=last-first+1;
        var source=new AbstractResource(){public String getDescription(){return "private-cloud-media";}public long contentLength(){return length;}public InputStream getInputStream(){return storage.content(row,first,last);}};
        // Open before committing headers so connection admission failures retain their HTTP status.
        try(InputStream input=streams.wrap(source,owner).getInputStream()){
            response.setStatus(range==null?200:206);response.setContentType("video/mp4");response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Accept-Ranges","bytes");response.setContentLengthLong(length);
            if(range!=null)response.setHeader("Content-Range","bytes "+first+"-"+last+"/"+size);
            var output=response.getOutputStream();byte[] buffer=new byte[65536];long remaining=length;
            while(remaining>0){int count=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0)throw new EOFException("Private media response ended early");output.write(buffer,0,count);remaining-=count;}
        }
    }
    static long[] range(String header,long size){
        if(size<1)throw new IllegalArgumentException("Empty object");if(header==null)return new long[]{0,size-1};
        var list=HttpRange.parseRanges(header);if(list.size()!=1)throw new IllegalArgumentException("One byte range is required");
        long first=list.getFirst().getRangeStart(size),last=list.getFirst().getRangeEnd(size);if(first<0||first>=size||last<first||last>=size)throw new IllegalArgumentException("Unsatisfiable range");return new long[]{first,last};
    }
}

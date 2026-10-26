package com.tianji.media.cloud;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public interface CloudObjectStore {
    record Upload(String url,Map<String,String> headers){@Override public String toString(){return "Upload[url=REDACTED]";}}
    record ObjectInfo(long size,String contentType,String version,String etag){}
    void verifyPrivateBucket();
    Upload uploadUrl(String key,long size,Instant expiry);
    ObjectInfo head(String key);
    InputStream read(String key,String version,String etag,long first,long last);
    ObjectInfo putReady(String key,Path file);
    /** Private, immutable cover write. Duplicate keys return the existing pinned object for verification. */
    ObjectInfo putImage(String key,byte[] bytes,String contentType);
    void delete(String key);
}

package com.tianji.media.config;
import com.tianji.media.storage.*;import com.tianji.media.storage.local.*;import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.core.env.Environment;import tools.jackson.databind.json.JsonMapper;import java.io.*;import java.util.*;
@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="tj.local-storage.enabled",havingValue="true")
public class LocalStorageConfiguration {
 @Bean public LocalObjectStore localObjectStore(Environment env,JsonMapper json){return new LocalObjectStore(env,json);}
 @Bean public IMediaStorage localMediaStorage(LocalObjectStore store){return new LocalMediaStorage(store);}
 @Bean public IFileStorage localFileStorage(LocalObjectStore store){return new IFileStorage(){
  public String uploadFile(String key,InputStream stream,long length){return store.upload(key,stream,length);}
  public InputStream downloadFile(String key){return store.download(key);}
  public void deleteFile(String key){store.delete(key);}
  public void deleteFiles(List<String> keys){keys.forEach(store::delete);}
 };}
}

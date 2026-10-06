package com.tianji.media.storage.local;
import com.tianji.media.storage.*;import com.tianji.media.domain.po.Media;import java.util.*;import java.io.*;
public final class LocalMediaStorage implements IMediaStorage {
 private final LocalObjectStore store;public LocalMediaStorage(LocalObjectStore store){this.store=store;}
 @Override public String getUploadSignature(){throw new com.tianji.common.exceptions.BadRequestException("本地环境请使用本地视频上传接口");}
 @Override public String getPlaySignature(String id,Long user,Integer preview){
  if(preview!=null)throw new com.tianji.common.exceptions.BadRequestException("本地模拟器不支持限时试看，请先报名课程");
  return store.signedUrl(id);
 }
 @Override public MediaUploadResult uploadFile(String filename,InputStream input,long length){
  throw new com.tianji.common.exceptions.BadRequestException("本地视频需要显式提供模拟时长");
 }
 @Override public void deleteFile(String id){store.delete(id);}
 @Override public void deleteFiles(List<String> ids){ids.forEach(store::delete);}
 @Override public List<Media> queryMediaInfos(String...ids){return Arrays.stream(ids).map(store::metadata).toList();}
}

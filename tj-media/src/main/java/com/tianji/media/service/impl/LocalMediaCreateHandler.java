package com.tianji.media.service.impl;
import com.tianji.common.autoconfigure.reliability.OperationHandler;import com.tianji.common.utils.UserContext;import com.tianji.media.service.IMediaService;import com.tianji.media.storage.local.LocalObjectStore;import com.tianji.media.domain.dto.MediaUploadResultDTO;import org.springframework.stereotype.Service;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import lombok.RequiredArgsConstructor;import tools.jackson.databind.json.JsonMapper;
@Service @RequiredArgsConstructor @ConditionalOnProperty(name="tj.local-storage.enabled",havingValue="true")
public class LocalMediaCreateHandler implements OperationHandler{
 private final IMediaService medias;private final JsonMapper json;private final LocalObjectStore store;
 public String kind(){return "LOCAL_MEDIA_CREATE";}
 public Object execute(String id,long user,String payload){
  String file=json.readTree(payload).get("fileId").asString();var metadata=store.metadata(file);
  if(!java.util.Objects.equals(metadata.getCreater(),user))throw new com.tianji.common.exceptions.ForbiddenException("无权登记此视频");
  UserContext.setUser(user);UserContext.setRole(1L);try{var request=new MediaUploadResultDTO();request.setFileId(file);return medias.save(request);}finally{UserContext.removeUser();}
 }
}

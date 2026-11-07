package com.zhixuetang.media.service.impl;

import com.zhixuetang.common.autoconfigure.reliability.OperationHandler;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.media.service.IMediaService;
import com.zhixuetang.media.storage.local.LocalObjectStore;
import com.zhixuetang.media.domain.dto.MediaUploadResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxt.local-storage.enabled", havingValue = "true")
public class LocalMediaCreateHandler implements OperationHandler {
    private final IMediaService medias;
    private final JsonMapper json;
    private final LocalObjectStore store;

    public String kind() {
        return "LOCAL_MEDIA_CREATE";
    }

    public Object execute(String id, long user, String payload) {
        String file = json.readTree(payload).get("fileId").asString();
        var metadata = store.metadata(file);
        if (!java.util.Objects.equals(metadata.getCreater(), user))
            throw new com.zhixuetang.common.exceptions.ForbiddenException("无权登记此视频");
        UserContext.setUser(user);
        UserContext.setRole(1L);
        try {
            var request = new MediaUploadResultDTO();
            request.setFileId(file);
            return medias.save(request);
        } finally {
            UserContext.removeUser();
        }
    }
}

package com.tianji.auth.task;

import cn.hutool.core.collection.CollectionUtil;
import com.tianji.auth.common.domain.PrivilegeRoleDTO;
import com.tianji.auth.service.IPrivilegeService;
import com.tianji.auth.util.PrivilegeCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class LoadPrivilegeRunner{

    private final PermissionPublication publication;

    @PostConstruct
    public void loadPrivilegeCache(){
        try {
            log.trace("开始更新权限缓存数据");
            publication.rebuild();
            log.trace("更新权限缓存数据成功！");
        }catch (Exception e){
            log.error("更新权限缓存数据失败！原因：{}", e.getMessage());
        }
    }
}

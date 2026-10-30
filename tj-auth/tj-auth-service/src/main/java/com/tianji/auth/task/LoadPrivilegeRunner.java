package com.tianji.auth.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;


@Slf4j
@Component
@RequiredArgsConstructor
public class LoadPrivilegeRunner{

    private final PermissionPublication publication;

    @PostConstruct
    public void loadPrivilegeCache(){
        log.trace("开始更新权限缓存数据");
        publication.rebuild();
        log.trace("更新权限缓存数据成功！");
    }
}

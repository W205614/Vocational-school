package com.tianji.remark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 点赞任务配置属性类（绑定Nacos配置）
 */
@Data
@Component
@RefreshScope // 关键：支持配置动态刷新
@ConfigurationProperties(prefix = "tianji.remark.liked.task") // 配置前缀
public class LikedTaskProperties {
    /**
     * 点赞支持的业务类型列表
     */
    private List<String> bizTypes = List.of("QA", "NOTE"); // 默认值，防止配置为空

    /**
     * 单次发送MQ的最大数据量
     */
    private int maxSendSize = 30; // 默认值
}

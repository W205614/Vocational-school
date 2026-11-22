package com.zhixuetang.api.dto.message;

public record ProductNotice(
        String noticeId, Long userId, String title, String content,
        String targetType, Long targetId, Long courseId, Long sectionId) {}

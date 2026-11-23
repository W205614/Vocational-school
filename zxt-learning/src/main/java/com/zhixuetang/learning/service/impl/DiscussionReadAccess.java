package com.zhixuetang.learning.service.impl;

import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DiscussionReadAccess {
    private final LearningEntitlementService entitlements;
    private final CourseClient courses;

    void require(long user, long courseId) {
        Long role = UserContext.getRole();
        if (Long.valueOf(1).equals(role)) return;
        if (Long.valueOf(3).equals(role)) {
            var course = courses.getCourseInfoById(courseId, false, true);
            if (course == null
                    || course.getTeacherIds() == null
                    || !course.getTeacherIds().contains(user))
                throw new ForbiddenException("需要该课程的授课权限");
            return;
        }
        if (entitlements.findAvailableReadOnly(user, courseId) == null)
            throw new ForbiddenException("无有效课程权益");
    }
}

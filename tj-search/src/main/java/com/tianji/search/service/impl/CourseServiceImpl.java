package com.tianji.search.service.impl;
import com.tianji.search.service.ICourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
/** Repair requests persist a revision; the worker reads authoritative current course state. */
@Service @RequiredArgsConstructor
public class CourseServiceImpl implements ICourseService {
 private final JdbcTemplate jdbc;
 private void stage(Long id){jdbc.update("INSERT INTO course_metadata_projection(course_id) VALUES(?) ON DUPLICATE KEY UPDATE version=version+1,status='PENDING',attempts=0,next_attempt_at=NOW(3)",id);}
 public void handleCourseUp(Long id){stage(id);}
 public void handleCourseDelete(Long id){stage(id);}
 public void handleCourseDeletes(List<Long> ids){ids.stream().distinct().forEach(this::stage);}
 public void updateCourseSold(List<Long> ids,int amount){throw new IllegalStateException("销量必须由订单条目来源和版本化绝对值投影更新");}
}

package com.tianji.learning.service.impl;

import com.tianji.learning.enums.PointsRecordType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

@Service
public class LearningMilestoneService {
    private final JdbcTemplate jdbc;private final PointsRecordServiceImpl points;private final Clock clock;
    public LearningMilestoneService(JdbcTemplate jdbc,PointsRecordServiceImpl points,@Qualifier("learningClock") Clock clock){this.jdbc=jdbc;this.points=points;this.clock=clock;}
    /** Invoked only after the guarded first transition to a completed learning record. */
    public void completed(long user,long course,long section,boolean exam,LocalDateTime occurredAt) {
        LocalDateTime time=occurredAt==null?LocalDateTime.now(clock):occurredAt;
        int fresh=jdbc.update("INSERT IGNORE INTO learning_milestone(user_id,course_id,section_id,kind,completed_at) VALUES(?,?,?,?,?)",user,course,section,exam?"EXAM":"VIDEO",time);
        if(fresh==0)return;
        String key="complete:"+UUID.nameUUIDFromBytes((user+":"+course+":"+section).getBytes(StandardCharsets.UTF_8));
        points.award(user,10,PointsRecordType.LEARNING,key,time);
        if(exam)badge(user,"FIRST_EXAM","首次通过考试",time);
        Integer complete=jdbc.queryForObject("SELECT COUNT(*) FROM learning_lesson WHERE user_id=? AND course_id=? AND status=2",Integer.class,user,course);
        if(complete!=null && complete>0)badge(user,"FIRST_COURSE","首次完成课程",time);
        LocalDate day=time.toLocalDate();
        var days=jdbc.query("SELECT DISTINCT DATE(completed_at) AS completed_day FROM learning_milestone WHERE user_id=? AND completed_at>=? AND completed_at<? ORDER BY completed_day",(rs,n)->rs.getDate(1).toLocalDate(),user,day.minusDays(6).atStartOfDay(),day.plusDays(7).atStartOfDay());
        int run=0;LocalDate previous=null;
        for(LocalDate completed:days){run=previous!=null && previous.plusDays(1).equals(completed)?run+1:1;previous=completed;if(run>=7){badge(user,"LEARNING_7_DAYS","连续七天学有所成",time);break;}}
    }
    private void badge(long user,String code,String name,LocalDateTime time){jdbc.update("INSERT IGNORE INTO learning_achievement(user_id,code,season_month,name,awarded_at) VALUES(?,?,'',?,?)",user,code,name,time);}
}

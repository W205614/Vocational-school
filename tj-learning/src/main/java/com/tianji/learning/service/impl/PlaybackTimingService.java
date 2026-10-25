package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.course.CourseClient;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Bounded playback-session accounting, not proof that the student actually watched. */
@Service
public class PlaybackTimingService {
    public record Start(Long lessonId, Long sectionId) {}
    public record Heartbeat(String sessionId, long sequence, long positionMillis, double rate, String state) {}
    public record View(String sessionId, long watchedMillis, long creditedMillis, long requiredMillis,
                       long durationMillis, boolean finished, String completionSource, long lastSequence, boolean active) {}
    private static final Set<String> STATES=Set.of("PLAYING","PAUSED","BUFFERING","HIDDEN","SEEKING","ENDED");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CourseClient courses;
    private final LearningEntitlementService entitlements;
    private final LearningMilestoneService milestones;
    private final Clock clock;
    private final JsonMapper json=JsonMapper.builder().build();

    public PlaybackTimingService(JdbcTemplate jdbc, PlatformTransactionManager manager, CourseClient courses,
                                 LearningEntitlementService entitlements, LearningMilestoneService milestones,
                                 @Qualifier("learningClock") Clock clock) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.courses=courses;
        this.entitlements=entitlements;this.milestones=milestones;this.clock=clock;
    }

    public View start(Start form) {
        long user=UserContext.requireUser();
        if(form==null || form.lessonId()==null || form.sectionId()==null)throw new BadRequestException("请选择视频小节");
        var lessons=jdbc.queryForList("SELECT course_id FROM learning_lesson WHERE id=? AND user_id=?",form.lessonId(),user);
        if(lessons.isEmpty())throw new ForbiddenException("课表不存在");
        long course=number(lessons.getFirst(),"course_id");
        // Remote metadata is obtained before taking any local row locks.
        var section=courses.sectionInfo(form.sectionId());
        var metadata=courses.getCourseInfoById(course,false,false);
        if(section==null || !Objects.equals(section.getCourseId(),course) || !Integer.valueOf(2).equals(section.getType()) ||
                section.getMediaDuration()==null || section.getMediaDuration()<=0 || metadata==null || metadata.getSectionNum()==null || metadata.getSectionNum()<1)
            throw new BadRequestException("视频小节或服务端时长无效");
        long duration=Math.multiplyExact(section.getMediaDuration().longValue(),1000L);
        String id=UUID.randomUUID().toString();
        return tx.execute(status->{
            // Every timing write locks the one-user slot, then entitlement, lesson, record.
            // Acquire X directly on an existing slot. INSERT IGNORE would leave an S
            // lock that concurrent starters could deadlock while upgrading below.
            jdbc.update("INSERT INTO learning_playback_session(user_id,session_id,lesson_id,course_id,section_id,duration_millis,section_count,last_seen_at) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE user_id=user_id",
                    user,id,form.lessonId(),course,form.sectionId(),duration,metadata.getSectionNum(),now());
            jdbc.queryForMap("SELECT user_id FROM learning_playback_session WHERE user_id=? FOR UPDATE",user);
            requireLesson(user,course,form.lessonId());
            ensureRecord(user,form.lessonId(),form.sectionId());
            jdbc.update("UPDATE learning_playback_session SET session_id=?,lesson_id=?,course_id=?,section_id=?,duration_millis=?,section_count=?,last_sequence=0,last_position_millis=0,last_rate=1,last_state='PAUSED',last_seen_at=?,last_request_hash=NULL,last_response=NULL,ended=0 WHERE user_id=?",
                    id,form.lessonId(),course,form.sectionId(),duration,metadata.getSectionNum(),now(),user);
            return view(id,form.lessonId(),form.sectionId(),duration,0,true);
        });
    }

    public View heartbeat(Heartbeat beat, boolean end) {
        long user=UserContext.requireUser();
        validate(beat);
        String hash=hash(beat,end);
        return tx.execute(status->{
            var rows=jdbc.queryForList("SELECT * FROM learning_playback_session WHERE user_id=? FOR UPDATE",user);
            if(rows.isEmpty() || !beat.sessionId().equals(rows.getFirst().get("session_id")))
                throw new ConflictException("计时已在其他页面开始，请在当前页面重新开始播放");
            var session=rows.getFirst();long last=number(session,"last_sequence");
            if(beat.sequence()==last && hash.equals(session.get("last_request_hash")))
                return json.readValue(session.get("last_response").toString(),View.class);
            if(number(session,"ended")!=0 || beat.sequence()!=last+1)
                throw new ConflictException("播放计时顺序已变化，请重新开始播放");
            long lesson=number(session,"lesson_id"),course=number(session,"course_id"),section=number(session,"section_id"),duration=number(session,"duration_millis");
            if(beat.positionMillis()>duration+2000)throw new BadRequestException("播放位置超过服务端视频时长");
            requireLesson(user,course,lesson);
            var record=jdbc.queryForMap("SELECT * FROM learning_record WHERE lesson_id=? AND section_id=? FOR UPDATE",lesson,section);
            LocalDateTime at=now();
            long elapsed=Duration.between(com.tianji.common.utils.JdbcTime.localDateTime(session.get("last_seen_at")),at).toMillis();
            long delta=beat.positionMillis()-number(session,"last_position_millis");
            double priorRate=((Number)session.get("last_rate")).doubleValue();
            Credit credit=credit(last,elapsed,delta,priorRate,Objects.toString(session.get("last_state")),beat.state());
            long watched=Math.addExact(number(record,"watched_millis"),credit.watchedMillis());
            long credited=Math.min(duration,Math.addExact(number(record,"credited_millis"),credit.creditedMillis()));
            jdbc.update("UPDATE learning_record SET watched_millis=?,credited_millis=?,moment=GREATEST(COALESCE(moment,0),?) WHERE id=?",watched,credited,Math.min(duration,beat.positionMillis())/1000,record.get("id"));
            if(credited>=required(duration) && jdbc.update("UPDATE learning_record SET finished=1,finish_time=?,completion_source='TIMED' WHERE id=? AND finished=0",at,record.get("id"))==1) {
                jdbc.update("UPDATE learning_lesson SET status=CASE WHEN learned_sections+1>=? THEN 2 ELSE 1 END,learned_sections=learned_sections+1,latest_section_id=?,latest_learn_time=? WHERE id=?",session.get("section_count"),section,at,lesson);
                milestones.completed(user,course,section,false,at);
            } else {
                jdbc.update("UPDATE learning_lesson SET latest_section_id=?,latest_learn_time=? WHERE id=?",section,at,lesson);
            }
            boolean active=!end && !"ENDED".equals(beat.state());
            View result=view(beat.sessionId(),lesson,section,duration,beat.sequence(),active);
            jdbc.update("UPDATE learning_playback_session SET last_sequence=?,last_position_millis=?,last_rate=?,last_state=?,last_seen_at=?,last_request_hash=?,last_response=?,ended=? WHERE user_id=?",
                    beat.sequence(),Math.min(duration,beat.positionMillis()),beat.rate(),active?beat.state():"ENDED",at,hash,json.writeValueAsString(result),active?0:1,user);
            return result;
        });
    }

    public record Credit(long watchedMillis,long creditedMillis) {}
    /** Pure interval rule shared by database writes and deterministic tests. */
    public static Credit credit(long previousSequence,long elapsed,long delta,double rate,String previousState,String state) {
        if(previousSequence<1 || elapsed<=0 || elapsed>30_000 || delta<=0 || !"PLAYING".equals(previousState) || "SEEKING".equals(state))return new Credit(0,0);
        long expected=(long)Math.floor(elapsed*rate);
        // A seek is a rebase, even if a caller omits its SEEKING event.
        if(delta>expected+2000)return new Credit(0,0);
        long credited=Math.min(delta,expected);
        return new Credit(Math.min(elapsed,(long)Math.floor(credited/rate)),credited);
    }
    private static long required(long duration){return (duration*9+9)/10;}
    private void requireLesson(long user,long course,long lesson) {
        if(entitlements.require(user,course)!=lesson)throw new ForbiddenException("课程权益已变化");
        if(jdbc.queryForList("SELECT id FROM learning_lesson WHERE id=? AND user_id=? AND course_id=? FOR UPDATE",lesson,user,course).isEmpty())throw new ForbiddenException("课表不存在");
    }
    private void ensureRecord(long user,long lesson,long section) {
        jdbc.update("INSERT IGNORE INTO learning_record(id,lesson_id,section_id,user_id,moment,finished) VALUES(?,?,?,?,0,0)",IdWorker.getId(),lesson,section,user);
    }
    private View view(String id,long lesson,long section,long duration,long sequence,boolean active) {
        var row=jdbc.queryForMap("SELECT watched_millis,credited_millis,finished,completion_source FROM learning_record WHERE lesson_id=? AND section_id=? FOR UPDATE",lesson,section);
        return new View(id,number(row,"watched_millis"),number(row,"credited_millis"),required(duration),duration,number(row,"finished")!=0,
                row.get("completion_source")==null?null:row.get("completion_source").toString(),sequence,active);
    }
    private static void validate(Heartbeat beat) {
        if(beat==null || beat.sessionId()==null || !beat.sessionId().matches("[0-9a-f-]{36}") || beat.sequence()<1 || beat.positionMillis()<0 ||
                !Double.isFinite(beat.rate()) || beat.rate()<.5 || beat.rate()>2 || beat.state()==null || !STATES.contains(beat.state()))throw new BadRequestException("播放计时参数无效，支持 0.5 至 2 倍速");
    }
    private static long number(Map<String,Object> row,String field){Object value=row.get(field);return value instanceof Boolean b?(b?1:0):((Number)value).longValue();}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),clock.getZone());}
    private String hash(Heartbeat beat,boolean end) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((json.writeValueAsString(beat)+":"+end).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
}

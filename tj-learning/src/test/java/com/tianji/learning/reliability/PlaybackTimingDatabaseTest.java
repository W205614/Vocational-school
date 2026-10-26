package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.media.MediaClient;
import com.tianji.api.dto.course.*;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.impl.*;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class PlaybackTimingDatabaseTest {
    private JdbcTemplate jdbc;private DataSourceTransactionManager manager;private CourseClient courses;private MediaClient media;
    private LearningEntitlementService rights;private LearningMilestoneService milestones;private PlaybackTimingService service;
    private MutableClock clock;private long user,lesson,course,section;
    @BeforeEach void setup(){
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);user=IdWorker.getId();lesson=user+1;course=user+2;section=user+3;
        // The minimal acceptance schema omits this direct legacy-endpoint dependency.
        // Match V002__progress_integrity.sql without changing production migrations.
        jdbc.execute("CREATE TABLE IF NOT EXISTS learning_progress_pending (record_id BIGINT PRIMARY KEY,lesson_id BIGINT NOT NULL,section_id BIGINT NOT NULL,moment INT NOT NULL,version BIGINT NOT NULL,processed_version BIGINT NOT NULL DEFAULT 0,updated_at DATETIME(3) NOT NULL,KEY idx_progress_dirty(updated_at,processed_version,version)) ENGINE=InnoDB");
        UserContext.setUser(user);UserContext.setRole(2L);
        clock=new MutableClock(LocalDate.of(5000+ThreadLocalRandom.current().nextInt(4000),1,15).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,learned_sections) VALUES(?,?,?,0,0)",lesson,user,course);
        jdbc.update("INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active) VALUES(?,?,?,?,1)",user+10,user+11,user,course);
        courses=mock(CourseClient.class);when(courses.sectionInfo(section)).thenReturn(new SectionInfoDTO(course,user+20,false,0,20,2));
        media=mock(MediaClient.class);when(media.durationMillis(user+20)).thenReturn(20_000L);
        var metadata=new CourseFullInfoDTO();metadata.setId(course);metadata.setSectionNum(1);when(courses.getCourseInfoById(course,false,false)).thenReturn(metadata);
        rights=new LearningEntitlementService(jdbc);
        milestones=new LearningMilestoneService(jdbc,new PointsRecordServiceImpl(jdbc,new InboxStore(jdbc,new TransactionTemplate(manager)),clock),clock);
        service=new PlaybackTimingService(jdbc,manager,courses,media,rights,milestones,clock);
    }
    @AfterEach void cleanup(){
        try{if(jdbc!=null && lesson!=0)jdbc.update("DELETE FROM learning_progress_pending WHERE lesson_id=?",lesson);}
        finally{UserContext.removeUser();}
    }
    private PlaybackTimingService.View start(){return service.start(new PlaybackTimingService.Start(lesson,section));}
    private PlaybackTimingService.Heartbeat beat(String id,long seq,long pos,double rate,String state){return new PlaybackTimingService.Heartbeat(id,seq,pos,rate,state);}
    @Test void ninetyPercentCompletesAtTwoTimesSpeedAndDuplicateResponseCannotAwardTwice(){
        var session=start();assertEquals(18_000,session.requiredMillis());
        var first=service.heartbeat(beat(session.sessionId(),1,0,2,"PLAYING"),false);assertEquals(0,first.creditedMillis());
        clock.advance(9000);var command=beat(session.sessionId(),2,18_000,2,"PLAYING");var completed=service.heartbeat(command,false);
        assertTrue(completed.finished());assertEquals(9000,completed.watchedMillis());assertEquals(18_000,completed.creditedMillis());assertEquals("TIMED",completed.completionSource());
        assertEquals(completed,service.heartbeat(command,false));
        assertThrows(ConflictException.class,()->service.heartbeat(beat(session.sessionId(),2,19_000,2,"PLAYING"),false));
        clock.advance(1000);service.heartbeat(beat(session.sessionId(),3,20_000,2,"ENDED"),true);
        assertEquals(1,jdbc.queryForObject("SELECT learned_sections FROM learning_lesson WHERE id=?",Integer.class,lesson));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_milestone WHERE user_id=?",Integer.class,user));
        assertEquals(10,jdbc.queryForObject("SELECT SUM(points) FROM points_record WHERE user_id=?",Integer.class,user));
    }
    @Test void seekHiddenBufferingAndDisconnectRebaseWithoutBackfilling(){
        String id=start().sessionId();service.heartbeat(beat(id,1,0,1,"PLAYING"),false);
        clock.advance(1000);assertEquals(0,service.heartbeat(beat(id,2,8000,1,"PLAYING"),false).creditedMillis());
        clock.advance(1000);assertEquals(1000,service.heartbeat(beat(id,3,9000,1,"PLAYING"),false).creditedMillis());
        service.heartbeat(beat(id,4,9000,1,"HIDDEN"),false);clock.advance(20_000);
        assertEquals(1000,service.heartbeat(beat(id,5,15_000,1,"PLAYING"),false).creditedMillis());
        clock.advance(31_000);assertEquals(1000,service.heartbeat(beat(id,6,16_000,1,"PLAYING"),false).creditedMillis());
        service.heartbeat(beat(id,7,16_000,1,"BUFFERING"),false);clock.advance(1000);
        assertEquals(1000,service.heartbeat(beat(id,8,17_000,1,"PLAYING"),false).creditedMillis());
        clock.advance(1000);assertEquals(2000,service.heartbeat(beat(id,9,18_000,1,"PAUSED"),false).creditedMillis());
        assertFalse(jdbc.queryForObject("SELECT finished FROM learning_record WHERE lesson_id=?",Boolean.class,lesson));
    }
    @Test void oneUserSlotRejectsOldTabAndAnotherUserAndSurvivesServiceRestart(){
        String old=start().sessionId();service.heartbeat(beat(old,1,0,1,"PLAYING"),false);clock.advance(3000);service.heartbeat(beat(old,2,3000,1,"PLAYING"),false);
        String current=start().sessionId();assertNotEquals(old,current);assertThrows(ConflictException.class,()->service.heartbeat(beat(old,3,4000,1,"PLAYING"),false));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_playback_session WHERE user_id=?",Integer.class,user));
        UserContext.setUser(user+100);assertThrows(ConflictException.class,()->service.heartbeat(beat(current,1,3000,1,"PLAYING"),false));UserContext.setUser(user);
        service.heartbeat(beat(current,1,3000,1,"PLAYING"),false);clock.advance(2000);
        var restarted=new PlaybackTimingService(jdbc,manager,courses,media,rights,milestones,clock);
        assertEquals(5000,restarted.heartbeat(beat(current,2,5000,1,"PLAYING"),false).creditedMillis());
        assertThrows(ConflictException.class,()->restarted.heartbeat(beat(current,4,6000,1,"PLAYING"),false));
    }
    @Test void refundAndForeignLessonRejectNewWritesButKeepHistory(){
        String id=start().sessionId();service.heartbeat(beat(id,1,0,1,"PLAYING"),false);clock.advance(1000);service.heartbeat(beat(id,2,1000,1,"PLAYING"),false);
        jdbc.update("UPDATE learning_entitlement SET active=0 WHERE user_id=?",user);clock.advance(1000);
        assertThrows(ForbiddenException.class,()->service.heartbeat(beat(id,3,2000,1,"PLAYING"),false));
        assertEquals(1000,jdbc.queryForObject("SELECT credited_millis FROM learning_record WHERE lesson_id=?",Long.class,lesson));
        assertThrows(ForbiddenException.class,()->service.start(new PlaybackTimingService.Start(lesson+100,section)));
    }
    @Test void legacyCompletionIsRetainedWithoutIssuingAnotherReward(){
        jdbc.update("INSERT INTO learning_record(id,lesson_id,section_id,user_id,moment,finished,completion_source) VALUES(?,?,?,?,20,1,'LEGACY')",user+30,lesson,section,user);
        jdbc.update("UPDATE learning_lesson SET learned_sections=1,status=2 WHERE id=?",lesson);
        var session=start();assertTrue(session.finished());assertEquals("LEGACY",session.completionSource());
        service.heartbeat(beat(session.sessionId(),1,0,2,"PLAYING"),false);clock.advance(9000);service.heartbeat(beat(session.sessionId(),2,18000,2,"PLAYING"),false);
        assertEquals(1,jdbc.queryForObject("SELECT learned_sections FROM learning_lesson WHERE id=?",Integer.class,lesson));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_milestone WHERE user_id=?",Integer.class,user));
    }
    @Test void fractionalRealDurationOverridesRoundedCatalogueAndCompletesWithinOnePlayback(){
        when(courses.sectionInfo(section)).thenReturn(new SectionInfoDTO(course,user+20,false,0,2,2));
        when(media.durationMillis(user+20)).thenReturn(1280L);
        var session=start();assertEquals(1280,session.durationMillis());assertEquals(1152,session.requiredMillis());
        service.heartbeat(beat(session.sessionId(),1,0,1,"PLAYING"),false);
        clock.advance(1280);
        var finished=service.heartbeat(beat(session.sessionId(),2,1280,1,"ENDED"),true);
        assertTrue(finished.finished());assertEquals(1280,finished.creditedMillis());
    }
    @Test void invalidMediaDurationCannotCreateSessionOrFallBackToCatalogue(){
        for(Long value:Arrays.asList(null,0L,-1L,86_400_001L)){
            when(media.durationMillis(user+20)).thenReturn(value);
            assertThrows(BadRequestException.class,this::start);
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_playback_session WHERE user_id=?",Integer.class,user));
    }
    @Test void oldPositionEndpointCannotCompleteOrAwardAndUnsupportedSpeedIsRejected(){
        var lessons=mock(ILearningLessonService.class);when(lessons.getById(lesson)).thenReturn(new LearningLesson().setId(lesson).setUserId(user).setCourseId(course));
        var legacy=new LearningRecordServiceImpl(lessons,courses,new LearningRecordDelayTaskHandler(jdbc,manager,rights),jdbc,manager,rights,milestones,clock);
        var form=new LearningRecordFormDTO();form.setLessonId(lesson);form.setSectionId(section);form.setSectionType(SectionType.VIDEO);form.setMoment(20);legacy.addLearningRecord(form);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_progress_pending WHERE lesson_id=? AND moment=20",Integer.class,lesson));
        assertFalse(jdbc.queryForObject("SELECT finished FROM learning_record WHERE lesson_id=?",Boolean.class,lesson));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_milestone WHERE user_id=?",Integer.class,user));
        var session=start();assertThrows(BadRequestException.class,()->service.heartbeat(beat(session.sessionId(),1,0,3,"PLAYING"),false));
    }
    @Test void parallelIdenticalHeartbeatsCountTheIntervalOnce()throws Exception{
        String id=start().sessionId();service.heartbeat(beat(id,1,0,1,"PLAYING"),false);clock.advance(5000);
        var command=beat(id,2,5000,1,"PLAYING");
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<PlaybackTimingService.View> work=()->{UserContext.setUser(user);try{return service.heartbeat(command,false);}finally{UserContext.removeUser();}};
            var a=pool.submit(work);var b=pool.submit(work);assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(5000,jdbc.queryForObject("SELECT credited_millis FROM learning_record WHERE lesson_id=?",Long.class,lesson));
    }
    @Test void simultaneousStartsTakeTheExistingSlotWithoutSharedLockUpgradeDeadlocks()throws Exception{
        start(); // Exercise the duplicate-key path, rather than only a fresh insert.
        var ready=new CountDownLatch(4);var release=new CountDownLatch(1);
        var sessions=new ArrayList<PlaybackTimingService.View>();
        try(var pool=Executors.newFixedThreadPool(4)){
            var work=new ArrayList<Future<PlaybackTimingService.View>>();
            for(int index=0;index<4;index++)work.add(pool.submit(()->{
                UserContext.setUser(user);UserContext.setRole(2L);ready.countDown();
                try{assertTrue(release.await(5,TimeUnit.SECONDS));return start();}
                finally{UserContext.removeUser();}
            }));
            assertTrue(ready.await(5,TimeUnit.SECONDS));release.countDown();
            for(var task:work)sessions.add(task.get(15,TimeUnit.SECONDS));
        }
        assertEquals(4,sessions.stream().map(PlaybackTimingService.View::sessionId).distinct().count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_playback_session WHERE user_id=?",Integer.class,user));
        String active=jdbc.queryForObject("SELECT session_id FROM learning_playback_session WHERE user_id=?",String.class,user);
        for(var candidate:sessions){
            var command=beat(candidate.sessionId(),1,0,1,"PLAYING");
            if(candidate.sessionId().equals(active))assertTrue(service.heartbeat(command,false).active());
            else assertThrows(ConflictException.class,()->service.heartbeat(command,false));
        }
    }
    private static final class MutableClock extends Clock {
        private Instant value;MutableClock(Instant value){this.value=value;}void advance(long millis){value=value.plusMillis(millis);}
        @Override public ZoneId getZone(){return ZoneId.of("Asia/Shanghai");}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return value;}
    }
}

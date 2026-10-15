package com.tianji.learning.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.impl.*;
import com.tianji.learning.service.impl.LearningHubOperationHandler.Command;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v2/learning-hub")
public class LearningHubController {
    private final JdbcTemplate jdbc;private final Clock clock;private final LearningSeasonService seasons;
    private final ILearningLessonService lessons;private final OperationStore operations;private final LearningHubOperationHandler handler;
    public LearningHubController(JdbcTemplate jdbc,@Qualifier("learningClock") Clock clock,LearningSeasonService seasons,ILearningLessonService lessons,OperationStore operations,LearningHubOperationHandler handler){this.jdbc=jdbc;this.clock=clock;this.seasons=seasons;this.lessons=lessons;this.operations=operations;this.handler=handler;}
    @GetMapping("/overview") public Object overview() {
        long user=UserContext.requireUser();var plan=lessons.queryMyPlans(new PageQuery().setPageSize(100));
        var result=new LinkedHashMap<String,Object>();result.put("weekFinished",plan.getWeekFinished());result.put("weekTotalPlan",Objects.requireNonNullElse(plan.getWeekTotalPlan(),0));result.put("weekPoints",Objects.requireNonNullElse(plan.getWeekPoints(),0));result.put("plans",plan.getList()==null?List.of():plan.getList());
        result.put("currentSeason",seasons.seasons().stream().filter(s->seasons.currentMonth().equals(s.get("month"))).findFirst().orElse(Map.of()));
        result.put("recentAchievements",jdbc.queryForList("SELECT code,name,awarded_at AS awardedAt,season_month AS seasonMonth FROM learning_achievement WHERE user_id=? ORDER BY awarded_at DESC LIMIT 5",user));
        var next=jdbc.queryForList("SELECT course_id AS courseId,latest_section_id AS sectionId,learned_sections AS learnedSections,latest_learn_time AS latestLearnTime FROM learning_lesson WHERE user_id=? AND status IN(0,1) AND (expire_time IS NULL OR expire_time>?) ORDER BY latest_learn_time DESC,id DESC LIMIT 1",user,LocalDateTime.now(clock));
        result.put("continueLearning",next.isEmpty()?null:next.getFirst());return result;
    }
    @GetMapping("/seasons") public Object seasons(){UserContext.requireUser();return seasons.seasons();}
    @GetMapping("/board") public Object board(@RequestParam(required=false) String month,PageQuery query){if(month==null)seasons.seasons();return seasons.board(month,query);}
    @GetMapping("/points") public Object points(PageQuery page) {
        page.validate();long user=UserContext.requireUser();
        var list=jdbc.queryForList("SELECT id,type,CASE type WHEN 1 THEN '课程学习' WHEN 2 THEN '每日签到' WHEN 3 THEN '回答采纳' WHEN 4 THEN '课程笔记' WHEN 5 THEN '课程评价' ELSE '其他积分' END AS typeName,points,occurred_at AS occurredAt,create_time AS creditedAt,board_month AS month,late FROM points_record WHERE user_id=? ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?",user,page.getPageSize(),page.from());
        return Map.of("list",list,"total",jdbc.queryForObject("SELECT COUNT(*) FROM points_record WHERE user_id=?",Long.class,user));
    }
    @GetMapping("/achievements") public Object achievements(){return Map.of("list",jdbc.queryForList("SELECT code,name,season_month AS seasonMonth,awarded_at AS awardedAt FROM learning_achievement WHERE user_id=? ORDER BY awarded_at DESC,code",UserContext.requireUser()));}
    @GetMapping("/rewards") public Object rewards(PageQuery page){page.validate();long user=UserContext.requireUser();return Map.of("list",jdbc.queryForList(rewardSelect()+" WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",user,page.getPageSize(),page.from()),"total",jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE user_id=?",Long.class,user));}
    @GetMapping("/reward-rules") public Object publicRules(@RequestParam(required=false) String month){if(month==null)month=seasons.currentMonth();LearningSeasonService.parseMonth(month);return Map.of("list",jdbc.queryForList("SELECT rank_from AS rankFrom,rank_to AS rankTo,coupon_id AS couponId FROM learning_reward_rule WHERE board_month=? AND enabled=1 ORDER BY rank_from",month));}
    @GetMapping("/teaching/questions") public Object teachingQuestions(@RequestParam long courseId,PageQuery page) {
        page.validate();long actor=UserContext.requireUser();handler.requireTeacher(actor,UserContext.getRole(),courseId);
        return Map.of("list",jdbc.queryForList("SELECT id,title,description,course_id AS courseId,section_id AS sectionId,answer_times AS answerTimes,accepted_reply_id AS acceptedReplyId,accepted_at AS acceptedAt,create_time AS createTime FROM interaction_question WHERE course_id=? AND hidden=0 ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?",courseId,page.getPageSize(),page.from()),"total",jdbc.queryForObject("SELECT COUNT(*) FROM interaction_question WHERE course_id=? AND hidden=0",Long.class,courseId));
    }
    public record Acceptance(Long replyId) {}
    @PostMapping("/questions/{id}/accept") public ResponseEntity<OperationStore.View> accept(@PathVariable long id,@RequestBody Acceptance form,@RequestHeader("Idempotency-Key") String key) {
        long actor=UserContext.requireUser();if(form.replyId()==null || form.replyId()<1)throw new BadRequestException("回答标识无效");
        var course=jdbc.queryForList("SELECT course_id FROM interaction_question WHERE id=? AND hidden=0",Long.class,id);if(course.isEmpty())throw new BadRequestException("问题不存在");handler.requireTeacher(actor,UserContext.getRole(),course.getFirst());
        Command request=new Command("ACCEPT",UserContext.getRole(),id,form.replyId(),null,null,null,null,null,null,null,null,null);
        Command snapshot=new Command("ACCEPT",UserContext.getRole(),id,form.replyId(),null,null,null,null,null,null,null,null,LocalDateTime.now(clock));
        return ResponseEntity.accepted().body(operations.submit(actor,"LEARNING_ACCEPT",key,request,snapshot));
    }
    @GetMapping("/admin/seasons") public Object adminSeasons(){admin();return Map.of("list",seasons.seasons());}
    @GetMapping("/admin/points-statistics") public Object statistics(@RequestParam(required=false) String month) {
        admin();if(month==null)month=seasons.currentMonth();LearningSeasonService.parseMonth(month);
        return Map.of("list",jdbc.queryForList("SELECT type,CASE type WHEN 1 THEN '课程学习' WHEN 2 THEN '每日签到' WHEN 3 THEN '回答采纳' WHEN 4 THEN '课程笔记' WHEN 5 THEN '课程评价' ELSE '其他积分' END AS typeName,SUM(points) AS points,COUNT(*) AS events,COUNT(DISTINCT user_id) AS students FROM points_record WHERE board_month=? GROUP BY type ORDER BY type",month));
    }
    @GetMapping("/admin/reward-rules") public Object rules(@RequestParam String month){admin();LearningSeasonService.parseMonth(month);return Map.of("list",jdbc.queryForList("SELECT CAST(id AS CHAR) AS id,board_month AS month,rank_from AS rankFrom,rank_to AS rankTo,CAST(coupon_id AS CHAR) AS couponId,enabled,version FROM learning_reward_rule WHERE board_month=? ORDER BY rank_from,id",month));}
    public record RuleForm(String month,Integer rankFrom,Integer rankTo,Long couponId,Boolean enabled,Long version) {}
    @PutMapping("/admin/reward-rules/{id}") public ResponseEntity<OperationStore.View> rule(@PathVariable long id,@RequestBody RuleForm form,@RequestHeader("Idempotency-Key") String key) {
        admin();long actor=UserContext.requireUser();Command request=new Command("RULE",UserContext.getRole(),null,null,id,form.month(),form.rankFrom(),form.rankTo(),form.couponId(),form.enabled(),form.version(),null,null);
        Command snapshot=new Command("RULE",UserContext.getRole(),null,null,id==0?IdWorker.getId():id,form.month(),form.rankFrom(),form.rankTo(),form.couponId(),form.enabled(),form.version(),null,null);
        return ResponseEntity.accepted().body(operations.submit(actor,"LEARNING_ACCEPT",key,request,snapshot));
    }
    @GetMapping("/admin/rewards") public Object adminRewards(@RequestParam(required=false) String month,PageQuery page){admin();page.validate();if(month!=null)LearningSeasonService.parseMonth(month);String where=month==null?"":" WHERE board_month=?";List<Object> args=new ArrayList<>();if(month!=null)args.add(month);long total=jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request"+where,Long.class,args.toArray());args.add(page.getPageSize());args.add(page.from());return Map.of("list",jdbc.queryForList(rewardSelect()+where+" ORDER BY created_at DESC LIMIT ? OFFSET ?",args.toArray()),"total",total);}
    @PostMapping("/admin/rewards/{requestId}/retry") public ResponseEntity<OperationStore.View> retry(@PathVariable String requestId,@RequestHeader("Idempotency-Key") String key){admin();return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"LEARNING_ACCEPT",key,new Command("RETRY",UserContext.getRole(),null,null,null,null,null,null,null,null,null,requestId,null)));}
    @GetMapping("/admin/reconciliations") public Object reconciliations(PageQuery page){admin();page.validate();return Map.of("list",jdbc.queryForList("SELECT id,user_id AS userId,board_month AS month,event_key AS eventKey,type,points,occurred_at AS occurredAt,status,created_at AS createdAt FROM learning_points_reconciliation ORDER BY created_at DESC LIMIT ? OFFSET ?",page.getPageSize(),page.from()),"total",jdbc.queryForObject("SELECT COUNT(*) FROM learning_points_reconciliation",Long.class));}
    private void admin(){UserContext.requireUser();LearningHubOperationHandler.requireAdmin(UserContext.getRole());}
    private static String rewardSelect(){return "SELECT request_id AS requestId,board_month AS seasonMonth,user_id AS userId,rank_no AS rankNo,CAST(coupon_id AS CHAR) AS couponId,status,CAST(user_coupon_id AS CHAR) AS userCouponId,last_error AS lastError,attempts,created_at AS createdAt FROM learning_reward_request";}
}

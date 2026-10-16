package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.common.autoconfigure.reliability.OperationHandler;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.exceptions.*;
import com.tianji.learning.enums.PointsRecordType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

/** Durable commands retain actor role and business time from the authenticated request. */
@Service
public class LearningHubOperationHandler implements OperationHandler {
    private final JdbcTemplate jdbc;private final JsonMapper json;private final CourseClient courses;private final UserClient users;
    private final PointsRecordServiceImpl points;private final LearningSeasonService seasons;private final OutboxStore outbox;private final Clock clock;
    public LearningHubOperationHandler(JdbcTemplate jdbc,JsonMapper json,CourseClient courses,UserClient users,PointsRecordServiceImpl points,LearningSeasonService seasons,OutboxStore outbox,@Qualifier("learningClock") Clock clock){this.jdbc=jdbc;this.json=json;this.courses=courses;this.users=users;this.points=points;this.seasons=seasons;this.outbox=outbox;this.clock=clock;}
    public record Command(String action,Long role,Long questionId,Long replyId,Long ruleId,String month,Integer rankFrom,Integer rankTo,Long couponId,Boolean enabled,Long version,String requestId,LocalDateTime occurredAt) {}
    @Override public String kind(){return "LEARNING_ACCEPT";}
    @Override public Object execute(String operation,long actor,String payload) {
        Command cmd=json.readValue(payload,Command.class);
        return switch(cmd.action()) {
            case "ACCEPT"->accept(actor,cmd);
            case "RULE"->rule(actor,cmd);
            case "RETRY"->retry(cmd);
            default->throw new BadRequestException("未知学习激励操作");
        };
    }
    public void requireTeacher(long actor,Long role,long course) {
        if(Long.valueOf(1).equals(role))return;
        if(!Long.valueOf(3).equals(role))throw new ForbiddenException("仅授课教师可采纳回答");
        var info=courses.getCourseInfoById(course,false,true);
        if(info==null || info.getTeacherIds()==null || !info.getTeacherIds().contains(actor))throw new ForbiddenException("需要该课程的授课权限");
    }
    private Object accept(long actor,Command cmd) {
        var questions=jdbc.queryForList("SELECT * FROM interaction_question WHERE id=? AND hidden=0 FOR UPDATE",cmd.questionId());
        if(questions.isEmpty())throw new BadRequestException("问题不存在");var question=questions.getFirst();
        requireTeacher(actor,cmd.role(),((Number)question.get("course_id")).longValue());
        if(question.get("accepted_reply_id")!=null) {
            if(((Number)question.get("accepted_reply_id")).longValue()==cmd.replyId())return Map.of("acceptedReplyId",cmd.replyId());
            throw new ConflictException("问题已有采纳回答，不能重复更换奖励");
        }
        var replies=jdbc.queryForList("SELECT * FROM interaction_reply WHERE id=? AND question_id=? AND hidden=0 FOR UPDATE",cmd.replyId(),cmd.questionId());
        if(replies.isEmpty())throw new BadRequestException("回答不存在");var reply=replies.getFirst();
        if(reply.get("answer_id")!=null && ((Number)reply.get("answer_id")).longValue()!=0)throw new BadRequestException("只能采纳问题的顶层回答");
        long student=((Number)reply.get("user_id")).longValue();
        if(student==actor || student==((Number)question.get("user_id")).longValue())throw new BadRequestException("只能奖励其他学员提供的有效回答");
        // Durable workers have no browser identity. Use the existing service-authenticated
        // account contract, not the interactive /users/{id} endpoint or configurable role IDs.
        com.tianji.common.domain.dto.LoginUserDTO author;
        try {author=users.sessionIdentity(student);}
        catch(feign.FeignException failure) {
            if(failure.status()==401)throw new BadRequestException("回答用户已禁用或不存在");
            throw failure;
        }
        if(author==null || !Objects.equals(author.getUserId(),student) || !Integer.valueOf(2).equals(author.getUserType()))throw new BadRequestException("只能奖励其他学员提供的有效回答");
        LocalDateTime time=cmd.occurredAt()==null?LocalDateTime.now(clock):cmd.occurredAt();
        jdbc.update("UPDATE interaction_question SET accepted_reply_id=?,accepted_by=?,accepted_at=? WHERE id=?",cmd.replyId(),actor,time,cmd.questionId());
        points.award(student,5,PointsRecordType.QA,"accepted:"+cmd.questionId(),time);
        outbox.enqueue("accepted-notice:"+cmd.questionId(),"learning.topic","learning.notice.created",Map.of("noticeId","accepted:"+cmd.questionId(),"userId",student,"title","你的回答已被教师采纳","content","课程回答获得采纳，积分按每日规则计入赛季。","bizId",cmd.questionId()));
        return Map.of("acceptedReplyId",cmd.replyId());
    }
    private Object rule(long actor,Command cmd) {
        requireAdmin(cmd.role());var month=LearningSeasonService.parseMonth(cmd.month());
        if(!month.isAfter(YearMonth.now(clock)))throw new BadRequestException("只能配置尚未开始的未来赛季，历史不补发");
        if(cmd.ruleId()==null || cmd.rankFrom()==null || cmd.rankTo()==null || cmd.rankFrom()<1 || cmd.rankTo()<cmd.rankFrom() || cmd.rankTo()>1000 || cmd.couponId()==null || cmd.couponId()<1 || cmd.enabled()==null || cmd.version()==null)throw new BadRequestException("奖励规则参数无效");
        LearningSeasonService.ensureSeason(jdbc,month);jdbc.queryForMap("SELECT state FROM learning_season WHERE board_month=? FOR UPDATE",cmd.month());
        var existing=jdbc.queryForList("SELECT * FROM learning_reward_rule WHERE id=? FOR UPDATE",cmd.ruleId());
        if(!existing.isEmpty() && !cmd.month().equals(existing.getFirst().get("board_month")))throw new ConflictException("规则不能移动到其他赛季");
        Integer overlaps=jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_rule WHERE board_month=? AND id<>? AND enabled=1 AND rank_from<=? AND rank_to>=?",Integer.class,cmd.month(),cmd.ruleId(),cmd.rankTo(),cmd.rankFrom());
        if(cmd.enabled() && overlaps!=null && overlaps>0)throw new ConflictException("奖励名次区间不能重叠");
        if(existing.isEmpty()) {
            if(cmd.version()!=0)throw new ConflictException("奖励规则版本已变化");
            jdbc.update("INSERT INTO learning_reward_rule(id,board_month,rank_from,rank_to,coupon_id,enabled,created_by) VALUES(?,?,?,?,?,?,?)",cmd.ruleId(),cmd.month(),cmd.rankFrom(),cmd.rankTo(),cmd.couponId(),cmd.enabled(),actor);
        }else if(jdbc.update("UPDATE learning_reward_rule SET rank_from=?,rank_to=?,coupon_id=?,enabled=?,version=version+1,updated_at=? WHERE id=? AND version=?",cmd.rankFrom(),cmd.rankTo(),cmd.couponId(),cmd.enabled(),LocalDateTime.now(clock),cmd.ruleId(),cmd.version())!=1)throw new ConflictException("奖励规则已被其他操作更新");
        return Map.of("id",cmd.ruleId());
    }
    private Object retry(Command cmd) {
        requireAdmin(cmd.role());var rows=jdbc.queryForList("SELECT * FROM learning_reward_request WHERE request_id=? FOR UPDATE",cmd.requestId());
        if(rows.isEmpty())throw new BadRequestException("奖励请求不存在");var request=rows.getFirst();
        if("FULFILLED".equals(request.get("status")))return Map.of("status","FULFILLED");
        if("PENDING".equals(request.get("status")))throw new ConflictException("奖励请求处理中，请等待回执");
        int attempt=((Number)request.get("attempts")).intValue()+1;
        jdbc.update("UPDATE learning_reward_request SET status='PENDING',attempts=?,last_error=NULL,updated_at=? WHERE request_id=?",attempt,LocalDateTime.now(clock),cmd.requestId());
        seasons.publishReward(cmd.requestId(),((Number)request.get("user_id")).longValue(),request.get("board_month").toString(),((Number)request.get("rule_id")).longValue(),((Number)request.get("coupon_id")).longValue(),attempt);
        return Map.of("status","PENDING");
    }
    public static void requireAdmin(Long role){if(!Long.valueOf(1).equals(role))throw new ForbiddenException("需要管理员权限");}
}

package com.tianji.learning.service.impl;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.vo.*;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.*;

@Service
public class SignRecordServiceImpl implements ISignRecordService,OperationHandler {
    private final JdbcTemplate jdbc;
    private final OutboxStore outbox;
    private final JsonMapper json;
    private final java.time.Clock clock;
    public SignRecordServiceImpl(JdbcTemplate jdbc,OutboxStore outbox,JsonMapper json,@org.springframework.beans.factory.annotation.Qualifier("learningClock") java.time.Clock clock){this.jdbc=jdbc;this.outbox=outbox;this.json=json;this.clock=clock;}
    public record Request(LocalDate date,java.time.LocalDateTime occurredAt) {public Request(LocalDate date){this(date,date.atStartOfDay());}}
    @Override public String kind() {return "SIGN_IN";}
    @Override public Object execute(String id,long user,String payload) {
        Request request=json.readValue(payload,Request.class);
        if(request.date()==null || request.date().isAfter(LocalDate.now(clock))) throw new BadRequestException("签到日期无效");
        return sign(user,request.date(),request.occurredAt()==null?request.date().atStartOfDay():request.occurredAt());
    }
    @Override @Transactional public SignResultVO addSignRecords() {var now=java.time.LocalDateTime.now(clock);return sign(UserContext.requireUser(),now.toLocalDate(),now);}
    private SignResultVO sign(long user,LocalDate date,java.time.LocalDateTime occurredAt) {
        int inserted=jdbc.update("INSERT IGNORE INTO sign_record(user_id,sign_day,sign_days,reward_points) VALUES(?,?,0,0)",user,date);
        if(inserted==1) {
            int days=streak(user,date);
            int reward=switch(days) {case 7 -> 10;case 14 -> 20;case 28 -> 40;default -> 0;};
            jdbc.update("UPDATE sign_record SET sign_days=?,reward_points=? WHERE user_id=? AND sign_day=?",days,reward,user,date);
            outbox.enqueue("sign:"+user+":"+date,MqConstants.Exchange.LEARNING_EXCHANGE,MqConstants.Key.SIGN_IN,SignInMessage.of(user,reward+1,occurredAt));
        }
        var row=jdbc.queryForMap("SELECT sign_days,reward_points FROM sign_record WHERE user_id=? AND sign_day=?",user,date);
        SignResultVO vo=new SignResultVO();vo.setSignDays(((Number)row.get("sign_days")).intValue());vo.setRewardPoints(((Number)row.get("reward_points")).intValue());return vo;
    }
    private int streak(long user,LocalDate date) {
        var dates=jdbc.query("SELECT sign_day FROM sign_record WHERE user_id=? AND sign_day<=? ORDER BY sign_day DESC",(rs,n)->rs.getDate(1).toLocalDate(),user,date);
        int count=0;LocalDate expected=date;
        for(LocalDate signed:dates) {if(!signed.equals(expected)) break;count++;expected=expected.minusDays(1);}
        return count;
    }
    @Override public SignRecordVO querySignRecords() {
        long user=UserContext.requireUser();LocalDate now=LocalDate.now(clock);
        Set<LocalDate> dates=new HashSet<>(jdbc.query("SELECT sign_day FROM sign_record WHERE user_id=? AND sign_day BETWEEN ? AND ?",(rs,n)->rs.getDate(1).toLocalDate(),user,now.withDayOfMonth(1),now));
        List<Byte> bits=new ArrayList<>();for(int day=1;day<=now.lengthOfMonth();day++) bits.add((byte)(dates.contains(now.withDayOfMonth(day))?1:0));
        SignRecordVO vo=new SignRecordVO();vo.setSignDays(streak(user,now));vo.setSignRecords(bits);vo.setMonth(now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));vo.setTodaySigned(dates.contains(now));vo.setDaysInMonth(now.lengthOfMonth());
        vo.setRewards(List.of(Map.of("days",7,"points",10),Map.of("days",14,"points",20),Map.of("days",28,"points",40)));return vo;
    }
}

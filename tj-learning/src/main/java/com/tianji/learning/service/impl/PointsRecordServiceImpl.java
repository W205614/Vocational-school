package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-07
 */
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.tianji.common.autoconfigure.reliability.InboxStore inbox;
    private final java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PointsRecordServiceImpl(org.springframework.jdbc.core.JdbcTemplate jdbc,com.tianji.common.autoconfigure.reliability.InboxStore inbox,@org.springframework.beans.factory.annotation.Qualifier("learningClock") java.time.Clock clock) {
        this.jdbc=jdbc;this.inbox=inbox;this.clock=clock;
    }
    public PointsRecordServiceImpl(org.springframework.jdbc.core.JdbcTemplate jdbc,com.tianji.common.autoconfigure.reliability.InboxStore inbox) {
        this(jdbc,inbox,java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
    }

    @Override
    public void addPointsRecord(Long userId,int points,PointsRecordType type,String sourceEventId) {
        award(userId,points,type,sourceEventId,LocalDateTime.now(clock));
    }

    public void award(Long userId,int points,PointsRecordType type,String sourceEventId,LocalDateTime occurredAt) {
        if(userId==null || type==null || points<=0) throw new com.tianji.common.exceptions.BadRequestException("积分事件无效");
        if(occurredAt==null || occurredAt.isAfter(LocalDateTime.now(clock).plusMinutes(1))) throw new com.tianji.common.exceptions.BadRequestException("积分事件时间无效");
        inbox.once("points."+type.getValue(),sourceEventId,() -> {
            java.time.LocalDate date=occurredAt.toLocalDate();
            String month=date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            LearningSeasonService.ensureSeason(jdbc,java.time.YearMonth.from(date));
            var season=jdbc.queryForMap("SELECT state FROM learning_season WHERE board_month=? FOR UPDATE",month);
            boolean late=java.util.Set.of("SETTLED","LEGACY_READONLY").contains(season.get("state"));
            jdbc.update("INSERT INTO points_daily_quota(user_id,type,quota_day,points) VALUES(?,?,?,0) ON DUPLICATE KEY UPDATE points=points",userId,type.getValue(),date);
            Integer used=jdbc.queryForObject("SELECT points FROM points_daily_quota WHERE user_id=? AND type=? AND quota_day=? FOR UPDATE",Integer.class,userId,type.getValue(),date);
            int awarded=type.getMaxPoints()==0?points:Math.max(0,Math.min(points,type.getMaxPoints()-used));
            if(awarded==0) return;
            jdbc.update("UPDATE points_daily_quota SET points=points+? WHERE user_id=? AND type=? AND quota_day=?",awarded,userId,type.getValue(),date);
            jdbc.update("INSERT INTO points_record(user_id,type,points,source_event_id,occurred_at,board_month,late) VALUES(?,?,?,?,?,?,?)",userId,type.getValue(),awarded,sourceEventId,occurredAt,month,late);
            if(late) {
                jdbc.update("INSERT IGNORE INTO learning_points_reconciliation(id,user_id,board_month,event_key,type,points,occurred_at) VALUES(?,?,?,?,?,?,?)",com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),userId,month,sourceEventId,type.getValue(),awarded,occurredAt);
                return;
            }
            jdbc.update("INSERT INTO points_projection(board_month,user_id,points,version,processed_version,reached_at) VALUES(?,?,?,1,0,?) ON DUPLICATE KEY UPDATE points=points+VALUES(points),version=version+1,reached_at=GREATEST(COALESCE(reached_at,VALUES(reached_at)),VALUES(reached_at))",month,userId,awarded,occurredAt);
        });
    }

    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        long user=UserContext.requireUser();
        var day=java.time.LocalDate.now(clock);
        var rows=jdbc.queryForList("SELECT type,SUM(points) AS points FROM points_record WHERE user_id=? AND occurred_at>=? AND occurred_at<? AND late=0 GROUP BY type ORDER BY type",user,day.atStartOfDay(),day.plusDays(1).atStartOfDay());
        List<PointsStatisticsVO> result=new ArrayList<>();
        for(var row:rows) {
            int code=((Number)row.get("type")).intValue();
            var type=PointsRecordType.of(code);var item=new PointsStatisticsVO();
            item.setTypeCode(code);item.setType(type==null?"其他积分":type.getDesc());
            item.setMaxPoints(type==null?0:type.getMaxPoints());item.setPoints(((Number)row.get("points")).intValue());result.add(item);
        }
        return result;
    }

    private int queryUserPointsByTypeAndDate(Long userId, PointsRecordType type, LocalDateTime begin, LocalDateTime end) {
        // 1. 查询条件
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .eq(type != null, PointsRecord::getType, type)
                .between(begin != null && end != null, PointsRecord::getCreateTime, begin, end);
        // 2. 调用mapper, 查询结果
        Integer points = getBaseMapper().queryUserPointsByTypeAndDate(wrapper);
        return points == null ? 0 : points;
    }
}

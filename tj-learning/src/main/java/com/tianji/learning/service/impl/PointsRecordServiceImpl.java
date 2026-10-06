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
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.tianji.common.autoconfigure.reliability.InboxStore inbox;

    @Override
    public void addPointsRecord(Long userId,int points,PointsRecordType type,String sourceEventId) {
        if(userId==null || type==null || points<=0) throw new com.tianji.common.exceptions.BadRequestException("积分事件无效");
        inbox.once("points."+type.getValue(),sourceEventId,() -> {
            java.time.LocalDate date=java.time.LocalDate.now();
            jdbc.update("INSERT INTO points_daily_quota(user_id,type,quota_day,points) VALUES(?,?,?,0) ON DUPLICATE KEY UPDATE points=points",userId,type.getValue(),date);
            Integer used=jdbc.queryForObject("SELECT points FROM points_daily_quota WHERE user_id=? AND type=? AND quota_day=? FOR UPDATE",Integer.class,userId,type.getValue(),date);
            int awarded=type.getMaxPoints()==0?points:Math.max(0,Math.min(points,type.getMaxPoints()-used));
            if(awarded==0) return;
            jdbc.update("UPDATE points_daily_quota SET points=points+? WHERE user_id=? AND type=? AND quota_day=?",awarded,userId,type.getValue(),date);
            jdbc.update("INSERT INTO points_record(user_id,type,points,source_event_id) VALUES(?,?,?,?)",userId,type.getValue(),awarded,sourceEventId);
            String month=date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            jdbc.update("INSERT INTO points_projection(board_month,user_id,points,version,processed_version) VALUES(?,?,?,1,0) ON DUPLICATE KEY UPDATE points=points+VALUES(points),version=version+1",month,userId,awarded);
        });
    }

    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        // 1. 获取用户
        Long userId = UserContext.getUser();
        // 2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        // 3. 构建查询条件
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .between(PointsRecord::getCreateTime, begin, end);
        // 4. 查询
        List<PointsRecord> list = getBaseMapper().queryUserPointsByDate(wrapper);
        if(CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 5. 封装返回
        List<PointsStatisticsVO> vos = new ArrayList<>(list.size());
        for (PointsRecord p : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc());
            vo.setMaxPoints(p.getType().getMaxPoints());
            vo.setPoints(p.getPoints());
            vos.add(vo);
        }
        return vos;
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

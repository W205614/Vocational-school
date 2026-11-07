package com.zhixuetang.learning.service;

import com.zhixuetang.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.learning.domain.vo.PointsStatisticsVO;
import com.zhixuetang.learning.enums.PointsRecordType;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-07
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(Long userId, int points, PointsRecordType type, String sourceEventId);

    List<PointsStatisticsVO> queryMyPointsToday();
}

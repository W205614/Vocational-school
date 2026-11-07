package com.zhixuetang.learning.service;

import com.zhixuetang.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.spring.service.IService;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-07
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    Integer querySeasonByTime(LocalDateTime time);
}

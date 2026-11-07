package com.zhixuetang.learning.service;

import com.zhixuetang.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.learning.domain.query.PointsBoardQuery;
import com.zhixuetang.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-07
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query);

    void createPointsBoardTableBySeason(Integer season);

    List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize);
}

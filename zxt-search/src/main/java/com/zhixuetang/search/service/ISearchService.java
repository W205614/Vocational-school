package com.zhixuetang.search.service;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.search.domain.query.CoursePageQuery;
import com.zhixuetang.search.domain.vo.CourseVO;

import java.util.List;

public interface ISearchService {

    List<CourseVO> queryCourseByCateId(Long cateLv2Id);

    List<CourseVO> queryBestTopN();

    List<CourseVO> queryNewTopN();

    List<CourseVO> queryFreeTopN();

    PageDTO<CourseVO> queryCoursesForPortal(CoursePageQuery query);

    List<Long> queryCoursesIdByName(String keyword);
}

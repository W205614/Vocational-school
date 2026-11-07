package com.zhixuetang.search.controller;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.search.domain.query.CoursePageQuery;
import com.zhixuetang.search.domain.vo.CourseVO;
import com.zhixuetang.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/public")
public class PublicCatalogController {
    private final ISearchService search;

    @GetMapping("/courses")
    public PageDTO<CourseVO> courses(CoursePageQuery query) {
        query.validate();
        return search.queryCoursesForPortal(query);
    }
}

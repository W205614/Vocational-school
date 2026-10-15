package com.tianji.search.controller;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.domain.vo.CourseVO;
import com.tianji.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/public")
public class PublicCatalogController {
 private final ISearchService search;
 @GetMapping("/courses") public PageDTO<CourseVO> courses(CoursePageQuery query) {
  query.validate();return search.queryCoursesForPortal(query);
 }
}

package com.tianji.course.controller;
import com.tianji.api.dto.course.CatalogueDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.course.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** Only published metadata; no media identifiers, signatures, or personal progress. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/public")
public class PublicCourseController {
 private final ICourseService courses;private final ICategoryService categories;private final ICourseCatalogueService catalogues;
 @GetMapping("/categories") public Object categories(){return categories.all(false);}
 @GetMapping("/courses/{id}") public Map<String,Object> course(@PathVariable long id) {
  var course=courses.getInfoById(id,false,false);
  if(course==null || course.getStatus()==null || !Set.of(2,4).contains(course.getStatus()))throw new BadRequestException("课程暂不可浏览");
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("id",course.getId());result.put("name",course.getName());result.put("coverUrl",course.getCoverUrl());
  result.put("free",course.getFree());result.put("price",course.getPrice());result.put("status",course.getStatus());
  result.put("introduce",course.getIntroduce());result.put("detail",course.getDetail());result.put("usePeople",course.getUsePeople());
  result.put("sectionNum",course.getSectionNum());result.put("purchaseEndTime",course.getPurchaseEndTime());result.put("validDuration",course.getValidDuration());
  result.put("chapters",catalogue(catalogues.queryPublicCourseCatalogues(id)));return result;
 }
 private List<Map<String,Object>> catalogue(List<CatalogueDTO> source) {
  if(source==null)return List.of();
  return source.stream().map(c->{Map<String,Object> item=new LinkedHashMap<>();item.put("id",c.getId());item.put("name",c.getName());item.put("index",c.getIndex());item.put("type",c.getType());item.put("mediaDuration",c.getMediaDuration());item.put("sections",catalogue(c.getSections()));return item;}).toList();
 }
}

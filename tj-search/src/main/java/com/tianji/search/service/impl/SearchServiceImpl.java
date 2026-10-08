package com.tianji.search.service.impl;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.*;
import com.tianji.search.config.InterestsProperties;
import com.tianji.search.constants.SearchErrorInfo;
import com.tianji.search.domain.po.Course;
import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.domain.vo.CourseVO;
import com.tianji.search.repository.CourseRepository;
import com.tianji.search.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements ISearchService {
 @Autowired private ElasticsearchClient restClient;
 @Autowired private IInterestsService interestsService;
 @Autowired private UserClient userClient;
 @Autowired private InterestsProperties interestsProperties;
 private static Map<String,Object> term(String field,Object value){return Map.of("term",Map.of(field,value));}
 private SearchResponse<Map> search(Map<String,Object> body) {
  try{return restClient.search(SearchRequest.of(b->b.index(CourseRepository.INDEX_NAME).withJson(new StringReader(JsonUtils.toJsonStr(body)))),Map.class);}
  catch(Exception error){throw new CommonException(SearchErrorInfo.QUERY_COURSE_ERROR,error);}
 }
 private Map<String,Object> available(List<Map<String,Object>> filters,Object must) {
  var clauses=new ArrayList<Map<String,Object>>();clauses.add(term("available",true));clauses.addAll(filters);
  Map<String,Object> bool=new LinkedHashMap<>();bool.put("filter",clauses);if(must!=null)bool.put("must",must);
  return Map.of("bool",bool);
 }
 @Override public List<CourseVO> queryCourseByCateId(Long id){return top(List.of(id),null,CourseRepository.PUBLISH_TIME,false,10);}
 @Override public List<CourseVO> queryBestTopN(){return recommend(false,CourseRepository.SOLD);}
 @Override public List<CourseVO> queryNewTopN(){return recommend(false,CourseRepository.PUBLISH_TIME);}
 @Override public List<CourseVO> queryFreeTopN(){return recommend(true,CourseRepository.SOLD);}
 private List<CourseVO> recommend(boolean free,String sort) {
  var categories=UserContext.getUser()==null?null:interestsService.queryMyInterestsIds();
  return top(categories,free,sort,false,interestsProperties.getTopNumber());
 }
 private List<CourseVO> top(List<Long> categories,Boolean free,String sort,boolean ascending,int count) {
  var filters=new ArrayList<Map<String,Object>>();if(free!=null)filters.add(term(CourseRepository.FREE,free));
  if(categories!=null && !categories.isEmpty())filters.add(Map.of("terms",Map.of(CourseRepository.CATEGORY_ID_LV2,categories)));
  var response=search(Map.of("query",available(filters,null),"size",count,"sort",List.of(Map.of(sort,ascending?"asc":"desc"))));
  var courses=response.hits().hits().stream().filter(hit->hit.source()!=null).map(hit->JsonUtils.toBean(JsonUtils.toJsonStr(hit.source()),Course.class)).toList();
  return withTeachers(courses);
 }
 private List<CourseVO> withTeachers(List<Course> courses) {
  if(courses.isEmpty())return List.of();
  Set<Long> ids=courses.stream().map(Course::getTeacher).filter(Objects::nonNull).filter(id->id!=0).collect(Collectors.toSet());
  Map<Long,String> names=ids.isEmpty()?Map.of():userClient.queryUserByIds(ids).stream().collect(Collectors.toMap(UserDTO::getId,UserDTO::getName,(a,b)->a));
  var result=new ArrayList<CourseVO>();for(var course:courses){var view=BeanUtils.toBean(course,CourseVO.class);view.setTeacher(course.getTeacher()==null?"未知":names.getOrDefault(course.getTeacher(),"未知"));result.add(view);}return result;
 }
 @Override public PageDTO<CourseVO> queryCoursesForPortal(CoursePageQuery query) {
  query.validate();query.validateSort(Set.of("price","sold","publishTime"));
  var filters=new ArrayList<Map<String,Object>>();
  if(query.getCategoryIdLv1()!=null)filters.add(term(CourseRepository.CATEGORY_ID_LV1,query.getCategoryIdLv1()));
  if(query.getCategoryIdLv2()!=null)filters.add(term(CourseRepository.CATEGORY_ID_LV2,query.getCategoryIdLv2()));
  if(query.getCategoryIdLv3()!=null)filters.add(term(CourseRepository.CATEGORY_ID_LV3,query.getCategoryIdLv3()));
  if(query.getFree()!=null)filters.add(term(CourseRepository.FREE,query.getFree()));
  if(query.getType()!=null)filters.add(term(CourseRepository.TYPE,query.getType()));
  if(query.getBeginTime()!=null || query.getEndTime()!=null) {
   Map<String,Object> range=new HashMap<>();if(query.getBeginTime()!=null)range.put("gte",query.getBeginTime().toString());if(query.getEndTime()!=null)range.put("lte",query.getEndTime().toString());
   filters.add(Map.of("range",Map.of(CourseRepository.UPDATE_TIME,range)));
  }
  Object must=query.getKeyword()==null || query.getKeyword().isBlank()?Map.of("match_all",Map.of()):Map.of("match_phrase",Map.of(CourseRepository.DEFAULT_QUERY_NAME,query.getKeyword()));
  Map<String,Object> body=new LinkedHashMap<>();body.put("query",available(filters,must));body.put("from",query.from());body.put("size",query.getPageSize());body.put("track_total_hits",true);
  body.put("highlight",Map.of("encoder","html","fields",Map.of(CourseRepository.DEFAULT_QUERY_NAME,Map.of())));
  body.put("_source",Map.of("excludes",CourseVO.EXCLUDE_FIELDS));
  if(query.getSortBy()!=null && !query.getSortBy().isBlank())body.put("sort",List.of(Map.of(query.getSortBy(),query.getIsAsc()?"asc":"desc")));
  var response=search(body);long total=response.hits().total()==null?0:response.hits().total().value();
  var courses=new ArrayList<Course>();
  for(var hit:response.hits().hits()) {
   if(hit.source()==null)continue;
   var course=JsonUtils.toBean(JsonUtils.toJsonStr(hit.source()),Course.class);
   var highlight=hit.highlight().get(CourseRepository.DEFAULT_QUERY_NAME);if(highlight!=null && !highlight.isEmpty())course.setName(String.join("",highlight));courses.add(course);
  }
  return new PageDTO<>(total,(total+query.getPageSize()-1)/query.getPageSize(),withTeachers(courses));
 }
 @Override public List<Long> queryCoursesIdByName(String keyword) {
  var response=search(Map.of("query",available(List.of(),Map.of("match_phrase",Map.of(CourseRepository.DEFAULT_QUERY_NAME,keyword))),"_source",List.of("id")));
  return response.hits().hits().stream().map(hit->Long.valueOf(hit.id())).toList();
 }
}

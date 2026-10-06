package com.tianji.search.mq;
import com.tianji.api.client.course.CourseClient;
import com.tianji.common.utils.BeanUtils;
import com.tianji.search.domain.po.Course;
import com.tianji.search.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import java.util.*;
@Component @RequiredArgsConstructor
public class MetadataProjectionWorker {
 private final JdbcTemplate jdbc;private final CourseClient courses;private final CourseRepository repository;
 private final ThreadPoolTaskExecutor metadataProjectionExecutor;
 @Configuration static class Pool {
  @Bean ThreadPoolTaskExecutor metadataProjectionExecutor(){var pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(2);pool.setMaxPoolSize(4);pool.setQueueCapacity(32);pool.setThreadNamePrefix("course-metadata-");pool.setWaitForTasksToCompleteOnShutdown(true);pool.setAwaitTerminationSeconds(20);return pool;}
 }
 @Scheduled(fixedDelay=1000) public void poll(){
  for(var row:jdbc.queryForList("SELECT * FROM course_metadata_projection WHERE status='PENDING' AND version>processed_version AND next_attempt_at<=NOW(3) AND (lease_until IS NULL OR lease_until<NOW(3)) ORDER BY next_attempt_at LIMIT 16")){
   String token=UUID.randomUUID().toString();
   if(jdbc.update("UPDATE course_metadata_projection SET lease_token=?,lease_until=NOW(3)+INTERVAL 30 SECOND WHERE course_id=? AND version>processed_version AND status='PENDING' AND (lease_until IS NULL OR lease_until<NOW(3))",token,row.get("course_id"))!=1)continue;
   try{metadataProjectionExecutor.execute(()->send(row,token));}catch(java.util.concurrent.RejectedExecutionException rejected){release(row,token,1);}
  }
 }
 private void send(Map<String,Object> row,String token){
  try {
   long id=((Number)row.get("course_id")).longValue(),version=((Number)row.get("version")).longValue();
   var source=courses.getSearchInfo(id);Course course=null;
   if(source!=null && source.getStatus()!=null && (source.getStatus()==2 || source.getStatus()==4)){course=BeanUtils.toBean(source,Course.class);course.setType(source.getCourseType());}
   repository.projectMetadata(id,course,version);
   jdbc.update("UPDATE course_metadata_projection SET processed_version=GREATEST(processed_version,?),attempts=0,last_error=NULL,lease_until=NULL,lease_token=NULL,next_attempt_at=NOW(3) WHERE course_id=? AND lease_token=?",version,id,token);
   // Metadata recreation must also republish the authoritative absolute sales count.
   jdbc.update("UPDATE course_sales_projection SET processed_version=0,next_attempt_at=NOW(3) WHERE course_id=?",id);
  }catch(Exception error){
   org.slf4j.LoggerFactory.getLogger(MetadataProjectionWorker.class).error("Metadata projection failed for course {} at version {}",row.get("course_id"),row.get("version"),error);
   int attempts=((Number)row.get("attempts")).intValue()+1;
   jdbc.update("UPDATE course_metadata_projection SET attempts=attempts+1,status=?,last_error=? WHERE course_id=? AND lease_token=? AND version=?",attempts>=10?"DEAD":"PENDING",error.getClass().getSimpleName(),row.get("course_id"),token,row.get("version"));
   release(row,token,Math.min(300,1<<Math.min(attempts,8)));
  }
 }
 private void release(Map<String,Object> row,String token,int seconds){jdbc.update("UPDATE course_metadata_projection SET lease_token=NULL,lease_until=NULL,next_attempt_at=TIMESTAMPADD(SECOND,?,NOW(3)) WHERE course_id=? AND lease_token=?",seconds,row.get("course_id"),token);}
}

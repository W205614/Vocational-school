package com.tianji.search.mq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tianji.search.repository.impl.CourseRepositoryImpl;
import java.util.*;
import static com.tianji.search.repository.CourseRepository.INDEX_NAME;
@Component @RequiredArgsConstructor @Slf4j
public class SalesProjectionWorker {
 private final JdbcTemplate jdbc;private final ElasticsearchClient es;
 private final ThreadPoolTaskExecutor salesProjectionExecutor;
 @Configuration static class Pool {
  @Bean ThreadPoolTaskExecutor salesProjectionExecutor() {
   var p=new ThreadPoolTaskExecutor();p.setCorePoolSize(2);p.setMaxPoolSize(4);p.setQueueCapacity(32);
   p.setThreadNamePrefix("sales-projection-");p.setWaitForTasksToCompleteOnShutdown(true);p.setAwaitTerminationSeconds(20);return p;
  }
 }
 @Scheduled(fixedDelayString="${tj.search.projection-interval-ms:1000}")
 public void publish() {
  for(var row:jdbc.queryForList("SELECT * FROM course_sales_projection WHERE status='PENDING' AND version>processed_version AND next_attempt_at<=NOW(3) AND (lease_until IS NULL OR lease_until<NOW(3)) ORDER BY course_id LIMIT 16")) {
   String token=UUID.randomUUID().toString();
   if(jdbc.update("UPDATE course_sales_projection SET lease_token=?,lease_until=DATE_ADD(NOW(3),INTERVAL 30 SECOND) WHERE course_id=? AND status='PENDING' AND version>processed_version AND (lease_until IS NULL OR lease_until<NOW(3))",token,row.get("course_id"))!=1)continue;
   try{salesProjectionExecutor.execute(()->send(row,token));}
   catch(java.util.concurrent.RejectedExecutionException e){release(row,token,1);}
  }
 }
 private void send(Map<String,Object> row,String token) {
  try {
   var script=CourseRepositoryImpl.script("if (ctx._source.salesVersion == null || ctx._source.salesVersion <= params.version) {ctx._source.sold=params.sold;ctx._source.salesVersion=params.version;} else {ctx.op='noop';}",Map.of("version",row.get("version"),"sold",row.get("sold")));
   es.update(u->u.index(INDEX_NAME).id(row.get("course_id").toString()).script(script).retryOnConflict(3),Map.class);
   jdbc.update("UPDATE course_sales_projection SET processed_version=GREATEST(processed_version,?),lease_token=NULL,lease_until=NULL,attempts=0,next_attempt_at=NOW(3),last_error=NULL WHERE course_id=? AND lease_token=?",row.get("version"),row.get("course_id"),token);
  }catch(Exception e){
   jdbc.update("UPDATE course_sales_projection SET attempts=attempts+1,status=?,last_error=? WHERE course_id=? AND lease_token=? AND version=?",((Number)row.get("attempts")).intValue()+1>=10?"DEAD":"PENDING",e.getClass().getSimpleName(),row.get("course_id"),token,row.get("version"));
   release(row,token,30);log.warn("销量投影失败，持久任务稍后重试: {}",e.getClass().getSimpleName());
  }
 }
 private void release(Map<String,Object> row,String token,int seconds){
  jdbc.update("UPDATE course_sales_projection SET lease_token=NULL,lease_until=NULL,next_attempt_at=TIMESTAMPADD(SECOND,?,NOW(3)) WHERE course_id=? AND lease_token=?",seconds,row.get("course_id"),token);
 }
}

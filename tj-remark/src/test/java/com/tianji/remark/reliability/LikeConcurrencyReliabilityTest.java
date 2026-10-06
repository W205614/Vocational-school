package com.tianji.remark.reliability;

import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.impl.LikedRecordServiceRedisImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LikeConcurrencyReliabilityTest {
 @Test void duplicateAndIndependentLikesSerializeWithoutDeadlock() throws Exception {
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:23316/acceptance_common?serverTimezone=Asia/Shanghai","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  var jdbc=new JdbcTemplate(source);
  jdbc.execute("CREATE TABLE IF NOT EXISTS liked_counter(biz_type VARCHAR(32) NOT NULL,biz_id BIGINT NOT NULL,liked_times INT NOT NULL,version BIGINT NOT NULL,PRIMARY KEY(biz_type,biz_id)) ENGINE=InnoDB");
  jdbc.execute("CREATE TABLE IF NOT EXISTS liked_record(user_id BIGINT NOT NULL,biz_type VARCHAR(32) NOT NULL,biz_id BIGINT NOT NULL,PRIMARY KEY(user_id,biz_type,biz_id)) ENGINE=InnoDB");
  var service=new LikedRecordServiceRedisImpl(jdbc,new OutboxStore(jdbc,JsonMapper.builder().build()));
  var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
  long biz=Math.abs(UUID.randomUUID().getMostSignificantBits()>>>1);
  var pool=Executors.newFixedThreadPool(10);
  try {
   run(pool,service,tx,biz,true,false);
   assertEquals(1,jdbc.queryForObject("SELECT liked_times FROM liked_counter WHERE biz_type='QA' AND biz_id=?",Integer.class,biz));
   assertEquals(1L,jdbc.queryForObject("SELECT version FROM liked_counter WHERE biz_type='QA' AND biz_id=?",Long.class,biz));
   run(pool,service,tx,biz,false,false);
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM liked_record WHERE biz_id=?",Integer.class,biz));
   run(pool,service,tx,biz,true,true);
   assertEquals(100,jdbc.queryForObject("SELECT liked_times FROM liked_counter WHERE biz_type='QA' AND biz_id=?",Integer.class,biz));
   assertEquals(102L,jdbc.queryForObject("SELECT version FROM liked_counter WHERE biz_type='QA' AND biz_id=?",Long.class,biz));
   assertEquals(102,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key LIKE ?",Integer.class,"like:QA:"+biz+":%"));
  } finally {
   pool.shutdownNow();
   jdbc.update("DELETE FROM liked_record WHERE biz_id=?",biz);
   jdbc.update("DELETE FROM liked_counter WHERE biz_id=?",biz);
   jdbc.update("DELETE FROM reliability_outbox WHERE business_key LIKE ?","like:QA:"+biz+":%");
  }
 }
 private void run(ExecutorService pool,LikedRecordServiceRedisImpl service,TransactionTemplate tx,long biz,boolean liked,boolean independent) throws Exception {
  var start=new CountDownLatch(1);var tasks=new ArrayList<Future<?>>();
  for(int i=0;i<100;i++){
   long user=independent?100000L+i:100000L;
   tasks.add(pool.submit(()->{
    start.await();UserContext.setUser(user);
    try{
     var form=new LikeRecordFormDTO();form.setBizId(biz);form.setBizType("QA");form.setLiked(liked);
     tx.executeWithoutResult(status->service.addLikeRecord(form));
    } finally {UserContext.removeUser();}
    return null;
   }));
  }
  start.countDown();for(var task:tasks)task.get(30,TimeUnit.SECONDS);
 }
}

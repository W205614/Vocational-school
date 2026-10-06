package com.tianji.search.reliability;
import com.tianji.search.repository.impl.CourseRepositoryImpl;
import com.tianji.search.domain.po.Course;
import com.tianji.common.exceptions.CommonException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.apache.http.HttpHost;
import org.elasticsearch.client.*;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_ES_URL",matches=".+")
class CourseRepositoryBulkTest {
 @Test void httpSuccessWithOneFailedBulkItemIsRejectedAndRepairedRetryKeepsSales() throws Exception {
  try(var client=new RestHighLevelClient(RestClient.builder(HttpHost.create(System.getenv("ACCEPTANCE_ES_URL"))))) {
   var repository=new CourseRepositoryImpl(client);
   long id=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
   var good=new Course();good.setId(id);good.setName("Bulk good");good.setCoverUrl("good");good.setSold(77);good.setPublishTime(java.time.LocalDateTime.of(2026,10,6,12,0));
   var bad=new Course();bad.setId(id+1);bad.setName("Bulk bad");bad.setCoverUrl("x".repeat(40000));
   try {
    assertThrows(CommonException.class,()->repository.saveAll(List.of(good,bad)));
    var existing=client.get(new GetRequest("course",Long.toString(id)),RequestOptions.DEFAULT);
    assertTrue(existing.isExists());
    assertEquals(0,((Number)existing.getSourceAsMap().get("sold")).intValue());
    bad.setCoverUrl("repaired");repository.saveAll(List.of(good,bad));repository.saveAll(List.of(good,bad));
    assertTrue(client.get(new GetRequest("course",Long.toString(id+1)),RequestOptions.DEFAULT).isExists());
    assertEquals(0,((Number)client.get(new GetRequest("course",Long.toString(id)),RequestOptions.DEFAULT).getSourceAsMap().get("sold")).intValue());
    repository.projectMetadata(id,good,2);
    repository.projectMetadata(id,null,3);
    repository.projectMetadata(id,good,2);
    assertEquals(false,client.get(new GetRequest("course",Long.toString(id)),RequestOptions.DEFAULT).getSourceAsMap().get("available"));
    repository.projectMetadata(id,good,4);
    assertEquals(true,client.get(new GetRequest("course",Long.toString(id)),RequestOptions.DEFAULT).getSourceAsMap().get("available"));
   } finally {client.delete(new DeleteRequest("course",Long.toString(id)),RequestOptions.DEFAULT);client.delete(new DeleteRequest("course",Long.toString(id+1)),RequestOptions.DEFAULT);}
  }
 }
}

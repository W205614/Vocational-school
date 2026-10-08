package com.tianji.search.reliability;
import com.tianji.search.repository.impl.CourseRepositoryImpl;
import com.tianji.search.domain.po.Course;
import com.tianji.search.config.SearchConnectionConfiguration;
import com.tianji.common.exceptions.CommonException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_ES_URL",matches=".+")
class CourseRepositoryBulkTest {
 @Test void partialBulkFailureRetryAndOutOfOrderVersionsPreserveSales()throws Exception {
  try(var transport=new SearchConnectionConfiguration().searchTransport(System.getenv("ACCEPTANCE_ES_URL"))) {
   var client=new ElasticsearchClient(transport);var repository=new CourseRepositoryImpl(client);
   long id=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
   var good=new Course();good.setId(id);good.setName("Bulk good");good.setCoverUrl("good");good.setSold(77);good.setPublishTime(java.time.LocalDateTime.of(2026,10,6,12,0));
   var bad=new Course();bad.setId(id+1);bad.setName("Bulk bad");bad.setCoverUrl("x".repeat(40000));
   try {
    assertThrows(CommonException.class,()->repository.saveAll(List.of(good,bad)));assertTrue(repository.findById(id).isPresent());assertEquals(0,repository.findById(id).orElseThrow().getSold());
    repository.updateById(id,"sold",17,"salesVersion",2);
    bad.setCoverUrl("repaired");repository.saveAll(List.of(good,bad));repository.saveAll(List.of(good,bad));
    assertTrue(repository.findById(id+1).isPresent());assertEquals(17,repository.findById(id).orElseThrow().getSold());
    repository.projectMetadata(id,good,2);repository.projectMetadata(id,null,3);repository.projectMetadata(id,good,2);
    assertEquals(false,client.get(g->g.index("course").id(Long.toString(id)),Map.class).source().get("available"));
    repository.projectMetadata(id,good,4);assertEquals(true,client.get(g->g.index("course").id(Long.toString(id)),Map.class).source().get("available"));
    repository.incrementSold(List.of(id,id+1),2);
    assertEquals(19,repository.findById(id).orElseThrow().getSold());assertEquals(2,repository.findById(id+1).orElseThrow().getSold());
    repository.increment(id,"sold",-2);assertEquals(17,repository.findById(id).orElseThrow().getSold());
    String sales="if(ctx._source.salesVersion==null || ctx._source.salesVersion<=params.version){ctx._source.sold=params.sold;ctx._source.salesVersion=params.version;}else{ctx.op='noop';}";
    client.update(u->u.index("course").id(Long.toString(id)).script(CourseRepositoryImpl.script(sales,Map.of("version",3,"sold",19))),Map.class);
    client.update(u->u.index("course").id(Long.toString(id)).script(CourseRepositoryImpl.script(sales,Map.of("version",2,"sold",99))),Map.class);
    assertEquals(19,repository.findById(id).orElseThrow().getSold());
   }finally{repository.deleteByIds(List.of(id,id+1));}
  }
 }
}

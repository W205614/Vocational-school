package com.tianji.search.repository.impl;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.*;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.json.JsonData;
import com.tianji.search.domain.po.Course;
import com.tianji.search.repository.CourseRepository;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.JsonUtils;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.tianji.search.constants.SearchErrorInfo.*;

@Component
public class CourseRepositoryImpl implements CourseRepository {
 private final ElasticsearchClient es;
 public CourseRepositoryImpl(ElasticsearchClient es){this.es=es;}
 private Map<String,Object> metadata(long id,Course course) {
  Map<String,Object> value=course==null?new HashMap<>():JsonUtils.toBean(JsonUtils.toJsonStr(course),Map.class);
  value.remove("sold");value.remove("salesVersion");
  if(course!=null && course.getPublishTime()!=null)value.put("publishTime",course.getPublishTime().toString());
  if(course!=null && course.getUpdateTime()!=null)value.put("updateTime",course.getUpdateTime().toString());
  value.put("id",Long.toString(id));value.put("available",course!=null);return value;
 }
 public static Script script(String source,Map<String,Object> parameters) {
  Map<String,JsonData> values=new HashMap<>();parameters.forEach((key,value)->values.put(key,JsonData.of(value)));
  return Script.of(s->s.lang("painless").source(v->v.scriptString(source)).params(values));
 }
 private Script metadataScript(long id,Course course,Long version) {
  String mutation="for(entry in params.metadata.entrySet()){ctx._source[entry.getKey()]=entry.getValue();} if(ctx._source.sold==null)ctx._source.sold=0;";
  if(version==null)return script(mutation,Map.of("metadata",metadata(id,course)));
  return script("if(ctx._source.metadataVersion==null || ctx._source.metadataVersion<=params.version){"+mutation+"ctx._source.metadataVersion=params.version;}else{ctx.op='noop';}",Map.of("metadata",metadata(id,course),"version",version));
 }
 private void metadataUpdate(long id,Course course,Long version) {
  try{es.update(u->u.index(INDEX_NAME).id(Long.toString(id)).script(metadataScript(id,course,version)).scriptedUpsert(true).upsert(Map.of()).retryOnConflict(3),Map.class);}
  catch(Exception error){throw new CommonException(SAVE_COURSE_ERROR,error);}
 }
 @Override public void projectMetadata(long id,Course course,long version){metadataUpdate(id,course,version);}
 @Override public void save(Course course){metadataUpdate(course.getId(),course,null);}
 @Override public void deleteById(Long id){try{es.delete(d->d.index(INDEX_NAME).id(id.toString()));}catch(Exception error){throw new CommonException(SAVE_COURSE_ERROR,error);}}
 @Override public Optional<Course> findById(Long id) {
  try{var result=es.get(g->g.index(INDEX_NAME).id(id.toString()),Map.class);return result.found() && result.source()!=null?Optional.of(JsonUtils.toBean(JsonUtils.toJsonStr(result.source()),Course.class)):Optional.empty();}
  catch(Exception error){throw new CommonException(QUERY_COURSE_ERROR,error);}
 }
 @Override public void updateById(Long id,Object... sources) {
  if(sources.length%2!=0)throw new IllegalArgumentException("Field/value pairs required");
  Map<String,Object> document=new HashMap<>();for(int i=0;i<sources.length;i+=2)document.put(sources[i].toString(),sources[i+1]);
  try{es.update(u->u.index(INDEX_NAME).id(id.toString()).doc(document).retryOnConflict(3),Map.class);}
  catch(Exception error){throw new CommonException(UPDATE_COURSE_STATUS_ERROR,error);}
 }
 @Override public void increment(Long id,String field,int amount) {
  if(!Set.of("sold","sections","duration").contains(field))throw new IllegalArgumentException("Unsupported increment field");
  try{es.update(u->u.index(INDEX_NAME).id(id.toString()).script(script("ctx._source[params.field]+=params.count",Map.of("field",field,"count",amount))).retryOnConflict(3),Map.class);}
  catch(Exception error){throw new CommonException(UPDATE_COURSE_STATUS_ERROR,error);}
 }
 @Override public void incrementSold(List<Long> ids,int amount) {
  var script=script("if(ctx._source.sold==null)ctx._source.sold=0;ctx._source.sold+=params.count;",Map.of(INCREMENT_SOLD_SCRIPT_PARAM,amount));
  var operations=ids.stream().map(id->BulkOperation.of(o->o.update(u->u.index(INDEX_NAME).id(id.toString()).retryOnConflict(3).action(a->a.script(script))))).toList();
  bulk(operations,UPDATE_COURSE_STATUS_ERROR);
 }
 @Override public void saveAll(List<Course> courses) {
  var operations=courses.stream().map(course->BulkOperation.of(o->o.update(u->u.index(INDEX_NAME).id(course.getId().toString()).retryOnConflict(3).action(a->a.script(metadataScript(course.getId(),course,null)).scriptedUpsert(true).upsert(Map.of()))))).toList();
  bulk(operations,SAVE_COURSE_ERROR);
 }
 @Override public void deleteByIds(List<Long> ids){bulk(ids.stream().map(id->BulkOperation.of(o->o.delete(d->d.index(INDEX_NAME).id(id.toString())))).toList(),SAVE_COURSE_ERROR);}
 private void bulk(List<BulkOperation> operations,String errorMessage) {
  if(operations.isEmpty())return;
  try {
   var response=es.bulk(b->b.operations(operations));
   var failed=response.items().stream().filter(item->item.error()!=null || item.status()>=400).map(item->item.id()+":"+(item.error()==null?item.status():item.error().reason())).toList();
   if(response.errors() || !failed.isEmpty())throw new CommonException("Elasticsearch bulk partially failed: "+failed);
  }catch(Exception error){throw new CommonException(errorMessage,error);}
 }
}

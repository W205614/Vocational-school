package com.tianji.search.repository.impl;

import com.tianji.search.domain.po.Course;
import com.tianji.search.repository.CourseRepository;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tianji.search.constants.SearchErrorInfo.*;

@Slf4j
@Component
public class CourseRepositoryImpl implements CourseRepository {

    private final RestHighLevelClient restHighLevelClient;

    public CourseRepositoryImpl(RestHighLevelClient restHighLevelClient) {
        this.restHighLevelClient = restHighLevelClient;
    }

    @Override
    public void projectMetadata(long courseId,Course course,long version) {
        Map<String,Object> metadata=course==null?new HashMap<>():JsonUtils.toBean(JsonUtils.toJsonStr(course),Map.class);
        metadata.remove("sold");metadata.remove("salesVersion");
        if(course!=null && course.getPublishTime()!=null) metadata.put("publishTime",course.getPublishTime().toString());
        metadata.put("id",Long.toString(courseId));metadata.put("available",course!=null);
        String source="if(ctx._source.metadataVersion==null || ctx._source.metadataVersion<=params.version) {for(entry in params.metadata.entrySet()){ctx._source[entry.getKey()]=entry.getValue();} ctx._source.metadataVersion=params.version; if(ctx._source.sold==null)ctx._source.sold=0;} else {ctx.op='noop';}";
        try {restHighLevelClient.update(new UpdateRequest(INDEX_NAME,Long.toString(courseId)).script(new Script(ScriptType.INLINE,"painless",source,Map.of("version",version,"metadata",metadata))).scriptedUpsert(true).upsert(Map.of()).retryOnConflict(3),RequestOptions.DEFAULT);}
        catch(Exception error){throw new CommonException(SAVE_COURSE_ERROR,error);}
    }

    @Override
    public void save(Course course) {
        UpdateRequest request = metadataUpdate(course);
        try {
            restHighLevelClient.update(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new CommonException(SAVE_COURSE_ERROR, e);
        }
    }

    @Override
    public void deleteById(Long courseId) {
        try {
            restHighLevelClient.delete(new DeleteRequest(INDEX_NAME, courseId.toString()), RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new CommonException(SAVE_COURSE_ERROR, e);
        }
    }

    @Override
    public Optional<Course> findById(Long courseId) {
        GetResponse response = null;
        try {
            response = restHighLevelClient.get(new GetRequest(INDEX_NAME, courseId.toString()), RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new CommonException(QUERY_COURSE_ERROR, e);
        }
        String source = response.getSourceAsString();
        if (StringUtils.isBlank(source)) {
            return Optional.empty();
        }
        return Optional.of(JsonUtils.toBean(source, Course.class));
    }

    @Override
    public void updateById(Long courseId, Object... sources) {
        // 1.创建Request
        UpdateRequest request = new UpdateRequest(INDEX_NAME, courseId.toString());
        // 2.更新字段
        request.doc(sources);
        // 3.发送请求
        try {
            restHighLevelClient.update(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new CommonException(UPDATE_COURSE_STATUS_ERROR, e);
        }
    }

    @Override
    public void increment(Long courseId, String field, int amount) {
        // 1.创建Request
        UpdateRequest request = new UpdateRequest(INDEX_NAME, courseId.toString());
        // 2.更新字段
        String code = "ctx._source." + field + " += params.count";
        Map<String, Object> params = new HashMap<>();
        params.put("count", amount);
        request.script(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, code, params));
        // 3.发送请求
        try {
            restHighLevelClient.update(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new CommonException(UPDATE_COURSE_STATUS_ERROR, e);
        }
    }

    @Override
    public void incrementSold(List<Long> courseIds, int amount) {
        // 1.bulk请求
        BulkRequest bulkRequest = new BulkRequest(INDEX_NAME);

        for (Long courseId : courseIds) {
            // 2.创建Request
            UpdateRequest request = new UpdateRequest(INDEX_NAME, courseId.toString());
            // 3.更新字段
            Map<String, Object> params = new HashMap<>();
            params.put(INCREMENT_SOLD_SCRIPT_PARAM, amount);
            request.script(new Script(ScriptType.STORED, null, INCREMENT_SOLD_SCRIPT_ID, params));
            bulkRequest.add(request);
        }

        // 4.发送请求
        try {
            checkBulk(restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT));
        } catch (Exception e) {
            throw new CommonException(UPDATE_COURSE_STATUS_ERROR, e);
        }
    }

    @Override
    public void saveAll(List<Course> list) {
        // 1.创建BulkRequest
        BulkRequest request = new BulkRequest(INDEX_NAME);
        // 2.添加参数
        for (Course course : list) {
            request.add(metadataUpdate(course));
        }
        // 3.批处理
        try {
            BulkResponse bulkResponse = restHighLevelClient.bulk(request, RequestOptions.DEFAULT);
            checkBulk(bulkResponse);
            for (BulkItemResponse itemResponse : bulkResponse.getItems()) {
                if (itemResponse.status().compareTo(RestStatus.BAD_REQUEST) >= 0) {
                    log.error("批处理失败，id:{}, 原因:{}", itemResponse.getId(), itemResponse.getFailureMessage());
                }
            }
        } catch (IOException e) {
            throw new CommonException(SAVE_COURSE_ERROR, e);
        }
    }

    @Override
    public void deleteByIds(List<Long> courseIds) {
        // 1.创建BulkRequest
        BulkRequest request = new BulkRequest(INDEX_NAME);
        // 2.添加参数
        for (Long courseId : courseIds) {
            request.add(new DeleteRequest(INDEX_NAME, courseId.toString()));
        }
        // 3.批处理
        try {
            BulkResponse bulkResponse = restHighLevelClient.bulk(request, RequestOptions.DEFAULT);
            checkBulk(bulkResponse);
            for (BulkItemResponse itemResponse : bulkResponse.getItems()) {
                if (itemResponse.status().compareTo(RestStatus.BAD_REQUEST) >= 0) {
                    log.error("批处理失败，id:{}, 原因:{}", itemResponse.getId(), itemResponse.getFailureMessage());
                }
            }
        } catch (IOException e) {
            throw new CommonException(SAVE_COURSE_ERROR, e);
        }
    }

    private UpdateRequest metadataUpdate(Course course){
        Map<String,Object> metadata=JsonUtils.toBean(JsonUtils.toJsonStr(course),Map.class);
        metadata.remove("sold");metadata.remove("salesVersion");
        if(course.getPublishTime()!=null) metadata.put("publishTime",course.getPublishTime().toString());
        metadata.put("available",true);
        String source="for (entry in params.metadata.entrySet()) {ctx._source[entry.getKey()]=entry.getValue();} if(ctx._source.sold==null)ctx._source.sold=0;";
        return new UpdateRequest(INDEX_NAME,course.getId().toString())
            .script(new Script(ScriptType.INLINE,"painless",source,Map.of("metadata",metadata)))
            .scriptedUpsert(true).upsert(Map.of()).retryOnConflict(3);
    }
    private void checkBulk(BulkResponse response) {
        if(response.hasFailures()) throw new CommonException("Elasticsearch bulk partially failed: "+response.buildFailureMessage());
    }
}

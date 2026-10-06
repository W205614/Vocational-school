package com.tianji.remark.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
@Service @RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper,LikedRecord> implements ILikedRecordService {
    private final JdbcTemplate jdbc;
    private final OutboxStore outbox;
    @Override @Transactional public void addLikeRecord(LikeRecordFormDTO form) {
        long user=UserContext.requireUser();
        if(form.getBizId()==null || form.getBizId()<=0 || form.getLiked()==null || form.getBizType()==null || !Set.of("QA","NOTE").contains(form.getBizType())) throw new BadRequestException("点赞对象无效");
        // A duplicate INSERT IGNORE takes a shared lock; upgrading it can deadlock.
        // Upsert acquires the exclusive counter lock before touching the relation.
        jdbc.update("INSERT INTO liked_counter(biz_type,biz_id,liked_times,version) VALUES(?,?,0,0) ON DUPLICATE KEY UPDATE biz_id=?",form.getBizType(),form.getBizId(),form.getBizId());
        int changed=form.getLiked()
                ?jdbc.update("INSERT IGNORE INTO liked_record(user_id,biz_type,biz_id) VALUES(?,?,?)",user,form.getBizType(),form.getBizId())
                :jdbc.update("DELETE FROM liked_record WHERE user_id=? AND biz_type=? AND biz_id=?",user,form.getBizType(),form.getBizId());
        if(changed==0) return;
        jdbc.update("UPDATE liked_counter SET liked_times=liked_times+?,version=version+1 WHERE biz_type=? AND biz_id=?",form.getLiked()?1:-1,form.getBizType(),form.getBizId());
        var counter=jdbc.queryForMap("SELECT liked_times,version FROM liked_counter WHERE biz_type=? AND biz_id=?",form.getBizType(),form.getBizId());
        long version=((Number)counter.get("version")).longValue();
        outbox.enqueue("like:"+form.getBizType()+":"+form.getBizId()+":"+version,LIKE_RECORD_EXCHANGE,form.getBizType()+".times.changed",
                List.of(LikeTimesDTO.of(form.getBizId(),((Number)counter.get("liked_times")).intValue(),version)));
    }
    @Override public Set<Long> isBizLiked(List<Long> ids) {
        if(ids==null || ids.isEmpty()) return Set.of();
        if(ids.size()>100) throw new BadRequestException("一次最多查询 100 个对象");
        return lambdaQuery().eq(LikedRecord::getUserId,UserContext.requireUser()).in(LikedRecord::getBizId,ids).list().stream().map(LikedRecord::getBizId).collect(java.util.stream.Collectors.toSet());
    }
    @Override public void readLikedTimesAndSendMessage(String bizType,int maxSize) {}
}

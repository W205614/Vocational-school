package com.tianji.learning.service.impl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import com.tianji.learning.constants.RedisConstants;
import java.util.List;
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsProjectionWorker {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private static final RedisScript<Long> PROJECT=RedisScript.of(
            "local v=tonumber(redis.call('HGET',KEYS[2],ARGV[1]) or '-1'); if v<=tonumber(ARGV[3]) then redis.call('ZADD',KEYS[1],ARGV[2],ARGV[1]); redis.call('HSET',KEYS[2],ARGV[1],ARGV[3]); return 1; end; return 0",Long.class);
    @Scheduled(fixedDelayString="${tj.learning.points-interval-ms:1000}")
    public void project() {
        try {
            String month=java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            if(!Boolean.TRUE.equals(redis.hasKey(RedisConstants.POINTS_BOARD_KEY_PREFIX+month+":versions")))
                jdbc.update("UPDATE points_projection SET processed_version=-1 WHERE board_month=? AND processed_version>=0",month);
        } catch(Exception unavailable) {log.warn("积分缓存不可用，读取仍由持久投影提供");return;}
        for(var row:jdbc.queryForList("SELECT * FROM points_projection WHERE version>processed_version ORDER BY board_month,user_id LIMIT 100")) {
            String key=RedisConstants.POINTS_BOARD_KEY_PREFIX+row.get("board_month");
            try {
                redis.execute(PROJECT,List.of(key,key+":versions"),row.get("user_id").toString(),row.get("points").toString(),row.get("version").toString());
                jdbc.update("UPDATE points_projection SET processed_version=? WHERE board_month=? AND user_id=? AND version=?",row.get("version"),row.get("board_month"),row.get("user_id"),row.get("version"));
            } catch(Exception e) { log.warn("积分投影未完成，将重试: {}",e.getClass().getSimpleName()); break; }
        }
    }
}

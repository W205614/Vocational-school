package com.tianji.auth.util;
import com.tianji.auth.common.domain.PrivilegeRoleDTO;import com.tianji.common.utils.JsonUtils;
import org.springframework.stereotype.Component;import org.springframework.data.redis.core.StringRedisTemplate;import org.springframework.data.redis.core.script.DefaultRedisScript;import java.util.*;
import static com.tianji.auth.common.constants.JwtConstants.*;
/** Hash mutation and publication version change atomically in one Redis slot. */
@Component
public class PrivilegeCache {
 private final StringRedisTemplate redis;
 private final com.tianji.common.autoconfigure.reliability.OutboxStore outbox;
 private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 private static final DefaultRedisScript<Long> WRITE=new DefaultRedisScript<>("""
  redis.call('DEL',KEYS[1])
  local changes=cjson.decode(ARGV[1])
  for field,value in pairs(changes) do redis.call('HSET',KEYS[1],field,value) end
  return redis.call('INCR',KEYS[2])
  """,Long.class);
 public PrivilegeCache(StringRedisTemplate redis,com.tianji.common.autoconfigure.reliability.OutboxStore outbox,org.springframework.jdbc.core.JdbcTemplate jdbc){this.redis=redis;this.outbox=outbox;this.jdbc=jdbc;}
 public void lockMutation(){jdbc.queryForObject("SELECT id FROM auth_cache_guard WHERE id=1 FOR UPDATE",Integer.class);}
 public void publishChange(){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Permission mutation must be transactional");
  outbox.enqueue("permissions:"+UUID.randomUUID(),"auth.permissions.exchange","auth.permissions.changed",Map.of());
 }
 /** Caller holds auth_cache_guard and reads committed database state. */
 public void publishCommittedSnapshot(List<PrivilegeRoleDTO> list){Map<String,String> map=new HashMap<>();for(var item:list)map.put(item.getId().toString(),JsonUtils.toJsonStr(item));redis.execute(WRITE,List.of(AUTH_PRIVILEGE_KEY,AUTH_PRIVILEGE_VERSION_KEY),JsonUtils.toJsonStr(map));}
}

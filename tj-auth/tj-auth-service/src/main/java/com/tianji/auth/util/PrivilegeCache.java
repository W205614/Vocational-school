package com.tianji.auth.util;
import com.tianji.auth.common.domain.PrivilegeRoleDTO;import com.tianji.auth.domain.po.Privilege;import com.tianji.common.utils.JsonUtils;
import org.springframework.stereotype.Component;import org.springframework.data.redis.core.StringRedisTemplate;import org.springframework.data.redis.core.script.DefaultRedisScript;import java.util.*;
import static com.tianji.auth.common.constants.JwtConstants.*;
/** Hash mutation and publication version change atomically in one Redis slot. */
@Component
public class PrivilegeCache {
 private final StringRedisTemplate redis;
 private final com.tianji.common.autoconfigure.reliability.OutboxStore outbox;
 private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 private static final DefaultRedisScript<Long> WRITE=new DefaultRedisScript<>("""
  local mode=ARGV[1]
  if mode=='FULL' then redis.call('DEL',KEYS[1]) end
  if mode=='FULL' or mode=='PUT' then
    local changes=cjson.decode(ARGV[2])
    for field,value in pairs(changes) do redis.call('HSET',KEYS[1],field,value) end
  elseif mode=='DELETE' then
    local ids=cjson.decode(ARGV[2])
    for _,id in ipairs(ids) do redis.call('HDEL',KEYS[1],id) end
  elseif mode=='ROLE' then
    local entries=redis.call('HGETALL',KEYS[1])
    for i=1,#entries,2 do
      local row=cjson.decode(entries[i+1])
      for n=#row.roles,1,-1 do if tostring(row.roles[n])==ARGV[2] then table.remove(row.roles,n) end end
      local encoded=cjson.encode(row);encoded=string.gsub(encoded,'"roles":{}','"roles":[]')
      redis.call('HSET',KEYS[1],entries[i],encoded)
    end
  end
  return redis.call('INCR',KEYS[2])
  """,Long.class);
 public PrivilegeCache(StringRedisTemplate redis,com.tianji.common.autoconfigure.reliability.OutboxStore outbox,org.springframework.jdbc.core.JdbcTemplate jdbc){this.redis=redis;this.outbox=outbox;this.jdbc=jdbc;}
 public void lockMutation(){jdbc.queryForObject("SELECT id FROM auth_cache_guard WHERE id=1 FOR UPDATE",Integer.class);}
 private void write(String mode,String data){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Permission mutation must be transactional");
  outbox.enqueue("permissions:"+UUID.randomUUID(),"auth.permissions.exchange","auth.permissions.changed",Map.of());
 }
 /** Caller holds auth_cache_guard and reads committed database state. */
 public void publishCommittedSnapshot(List<PrivilegeRoleDTO> list){Map<String,String> map=new HashMap<>();for(var item:list)map.put(item.getId().toString(),JsonUtils.toJsonStr(item));redis.execute(WRITE,List.of(AUTH_PRIVILEGE_KEY,AUTH_PRIVILEGE_VERSION_KEY),"FULL",JsonUtils.toJsonStr(map));}
 public void initPrivilegesCache(List<PrivilegeRoleDTO> list){Map<String,String> map=new HashMap<>();for(var item:list)map.put(item.getId().toString(),JsonUtils.toJsonStr(item));write("FULL",JsonUtils.toJsonStr(map));}
 public void cacheSinglePrivilege(Privilege privilege,Set<Long> roles){
  var value=new PrivilegeRoleDTO();value.setId(privilege.getId());value.setAntPath(privilege.getMethod()+":"+privilege.getUri());value.setRoles(roles);value.setInternal(privilege.getInternal());
  write("PUT",JsonUtils.toJsonStr(Map.of(privilege.getId().toString(),JsonUtils.toJsonStr(value))));
 }
 public void removePrivilegeCacheById(Long id){removePrivilegeCacheByIds(List.of(id));}
 public void removePrivilegeCacheByIds(List<Long> ids){write("DELETE",JsonUtils.toJsonStr(ids.stream().map(Object::toString).toList()));}
 public void removeCacheByRoleId(Long id){write("ROLE",id.toString());}
}

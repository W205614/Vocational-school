package com.tianji.pay.service.impl;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;import java.util.function.Supplier;
@Service @RequiredArgsConstructor
public class ProviderRequestGuard {
 private final JdbcTemplate jdbc;
 @Transactional public <T>T prepare(String kind,long business,Supplier<T> request){
  jdbc.update("INSERT INTO provider_request_guard(kind,business_id) VALUES(?,?) ON DUPLICATE KEY UPDATE business_id=VALUES(business_id)",kind,business);
  return request.get();
 }
}

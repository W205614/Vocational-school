package com.tianji.common.utils;
import java.time.LocalDateTime;
public final class JdbcTime {
 private JdbcTime(){}
 public static LocalDateTime localDateTime(Object value){
  if(value instanceof LocalDateTime time)return time;
  if(value instanceof java.sql.Timestamp stamp)return stamp.toLocalDateTime();
  if(value==null)throw new IllegalArgumentException("Missing timestamp");
  return LocalDateTime.parse(value.toString().replace(' ','T'));
 }
}

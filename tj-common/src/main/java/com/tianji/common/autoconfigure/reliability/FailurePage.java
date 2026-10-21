package com.tianji.common.autoconfigure.reliability;

import com.tianji.common.exceptions.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Bounded administrator queries; identifiers are exact matches, never SQL fragments. */
public record FailurePage(List<Map<String,Object>> list,long total,int pageNo,int pageSize) {
    static FailurePage query(JdbcTemplate jdbc,String columns,String from,String where,String order,
                             int pageNo,int pageSize,List<Object> values) {
        if(pageNo<1 || pageNo>10000 || pageSize<1 || pageSize>100)
            throw new BadRequestException("分页范围无效");
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) "+from+where,Long.class,values.toArray()));
        var parameters=new ArrayList<>(values);parameters.add(pageSize);parameters.add((pageNo-1)*pageSize);
        return new FailurePage(jdbc.queryForList("SELECT "+columns+" "+from+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",parameters.toArray()),total,pageNo,pageSize);
    }
    static String identifier(String value) {
        if(value==null || value.isBlank())return null;
        value=value.trim();
        if(value.length()>190)throw new BadRequestException("标识长度不能超过190个字符");
        return value;
    }
}

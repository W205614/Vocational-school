package com.tianji.learning.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.tianji.learning.utils.TableInfoContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MyBatisConfiguration {

    @Bean
    public DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor() {
        Map<String, TableNameHandler> map = new HashMap<>(1);
        map.put("points_board", ((sql, tableName) -> TableInfoContext.getInfo()));
        return new DynamicTableNameInnerInterceptor((sql,tableName) -> {
            if(!"points_board".equals(tableName)) return tableName;
            String name=TableInfoContext.getInfo();
            if(name==null || !name.matches("points_board_[0-9]+")) throw new IllegalArgumentException("Invalid points board table");
            return name;
        });
    }
}

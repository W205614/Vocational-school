package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import com.tianji.common.autoconfigure.mybatis.MyBatisAutoFillInterceptor;
import com.tianji.common.autoconfigure.mybatis.MybatisConfig;
import com.tianji.learning.config.MyBatisConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class MybatisPaginationConfigurationTest {
    static ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                MybatisConfig.class,MybatisPlusInnerInterceptorAutoConfiguration.class,MybatisPlusAutoConfiguration.class))
                .withBean(DataSource.class,()->new DriverManagerDataSource("jdbc:mysql://127.0.0.1:1/not_used","not_used","not_used"))
                .withPropertyValues("mybatis-plus.mapper-locations=classpath:/pagination-test/no-mappers/*.xml");
    }
    @Test void dynamicTableAutoConfigurationCannotReplacePaginationAndAutofill() {
        runner().withUserConfiguration(MyBatisConfiguration.class).run(context->{
            assertNull(context.getStartupFailure());
            var plugin=context.getBean(MybatisPlusInterceptor.class);
            assertEquals(1,plugin.getInterceptors().stream().filter(PaginationInnerInterceptor.class::isInstance).count());
            assertEquals(1,plugin.getInterceptors().stream().filter(MyBatisAutoFillInterceptor.class::isInstance).count());
            assertEquals(1,plugin.getInterceptors().stream().filter(DynamicTableNameInnerInterceptor.class::isInstance).count());
            assertEquals(100L,plugin.getInterceptors().stream().filter(PaginationInnerInterceptor.class::isInstance).map(PaginationInnerInterceptor.class::cast).findFirst().orElseThrow().getMaxLimit());
            assertTrue(context.getBean(SqlSessionFactory.class).getConfiguration().getInterceptors().contains(plugin));
        });
    }
    @Test void modulesWithoutDynamicTablesStillInstallBoundedPagination() {
        runner().run(context->{
            assertNull(context.getStartupFailure());
            var plugin=context.getBean(MybatisPlusInterceptor.class);
            assertEquals(1,plugin.getInterceptors().stream().filter(PaginationInnerInterceptor.class::isInstance).count());
            assertEquals(1,plugin.getInterceptors().stream().filter(MyBatisAutoFillInterceptor.class::isInstance).count());
            assertTrue(context.getBean(SqlSessionFactory.class).getConfiguration().getInterceptors().contains(plugin));
        });
    }
    @Test void anExplicitModuleInterceptorRetainsItsOverride() {
        var explicit=new MybatisPlusInterceptor();
        runner().withBean("explicitInterceptor",MybatisPlusInterceptor.class,()->explicit).run(context->{
            assertNull(context.getStartupFailure());
            assertEquals(1,context.getBeansOfType(MybatisPlusInterceptor.class).size());
            assertSame(explicit,context.getBean(MybatisPlusInterceptor.class));
        });
    }
}

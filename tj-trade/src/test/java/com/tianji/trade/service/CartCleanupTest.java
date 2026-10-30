package com.tianji.trade.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tianji.api.client.course.CourseClient;
import com.tianji.trade.config.TradeProperties;
import com.tianji.trade.mapper.CartMapper;
import com.tianji.trade.service.impl.CartServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartCleanupTest {
    @BeforeAll static void mapping(){
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"cart-cleanup-test"),com.tianji.trade.domain.po.Cart.class);
    }
    private final CartMapper mapper=mock(CartMapper.class);
    private final CartServiceImpl carts=new CartServiceImpl(mock(CourseClient.class),new TradeProperties(),mock(org.springframework.jdbc.core.JdbcTemplate.class),mock(org.springframework.transaction.PlatformTransactionManager.class));
    @Test void storageFailurePropagatesToTheOrderTransaction(){
        ReflectionTestUtils.setField(carts,"baseMapper",mapper);
        var failure=new DataAccessResourceFailureException("fixture unavailable");doThrow(failure).when(mapper).delete(any(Wrapper.class));
        assertSame(failure,assertThrows(DataAccessResourceFailureException.class,()->carts.deleteCartByUserAndCourseIds(7L,List.of(42L))));
    }
    @Test void noMatchingCartIsANormalSuccessfulCleanup(){
        ReflectionTestUtils.setField(carts,"baseMapper",mapper);
        assertDoesNotThrow(()->carts.deleteCartByUserAndCourseIds(7L,List.of(42L)));verify(mapper).delete(any(Wrapper.class));
    }
}

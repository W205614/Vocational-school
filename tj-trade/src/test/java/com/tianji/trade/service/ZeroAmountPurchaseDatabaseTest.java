package com.tianji.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.constants.CourseStatus;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.common.autoconfigure.mybatis.MybatisConfig;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.utils.UserContext;
import com.tianji.pay.sdk.client.PayClient;
import com.tianji.trade.config.TradeProperties;
import com.tianji.trade.domain.dto.*;
import com.tianji.trade.mapper.*;
import com.tianji.trade.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class ZeroAmountPurchaseDatabaseTest {
    JdbcTemplate jdbc;TransactionTemplate tx;OrderServiceImpl orders;RefundApplyServiceImpl refunds;
    ZeroAmountSettlementService zero;PayClient provider;long order,course;PlaceOrderDTO request;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_trade?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(OrderMapper.class);configuration.addMapper(OrderDetailMapper.class);configuration.addMapper(RefundApplyMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(configuration);
        factory.setPlugins(new MybatisConfig().mybatisPlusInterceptor(null));
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*Mapper.xml"));factory.afterPropertiesSet();
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        var json=JsonMapper.builder().build();var outbox=new OutboxStore(jdbc,json);zero=new ZeroAmountSettlementService(jdbc,outbox);
        var details=mock(OrderDetailServiceImpl.class,CALLS_REAL_METHODS);ReflectionTestUtils.setField(details,"baseMapper",session.getMapper(OrderDetailMapper.class));
        var metadata=mock(CourseClient.class);var promotions=mock(PromotionClient.class);
        order=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();course=order+1;
        var info=new CourseSimpleInfoDTO();info.setId(course);info.setPrice(1000);info.setStatus(CourseStatus.SHELF.getValue());info.setName("zero balance fixture");info.setCoverUrl("/fixture.png");info.setValidDuration(3);info.setThirdCateId(1L);
        when(metadata.getSimpleInfoList(List.of(course))).thenReturn(List.of(info));
        when(promotions.queryDiscountDetailByOrder(any())).thenReturn(new CouponDiscountDTO().setIds(List.of(91L)).setDiscountAmount(1000).setDiscountDetail(Map.of(course,1000)));
        orders=mock(OrderServiceImpl.class,CALLS_REAL_METHODS);
        for(var entry:Map.<String,Object>of("baseMapper",session.getMapper(OrderMapper.class),"jdbc",jdbc,"transactionManager",manager,"json",json,"zeroAmountSettlement",zero,"courseClient",metadata,"promotionClient",promotions,"detailService",details,"cartService",mock(ICartService.class),"tradeProperties",new TradeProperties()).entrySet())ReflectionTestUtils.setField(orders,entry.getKey(),entry.getValue());
        refunds=mock(RefundApplyServiceImpl.class,CALLS_REAL_METHODS);provider=mock(PayClient.class);
        ReflectionTestUtils.setField(refunds,"baseMapper",session.getMapper(RefundApplyMapper.class));ReflectionTestUtils.setField(refunds,"orderMapper",session.getMapper(OrderMapper.class));
        ReflectionTestUtils.setField(refunds,"detailService",details);ReflectionTestUtils.setField(refunds,"jdbc",jdbc);ReflectionTestUtils.setField(refunds,"outbox",outbox);ReflectionTestUtils.setField(refunds,"payClient",provider);
        var beans=new StaticListableBeanFactory();beans.addBean("refunds",refunds);ReflectionTestUtils.setField(refunds,"self",beans.getBeanProvider(IRefundApplyService.class));
        request=new PlaceOrderDTO();request.setOrderId(order);request.setCourseIds(List.of(course));request.setCouponIds(List.of(91L));UserContext.setUser(7L);
    }
    @AfterEach void cleanup() {
        UserContext.removeUser();
        jdbc.update("DELETE FROM reliability_outbox WHERE business_key IN(?,?)","order:"+order+":paid","order:"+order+":coupon-release");
        jdbc.update("DELETE FROM reliability_outbox WHERE business_key IN(SELECT CONCAT('refund:',id,':success') FROM refund_apply WHERE order_id=?)",order);
        jdbc.update("DELETE FROM refund_delivery_task WHERE refund_id IN(SELECT id FROM refund_apply WHERE order_id=?)",order);
        for(String table:List.of("refund_apply","order_detail","order_creation"))jdbc.update("DELETE FROM "+table+" WHERE order_id=?",order);
        jdbc.update("DELETE FROM `order` WHERE id=?",order);
    }
    int events(String key) {return jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key=?",Integer.class,key);}
    @Test void cartCleanupFailureRollsBackOrderDetailsAndSettlementEvent(){
        var cart=(com.tianji.trade.service.ICartService)ReflectionTestUtils.getField(orders,"cartService");
        var failure=new org.springframework.dao.DataAccessResourceFailureException("fixture cart unavailable");
        doThrow(failure).when(cart).deleteCartByUserAndCourseIds(7L,List.of(course));
        assertSame(failure,assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,()->orders.placeOrder(request)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM `order` WHERE id=?",Integer.class,order));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_detail WHERE order_id=?",Integer.class,order));
        assertEquals(0,events("order:"+order+":paid"));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM order_creation WHERE order_id=?",String.class,order));
    }
    @Test void fullDiscountCreatesPaidOrderAndOneSharedCouponAndEntitlementEvent() {
        var result=orders.placeOrder(request);assertEquals(2,result.getStatus());assertEquals(0,result.getPayAmount());
        assertEquals(2,orders.placeOrder(request).getStatus());
        var stored=jdbc.queryForMap("SELECT real_amount,status,pay_channel,pay_order_no,pay_time FROM `order` WHERE id=?",order);
        assertEquals("ZERO",stored.get("pay_channel"));assertNull(stored.get("pay_order_no"));assertNotNull(stored.get("pay_time"));
        assertEquals(1,events("order:"+order+":paid"));
        var detail=jdbc.queryForMap("SELECT status,pay_channel,course_expire_time FROM order_detail WHERE order_id=?",order);
        assertEquals(2,((Number)detail.get("status")).intValue());assertEquals("ZERO",detail.get("pay_channel"));assertNotNull(detail.get("course_expire_time"));
        var payload=JsonMapper.builder().build().readTree(jdbc.queryForObject("SELECT payload FROM reliability_outbox WHERE business_key=?",String.class,"order:"+order+":paid"));
        assertEquals("order.pay",payload.get("eventType").asText());assertEquals(order,payload.get("payload").get("orderId").asLong());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM payment_fact WHERE order_id=?",Integer.class,order));
    }
    @Test void zeroSettlementAndOutboxRollBackTogetherAndRetryIsSafe() {
        orders.placeOrder(request);
        jdbc.update("DELETE FROM reliability_outbox WHERE business_key=?","order:"+order+":paid");
        jdbc.update("UPDATE `order` SET status=1,pay_channel='',pay_time=NULL WHERE id=?",order);
        jdbc.update("UPDATE order_detail SET status=1,pay_channel='',course_expire_time=NULL WHERE order_id=?",order);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{zero.settle(order);throw new IllegalStateException("crash before commit");}));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM `order` WHERE id=?",Integer.class,order));assertEquals(0,events("order:"+order+":paid"));
        tx.executeWithoutResult(s->zero.settle(order));tx.executeWithoutResult(s->zero.settle(order));assertEquals(1,events("order:"+order+":paid"));
    }
    @Test void approvedZeroRefundFinishesLocallyAndRevokesThroughExistingEvent() {
        orders.placeOrder(request);long detail=jdbc.queryForObject("SELECT id FROM order_detail WHERE order_id=?",Long.class,order);
        var form=new RefundFormDTO();form.setOrderDetailId(detail);form.setRefundReason("zero amount withdrawal");
        tx.executeWithoutResult(s->refunds.applyRefund(form));
        long apply=jdbc.queryForObject("SELECT id FROM refund_apply WHERE order_id=?",Long.class,order);
        assertEquals(1,jdbc.queryForObject("SELECT status FROM refund_apply WHERE id=?",Integer.class,apply));
        UserContext.setRole(1L);var approval=new ApproveFormDTO();approval.setId(apply);approval.setApproveType(1);
        tx.executeWithoutResult(s->refunds.approveRefundApply(approval));tx.executeWithoutResult(s->refunds.approveRefundApply(approval));
        assertEquals(5,jdbc.queryForObject("SELECT status FROM refund_apply WHERE id=?",Integer.class,apply));
        assertEquals("ZERO",jdbc.queryForObject("SELECT refund_channel FROM refund_apply WHERE id=?",String.class,apply));
        assertEquals(7,jdbc.queryForObject("SELECT status FROM order_detail WHERE id=?",Integer.class,detail));
        assertEquals(7,jdbc.queryForObject("SELECT status FROM `order` WHERE id=?",Integer.class,order));
        assertEquals(1,events("refund:"+apply+":success"));verifyNoInteractions(provider);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM refund_delivery_task WHERE refund_id=?",Integer.class,apply));
    }
}

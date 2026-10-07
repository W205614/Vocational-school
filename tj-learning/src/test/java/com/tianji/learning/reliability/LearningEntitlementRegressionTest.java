package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.learning.service.impl.LearningEntitlementService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningEntitlementRegressionTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private LearningEntitlementService service;
    private long user, course;
    private final LocalDateTime purchased = LocalDateTime.now().withNano(0);

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316") + "/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true", "root", System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new LearningEntitlementService(jdbc);
        user = IdWorker.getId(); course = user + 1;
    }

    private OrderBasicDTO order(long detail) {
        var order = new OrderBasicDTO();
        order.setOrderId(detail + 1); order.setUserId(user); order.setCourseIds(List.of(course));
        order.setDetailIds(Map.of(course, detail)); order.setFinishTime(purchased);
        return order;
    }
    private void grant(long detail, Integer duration) {
        var info = new CourseSimpleInfoDTO(); info.setId(course); info.setValidDuration(duration);
        transactions.executeWithoutResult(status -> service.grant(order(detail), List.of(info)));
    }
    private void revoke(long detail) { transactions.executeWithoutResult(status -> service.revoke(order(detail))); }
    private LocalDateTime expiry() { return jdbc.queryForObject("SELECT expire_time FROM learning_lesson WHERE user_id=? AND course_id=?", LocalDateTime.class, user, course); }

    @Test void refundOfLongerEntitlementShrinksLessonToRemainingExpiry() {
        grant(user + 10, 1); grant(user + 20, 2); revoke(user + 20);
        assertEquals(purchased.plusMonths(1), expiry());
    }
    @Test void refundOfPermanentEntitlementRestoresFiniteExpiry() {
        grant(user + 10, null); grant(user + 20, 1); revoke(user + 10);
        assertEquals(purchased.plusMonths(1), expiry());
    }
    @Test void revokeBeforeGrantCannotResurrectEntitlement() {
        revoke(user + 10); grant(user + 10, 1);
        assertEquals(0, jdbc.queryForObject("SELECT active FROM learning_entitlement WHERE order_detail_id=?", Integer.class, user + 10));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learning_lesson WHERE user_id=? AND course_id=? AND status<>3", Integer.class, user, course));
    }
}

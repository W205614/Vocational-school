package com.tianji.promotion.service.impl;

import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Production table precision and real JDBC roundtrips at a deterministic .900s boundary. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class CouponIssuePrecisionDatabaseTest {
    private static DriverManagerDataSource source;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime AT=LocalDateTime.of(2030,1,2,12,34,56,900_000_000);
    private static final Clock CLOCK=Clock.fixed(AT.atZone(ZONE).toInstant(),ZONE);
    private JdbcTemplate jdbc;private long couponId;
    private CouponServiceImpl issues;private CouponMapper coupons;private UserCouponMapper tickets;
    private StringRedisTemplate redis;

    @BeforeAll static void schema()throws Exception {
        String server="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
        var admin=new JdbcTemplate(new DriverManagerDataSource(server,"root",System.getenv("ACCEPTANCE_DB_PASSWORD")));
        admin.execute("CREATE DATABASE IF NOT EXISTS acceptance_coupon_issue_precision");
        source=new DriverManagerDataSource(server+"acceptance_coupon_issue_precision?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        Path repo=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(repo!=null&&!Files.exists(repo.resolve("deploy/compact/schema.sql")))repo=repo.getParent();assertNotNull(repo);
        String schema=Files.readString(repo.resolve("deploy/compact/schema.sql"));
        for(String table:List.of("coupon","user_coupon")){
            var match=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(schema);assertTrue(match.find());
            new ResourceDatabasePopulator(new ByteArrayResource(match.group().replaceFirst("CREATE TABLE","CREATE TABLE IF NOT EXISTS").getBytes(StandardCharsets.UTF_8))).execute(source);
        }
    }
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(source);couponId=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("INSERT INTO coupon(id,discount_type,obtain_way,status,total_num,user_limit,creater,updater) VALUES(?,1,1,1,2,1,1,1)",couponId);
        redis=mock(StringRedisTemplate.class,RETURNS_DEEP_STUBS);
        issues=spy(new CouponServiceImpl(null,null,null,null,redis,CLOCK));
        doAnswer(call->read()).when(issues).getById(any());
        doAnswer(call->{Coupon c=call.getArgument(0);return jdbc.update("UPDATE coupon SET status=?,issue_begin_time=?,issue_end_time=?,term_days=?,term_begin_time=?,term_end_time=? WHERE id=?",c.getStatus().getValue(),c.getIssueBeginTime(),c.getIssueEndTime(),c.getTermDays(),c.getTermBeginTime(),c.getTermEndTime(),c.getId())==1;}).when(issues).updateById(any(Coupon.class));
        coupons=mock(CouponMapper.class);tickets=mock(UserCouponMapper.class);
        when(coupons.selectOne(any())).thenAnswer(call->read());
        when(coupons.incrIssueNum(couponId)).thenAnswer(call->jdbc.update("UPDATE coupon SET issue_num=issue_num+1 WHERE id=? AND issue_num<total_num",couponId));
        doAnswer(call->{UserCoupon c=call.getArgument(0);return jdbc.update("INSERT INTO user_coupon(id,user_id,coupon_id,term_begin_time,term_end_time,status) VALUES(?,?,?,?,?,?)",c.getId(),c.getUserId(),c.getCouponId(),c.getTermBeginTime(),c.getTermEndTime(),c.getStatus().getValue());}).when(tickets).insert(any(UserCoupon.class));
    }
    private Coupon read(){return jdbc.queryForObject("SELECT * FROM coupon WHERE id=?",(rs,n)->new Coupon().setId(rs.getLong("id")).setStatus(CouponStatus.of(rs.getInt("status"))).setObtainWay(ObtainType.PUBLIC).setTotalNum(2).setUserLimit(1).setIssueBeginTime(time(rs.getTimestamp("issue_begin_time"))).setIssueEndTime(time(rs.getTimestamp("issue_end_time"))).setTermDays(rs.getInt("term_days")).setTermBeginTime(time(rs.getTimestamp("term_begin_time"))).setTermEndTime(time(rs.getTimestamp("term_end_time"))),couponId);}
    private static LocalDateTime time(java.sql.Timestamp value){return value==null?null:value.toLocalDateTime();}
    private CouponIssueFormDTO form(){var dto=new CouponIssueFormDTO();dto.setId(couponId);dto.setIssueEndTime(AT.plusDays(1).withNano(0));dto.setTermDays(30);return dto;}
    private Object claim(){
        var json=JsonMapper.builder().build();var claims=new CouponClaimService(coupons,tickets,jdbc,json,CLOCK);
        return new TransactionTemplate(new DataSourceTransactionManager(source)).execute(tx->claims.execute("precision-"+couponId,couponId+1,json.writeValueAsString(new CouponClaimService.Request(couponId,null))));
    }
    @Test void jdbcReproducesTheDatabaseForwardRoundingBoundary(){
        jdbc.update("UPDATE coupon SET issue_begin_time=?,issue_end_time=?,status=3,term_days=30 WHERE id=?",AT,AT.plusDays(1).withNano(0),couponId);
        assertEquals(AT.withNano(0).plusSeconds(1),read().getIssueBeginTime());
        assertTrue(read().getIssueBeginTime().isAfter(AT));
        var rejected=assertThrows(com.tianji.common.exceptions.BizIllegalException.class,this::claim);
        assertEquals("不在优惠券领取时间内",rejected.getMessage());
    }
    @Test void immediateIssueHasOneDatabaseAndCacheStartAndCanBeClaimedAtOnce(){
        issues.beginIssue(form());
        assertEquals(AT.withNano(0),read().getIssueBeginTime());
        verify(redis.opsForHash()).putAll(anyString(),argThat(values->Objects.equals(values.get("issueBeginTime"),String.valueOf(AT.withNano(0).atZone(ZONE).toInstant().toEpochMilli()))));
        assertNotNull(claim());
    }
    @Test void newRelativeClaimIsImmediatelyUsableForItsCompleteDayPeriod(){
        var dto=form();issues.beginIssue(dto);
        // Isolate claim validity from the separate issue-start regression.
        jdbc.update("UPDATE coupon SET issue_begin_time=? WHERE id=?",AT.minusDays(1).withNano(0),couponId);
        assertNotNull(claim());
        var row=jdbc.queryForMap("SELECT term_begin_time,term_end_time FROM user_coupon WHERE coupon_id=?",couponId);
        assertEquals(AT.withNano(0),row.get("term_begin_time"));
        assertEquals(AT.withNano(0).plusDays(30),row.get("term_end_time"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_coupon WHERE coupon_id=? AND term_begin_time<=CAST(? AS DATETIME(6)) AND term_end_time>CAST(? AS DATETIME(6))",Integer.class,couponId,AT.toString(),AT.toString()));
    }
    @Test void exactComparisonKeepsElapsedMillisecondRequestsImmediateAndScheduledRequestsPending(){
        var elapsed=form();elapsed.setIssueBeginTime(AT.minusNanos(100_000_000));issues.beginIssue(elapsed);
        assertEquals(CouponStatus.ISSUING,read().getStatus());
        jdbc.update("UPDATE coupon SET status=1 WHERE id=?",couponId);
        var scheduled=form();scheduled.setIssueBeginTime(AT.plusSeconds(10).withNano(0));issues.beginIssue(scheduled);
        assertEquals(CouponStatus.UN_ISSUE,read().getStatus());assertEquals(scheduled.getIssueBeginTime(),read().getIssueBeginTime());
    }
}

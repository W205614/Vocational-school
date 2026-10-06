package com.tianji.promotion.service.impl;
import com.tianji.api.dto.promotion.*;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.*;
import com.tianji.promotion.enums.*;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DiscountServiceTest {
 private UserCouponMapper mapper;private ICouponScopeService scopes;private DiscountServiceImpl service;
 @BeforeEach void setup(){mapper=mock(UserCouponMapper.class);scopes=mock(ICouponScopeService.class);service=new DiscountServiceImpl(mapper,scopes,Runnable::run);UserContext.setUser(1L);}
 @AfterEach void cleanup(){UserContext.removeUser();}
 private Coupon coupon(int i){
  return new Coupon().setId((long)i).setCreater(100L+i).setDiscountType(i%2==0?DiscountType.RATE_DISCOUNT:DiscountType.PRICE_DISCOUNT)
   .setDiscountValue(i%2==0?80:125).setThresholdAmount(0).setMaxDiscountAmount(100000).setSpecific(false);
 }
 private List<OrderCourseDTO> courses(){return List.of(new OrderCourseDTO().setId(1L).setCateId(10L).setPrice(1737),new OrderCourseDTO().setId(2L).setCateId(20L).setPrice(891));}
 @Test void oneThroughSixCouponsMatchIndependentExhaustiveOracle(){
  for(int count=1;count<=6;count++){
   List<Coupon> coupons=new ArrayList<>();for(int i=1;i<=count;i++)coupons.add(coupon(i));when(mapper.queryMyCoupons(1L)).thenReturn(coupons);
   var result=service.findDiscountSolution(courses());int expected=oracle(coupons,new boolean[count],2628,0);
   assertEquals(expected,result.getFirst().getDiscountAmount(),"count "+count);
   for(var candidate:result){assertEquals(candidate.getDiscountAmount(),candidate.getDiscountDetail().values().stream().mapToInt(Integer::intValue).sum());assertTrue(candidate.getDiscountDetail().get(1L)<=1737);assertTrue(candidate.getDiscountDetail().get(2L)<=891);}
  }
 }
 // Independent scalar recurrence for full-scope tickets. Does not call production calculation or strategies.
 private int oracle(List<Coupon> coupons,boolean[] used,int balance,int reduction){
  int best=reduction;
  for(int i=0;i<used.length;i++)if(!used[i]){
   var coupon=coupons.get(i);int amount=coupon.getDiscountType()==DiscountType.RATE_DISCOUNT?(int)((long)balance*(100-coupon.getDiscountValue())/100):coupon.getDiscountValue();
   if(amount>balance || balance<coupon.getThresholdAmount())continue;
   used[i]=true;best=Math.max(best,oracle(coupons,used,balance-amount,reduction+amount));used[i]=false;
  }return best;
 }
 @Test void sevenCouponsAreRejectedWithoutExecutingSearch(){
  when(mapper.queryMyCoupons(1L)).thenReturn(java.util.stream.IntStream.rangeClosed(1,7).mapToObj(this::coupon).toList());
  assertThrows(BadRequestException.class,()->service.findDiscountSolution(courses()));
 }
 @Test @SuppressWarnings("unchecked") void scopeFilteringAndDuplicateTemplateTicketsPreserveUserTicketIds(){
  var first=coupon(1).setSpecific(true);var second=coupon(1).setCreater(999L).setSpecific(true);
  when(mapper.queryMyCoupons(1L)).thenReturn(List.of(first,second));
  LambdaQueryChainWrapper<CouponScope> chain=mock(LambdaQueryChainWrapper.class,RETURNS_SELF);when(scopes.lambdaQuery()).thenReturn(chain);
  doReturn(chain).when(chain).in(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyCollection());
  when(chain.list()).thenReturn(List.of(new CouponScope().setCouponId(1L).setBizId(10L)));
  var result=service.findDiscountSolution(courses()).getFirst();assertEquals(250,result.getDiscountAmount());
  assertEquals(Set.of(101L,999L),new HashSet<>(result.getIds()));assertEquals(0,result.getDiscountDetail().get(2L));
  verify(chain,times(1)).list();
 }
 @Test void queuedTimeoutCancelsTaskAndReturnsNoPartialOptimum(){
  when(mapper.queryMyCoupons(1L)).thenReturn(List.of(coupon(1)));List<Runnable> queued=new ArrayList<>();
  var timed=new DiscountServiceImpl(mapper,scopes,queued::add);
  assertThrows(CommonException.class,()->timed.findDiscountSolution(courses()));
  assertEquals(1,queued.size());assertTrue(((Future<?>)queued.getFirst()).isCancelled());
 }
 @Test void allocationUsesLongArithmeticAndRespectsRoundingAndCapacity(){
  var items=List.of(new OrderCourseDTO().setId(1L).setPrice(1_000_000_000),new OrderCourseDTO().setId(2L).setPrice(999_999_999));
  Map<Long,Integer> details=new LinkedHashMap<>(Map.of(1L,0,2L,0));DiscountServiceImpl.allocate(details,items,1_999_999_999,1_999_999_998);
  assertEquals(1_999_999_998,details.values().stream().mapToInt(Integer::intValue).sum());assertTrue(details.get(1L)<=1_000_000_000);assertTrue(details.get(2L)<=999_999_999);
 }
 @Test void invalidPerPriceThresholdCannotLoopAndLargeRateDoesNotOverflow(){
  var every=new com.tianji.promotion.strategy.discount.PerPriceDiscount();var coupon=coupon(1).setThresholdAmount(0);
  assertFalse(every.canUse(100,coupon));assertThrows(BadRequestException.class,()->every.calculateDiscount(100,coupon));
  assertEquals(400_000_000,new com.tianji.promotion.strategy.discount.RateDiscount().calculateDiscount(2_000_000_000,coupon(2).setMaxDiscountAmount(0)));
 }
}

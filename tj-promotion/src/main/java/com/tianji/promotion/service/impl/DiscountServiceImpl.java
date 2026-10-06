package com.tianji.promotion.service.impl;

import com.tianji.api.dto.promotion.*;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.*;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.*;
import com.tianji.promotion.strategy.discount.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements IDiscountService {
    public static final int MAX_COUPONS=6;
    private final UserCouponMapper userCouponMapper;
    private final ICouponScopeService scopeService;
    private final Executor discountSolutionExecutor;
    @Override public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
        validateCourses(courses);
        List<Coupon> coupons=userCouponMapper.queryMyCoupons(requireUser());
        if(coupons.size()>MAX_COUPONS) throw new BadRequestException("一次最多参与 6 张有效用户券，请选择后重试");
        Map<Coupon,List<OrderCourseDTO>> scopes=available(coupons,courses);
        if(scopes.isEmpty()) return List.of();
        FutureTask<List<CouponDiscountDTO>> task=new FutureTask<>(() -> {
            List<CouponDiscountDTO> results=new ArrayList<>();
            enumerate(new ArrayList<>(scopes.keySet()),new boolean[scopes.size()],new ArrayList<>(),scopes,courses,results);
            Map<Integer,CouponDiscountDTO> best=new TreeMap<>(Comparator.reverseOrder());
            for(CouponDiscountDTO dto:results) {
                if(dto.getDiscountAmount()<=0) continue;
                best.merge(dto.getDiscountAmount(),dto,(a,b) -> compare(a,b)<=0?a:b);
            }
            return List.copyOf(best.values());
        });
        try {
            discountSolutionExecutor.execute(task);
            return task.get(2,TimeUnit.SECONDS);
        } catch(InterruptedException e) {
            task.cancel(true); Thread.currentThread().interrupt();
            throw new CommonException("优惠计算被中断，请重试",e);
        } catch(TimeoutException | RejectedExecutionException e) {
            task.cancel(true); throw new CommonException("优惠计算繁忙或超时，请重试",e);
        } catch(ExecutionException e) {
            throw new CommonException("优惠计算失败，请重试",e.getCause());
        }
    }
    private static int compare(CouponDiscountDTO a,CouponDiscountDTO b) {
        int c=Integer.compare(a.getIds().size(),b.getIds().size());
        return c!=0?c:a.getIds().toString().compareTo(b.getIds().toString());
    }
    private void enumerate(List<Coupon> coupons,boolean[] used,List<Coupon> path,
                           Map<Coupon,List<OrderCourseDTO>> scopes,List<OrderCourseDTO> courses,List<CouponDiscountDTO> results) {
        if(Thread.currentThread().isInterrupted()) throw new CancellationException();
        for(int i=0;i<coupons.size();i++) {
            if(used[i]) continue;
            used[i]=true;path.add(coupons.get(i));
            results.add(calculate(scopes,courses,path,false));
            enumerate(coupons,used,path,scopes,courses,results);
            path.removeLast();used[i]=false;
        }
    }
    @Override public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO order) {
        validateCourses(order.getCourseList());
        List<Long> ids=order.getUserCouponIds();
        if(ids==null || ids.isEmpty()) { CouponDiscountDTO d=new CouponDiscountDTO();return d; }
        if(ids.size()>MAX_COUPONS || new HashSet<>(ids).size()!=ids.size()) throw new BadRequestException("优惠券数量超限或重复");
        List<Coupon> coupons=userCouponMapper.queryCouponByUserCouponIds(ids,UserCouponStatus.UNUSED,requireUser(),order.getOrderId());
        if(coupons.size()!=ids.size()) throw new BadRequestException("优惠券不属于当前用户、已使用或已过期");
        Map<Long,Coupon> byId=coupons.stream().collect(Collectors.toMap(Coupon::getCreater,c->c));
        List<Coupon> ordered=ids.stream().map(byId::get).toList();
        Map<Coupon,List<OrderCourseDTO>> scopes=available(ordered,order.getCourseList());
        return calculate(scopes,order.getCourseList(),ordered,true);
    }
    private Map<Coupon,List<OrderCourseDTO>> available(List<Coupon> coupons,List<OrderCourseDTO> courses) {
        if(coupons.isEmpty()) return Map.of();
        List<Long> templateIds=coupons.stream().filter(c->Boolean.TRUE.equals(c.getSpecific())).map(Coupon::getId).distinct().toList();
        Map<Long,Set<Long>> scopes=new HashMap<>();
        if(!templateIds.isEmpty()) for(CouponScope scope:scopeService.lambdaQuery().in(CouponScope::getCouponId,templateIds).list())
            scopes.computeIfAbsent(scope.getCouponId(),id->new HashSet<>()).add(scope.getBizId());
        Map<Coupon,List<OrderCourseDTO>> result=new LinkedHashMap<>();
        for(Coupon coupon:coupons) {
            List<OrderCourseDTO> eligible=Boolean.TRUE.equals(coupon.getSpecific())
                    ?courses.stream().filter(c->scopes.getOrDefault(coupon.getId(),Set.of()).contains(c.getCateId())).toList():courses;
            int total=eligible.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            if(!eligible.isEmpty() && DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(total,coupon)) result.put(coupon,eligible);
        }
        return result;
    }
    CouponDiscountDTO calculate(Map<Coupon,List<OrderCourseDTO>> scopes,List<OrderCourseDTO> courses,List<Coupon> path,boolean strict) {
        CouponDiscountDTO dto=new CouponDiscountDTO();
        Map<Long,Integer> details=new LinkedHashMap<>();
        courses.forEach(c->details.put(c.getId(),0)); dto.setDiscountDetail(details);
        for(Coupon coupon:path) {
            List<OrderCourseDTO> eligible=scopes.get(coupon);
            if(eligible==null) { if(strict) throw new BadRequestException("优惠券不适用于订单");continue; }
            int total=eligible.stream().mapToInt(c->c.getPrice()-details.get(c.getId())).sum();
            Discount discount=DiscountStrategy.getDiscount(coupon.getDiscountType());
            if(total==0 || !discount.canUse(total,coupon)) { if(strict) throw new BadRequestException("优惠券未达到使用门槛");continue; }
            int amount=discount.calculateDiscount(total,coupon);
            if(amount<0 || amount>total) throw new BadRequestException("优惠金额无效");
            allocate(details,eligible,total,amount);
            dto.getIds().add(coupon.getCreater()); dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(Math.addExact(dto.getDiscountAmount(),amount));
        }
        return dto;
    }
    static void allocate(Map<Long,Integer> details,List<OrderCourseDTO> courses,int total,int amount) {
        int remaining=amount;
        for(OrderCourseDTO c:courses) {
            int balance=c.getPrice()-details.get(c.getId());
            int share=(int)((long)amount*balance/total);
            details.merge(c.getId(),share,Integer::sum); remaining-=share;
        }
        // Allocate rounding remainder only to courses which still have capacity.
        for(OrderCourseDTO c:courses) {
            int extra=Math.min(remaining,c.getPrice()-details.get(c.getId()));
            details.merge(c.getId(),extra,Integer::sum);remaining-=extra;
            if(remaining==0) break;
        }
        if(remaining!=0) throw new IllegalStateException("Discount allocation mismatch");
    }
    private static void validateCourses(List<OrderCourseDTO> courses) {
        if(courses==null || courses.isEmpty() || courses.size()>100) throw new BadRequestException("课程列表无效");
        Set<Long> ids=new HashSet<>(); long total=0;
        for(OrderCourseDTO c:courses) {
            if(c==null || c.getId()==null || c.getPrice()==null || c.getPrice()<0 || !ids.add(c.getId())) throw new BadRequestException("课程金额或标识无效");
            total+=c.getPrice();
        }
        if(total>Integer.MAX_VALUE) throw new BadRequestException("订单金额超限");
    }
    private static long requireUser() {
        Long id=UserContext.getUser();if(id==null) throw new UnauthorizedException("请先登录");return id;
    }
}

import com.tianji.promotion.domain.dto.*;
import com.tianji.trade.domain.dto.*;
import com.tianji.pay.sdk.dto.PayApplyDTO;
import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.*;

/** Offline Bean Validation against actual packaged DTOs. No Spring, network or DB. */
public class FixtureDtoContractProbe {
 public static void main(String[] args)throws Exception {
  var json=JsonMapper.builder().findAndAddModules().build();
  var validator=Validation.buildDefaultValidatorFactory().getValidator();
  int checks=0;
  var coupon=json.readValue(System.in.readAllBytes(),CouponFormDTO.class);
  if(!validator.validate(coupon).isEmpty())throw new AssertionError("Generated coupon violates actual production DTO");checks++;
  for(var edge:Map.of(3,false,4,true,20,true,21,false,24,false).entrySet()) {
   coupon.setName("a".repeat(edge.getKey()));boolean valid=validator.validateProperty(coupon,"name").isEmpty();
   if(valid!=edge.getValue())throw new AssertionError("Coupon name boundary differs");checks++;
  }
  coupon.setTotalNum(0);if(validator.validateProperty(coupon,"totalNum").isEmpty())throw new AssertionError("Invalid stock admitted");checks++;
  coupon.setUserLimit(11);if(validator.validateProperty(coupon,"userLimit").isEmpty())throw new AssertionError("Invalid user limit admitted");checks++;
  var issue=new CouponIssueFormDTO();issue.setId(12L);issue.setIssueEndTime(LocalDateTime.now().plusDays(2));issue.setTermDays(30);
  if(!validator.validate(issue).isEmpty())throw new AssertionError("Generated issue violates actual DTO");checks++;
  issue.setIssueEndTime(LocalDateTime.now().minusDays(1));if(validator.validate(issue).isEmpty())throw new AssertionError("Past issue accepted");checks++;
  var order=new PlaceOrderDTO();order.setOrderId(40L);order.setCourseIds(List.of(7L));order.setCouponIds(List.of(11L));
  if(!validator.validate(order).isEmpty())throw new AssertionError("Generated order violates actual DTO");checks++;
  order.setCourseIds(List.of());if(validator.validate(order).isEmpty())throw new AssertionError("Empty order accepted");checks++;
  order.setCourseIds(java.util.stream.LongStream.rangeClosed(1,11).boxed().toList());if(validator.validate(order).isEmpty())throw new AssertionError("Oversize order accepted");checks++;
  var publicPay=new PayApplyFormDTO();publicPay.setOrderId(40L);publicPay.setPayChannelCode("mockPay");
  if(!validator.validate(publicPay).isEmpty())throw new AssertionError("Generated public payment violates DTO");checks++;
  var payment=PayApplyDTO.builder().bizOrderNo(40L).bizUserId(1L).amount(90).payChannelCode("mockPay").payType(4).orderInfo("Course").build();
  if(!validator.validate(payment).isEmpty())throw new AssertionError("Generated internal payment violates DTO");checks++;
  payment.setAmount(0);if(validator.validate(payment).isEmpty())throw new AssertionError("Zero payment accepted");checks++;
  System.out.println("FIXTURE_DTO_PROOF="+json.writeValueAsString(Map.of("status","PASSED","checks",checks,"actualProductionDtos",List.of("CouponFormDTO","CouponIssueFormDTO","PlaceOrderDTO","PayApplyFormDTO","PayApplyDTO"),"runtimeExercised",false)));
 }
}

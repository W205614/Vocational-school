package com.tianji.pay.third.ali;

import com.alipay.easysdk.payment.common.models.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.easysdk.payment.common.models.TradeFundBill;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AliPayAdapterTest {
 @Test void centsRemainExactAcrossAllPriceMagnitudes(){
  for(int amount:new int[]{0,1,99,100,19900,19999,99999,Integer.MAX_VALUE}){
   String value=AliPayService.transferAmount2String(amount);assertTrue(value.matches("[0-9]+\\.[0-9]{2}"));assertEquals(amount,AliPayService.transferStringAmount2Int(value));
  }
  assertEquals("199.00",AliPayService.transferAmount2String(19900));assertEquals("199.99",AliPayService.transferAmount2String(19999));
  assertThrows(IllegalArgumentException.class,()->AliPayService.transferAmount2String(-1));
  assertThrows(ArithmeticException.class,()->AliPayService.transferStringAmount2Int("0.001"));
 }
 @Test void successfulRefundQueryRemainsSuccessWithoutOptionalFundDetails(){
  var response=new AlipayTradeFastpayRefundQueryResponse();response.code="10000";response.refundStatus="REFUND_SUCCESS";response.refundAmount="199.00";
  var parsed=AliPayService.parseRefundQuery(response);assertTrue(parsed.getSuccess());assertEquals(2,parsed.getStatus());assertEquals(19900,parsed.getAmount());assertNull(parsed.getChannel());
 }
 @Test void optionalRefundDetailsAndChannelRejectionAreHandledOffline(){
  var response=new AlipayTradeFastpayRefundQueryResponse();response.code="10000";response.refundStatus="REFUND_SUCCESS";response.refundAmount="199.99";
  var detail=new TradeFundBill();detail.amount="99.99";detail.fundChannel="ALIPAYACCOUNT";response.refundDetailItemList=List.of(detail);
  var success=AliPayService.parseRefundQuery(response);assertTrue(success.getSuccess());assertEquals(19999,success.getAmount());
  response.subCode="ACQ.INVALID_PARAMETER";response.code="40004";response.msg="Business Failed";
  var rejected=AliPayService.parseRefundQuery(response);assertFalse(rejected.getSuccess());assertEquals("40004",rejected.getCode());
 }
}

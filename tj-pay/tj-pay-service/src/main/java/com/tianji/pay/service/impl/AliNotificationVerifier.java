package com.tianji.pay.service.impl;

import com.alipay.easysdk.kernel.util.Signer;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.pay.third.ali.AliPayService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Offline RSA verification precedes all settlement side effects. Never logs provider payloads. */
@Component
public final class AliNotificationVerifier {
    public record Payment(long number,int cents,LocalDateTime occurred,boolean paid) {}
    private final Environment environment;
    public AliNotificationVerifier(Environment environment){this.environment=environment;}
    public Payment verify(Map<String,String> parameters) {
        try {
            if(!environment.getProperty("tj.pay.ali.enabled",Boolean.class,false))throw invalid();
            String key=property("public-key","publicKey"),app=property("app-id","appId"),seller=property("seller-id","sellerId");
            if(key.isBlank() || app.isBlank() || seller.isBlank() || !"RSA2".equals(parameters.get("sign_type")) ||
                !Signer.verifyParams(new java.util.HashMap<>(parameters),key))throw invalid();
            if(!app.equals(parameters.get("app_id")) || !seller.equals(parameters.get("seller_id")))throw invalid();
            boolean paid="TRADE_SUCCESS".equals(parameters.get("trade_status")) || "TRADE_FINISHED".equals(parameters.get("trade_status"));
            if(!paid)return new Payment(0,0,null,false);
            String number=parameters.get("out_trade_no"),amount=parameters.get("total_amount"),at=parameters.get("gmt_payment");
            if(number==null || !number.matches("[0-9]{1,19}") || amount==null || !amount.matches("[0-9]{1,10}\\.[0-9]{2}") || at==null)throw invalid();
            long parsed=Long.parseLong(number);int cents=AliPayService.transferStringAmount2Int(amount);
            if(parsed<1 || cents<1)throw invalid();
            return new Payment(parsed,cents,LocalDateTime.parse(at,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)),true);
        } catch(BadRequestException error){throw error;}
        catch(Exception error){throw invalid();}
    }
    private String property(String canonical,String legacy){
        return environment.getProperty("tj.pay.ali."+canonical,environment.getProperty("tj.pay.ali."+legacy,""));
    }
    private static BadRequestException invalid(){return new BadRequestException("支付通知校验失败");}
}

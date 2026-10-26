package com.tianji.pay.reliability;

import com.alipay.easysdk.kernel.util.Signer;
import com.tianji.pay.service.impl.AliNotificationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic RSA keypair and SDK verification only: never contacts a payment provider. */
class AliNotificationVerifierTest {
    private final KeyPair keys;
    AliNotificationVerifierTest() throws Exception {var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);keys=generator.generateKeyPair();}
    private AliNotificationVerifier verifier(){return new AliNotificationVerifier(new MockEnvironment().withProperty("tj.pay.ali.enabled","true")
        .withProperty("tj.pay.ali.app-id","synthetic-app").withProperty("tj.pay.ali.seller-id","synthetic-seller")
        .withProperty("tj.pay.ali.public-key",Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())));}
    private Map<String,String> signed(Map<String,String> overrides) throws Exception {
        var parameters=new HashMap<>(Map.of("app_id","synthetic-app","seller_id","synthetic-seller","trade_status","TRADE_SUCCESS","out_trade_no","1001","total_amount","199.99","gmt_payment","2026-10-26 10:00:00"));parameters.putAll(overrides);
        parameters.put("sign",new Signer().sign(Signer.getSignCheckContent(parameters),Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded())));parameters.put("sign_type","RSA2");return parameters;
    }
    @Test void verifiesRealRsaSignatureAndUsesPaymentTimeAndExactCents() throws Exception {
        var payment=verifier().verify(signed(Map.of()));assertTrue(payment.paid());assertEquals(1001L,payment.number());assertEquals(19999,payment.cents());assertEquals(10,payment.occurred().getHour());
    }
    @Test void manuallyAssembledChildEnvironmentsRetainExistingCamelCaseConfiguration() throws Exception {
        var environment=new MockEnvironment().withProperty("tj.pay.ali.enabled","true").withProperty("tj.pay.ali.appId","synthetic-app")
            .withProperty("tj.pay.ali.sellerId","synthetic-seller").withProperty("tj.pay.ali.publicKey",Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        assertTrue(new AliNotificationVerifier(environment).verify(signed(Map.of())).paid());
    }
    @Test void wrongMerchantAlteredAmountMalformedDateAndMissingSignatureAreRejected() throws Exception {
        for(var change:List.of(Map.of("app_id","other-app"),Map.of("seller_id","other-seller"),Map.of("gmt_payment","2026-02-30 10:00:00"),Map.of("total_amount","0.001")))
            assertThrows(RuntimeException.class,()->verifier().verify(signed(change)));
        var changed=signed(Map.of());changed.put("total_amount","1.00");assertThrows(RuntimeException.class,()->verifier().verify(changed));
        changed.remove("sign");assertThrows(RuntimeException.class,()->verifier().verify(changed));
    }
    @Test void unsuccessfulStatusAlsoRequiresAnAuthenticNotification() throws Exception {
        assertFalse(verifier().verify(signed(Map.of("trade_status","WAIT_BUYER_PAY"))).paid());
        var forged=signed(Map.of("trade_status","WAIT_BUYER_PAY"));forged.put("app_id","forged");assertThrows(RuntimeException.class,()->verifier().verify(forged));
    }
}

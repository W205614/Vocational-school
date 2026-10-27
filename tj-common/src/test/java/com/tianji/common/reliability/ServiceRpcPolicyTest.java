package com.tianji.common.reliability;
import com.tianji.common.utils.ServiceRpcPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServiceRpcPolicyTest {
    private final ServiceRpcPolicy policy=new ServiceRpcPolicy();
    @Test void noServiceIdentityCanReopenTheRetiredPhonePasswordRoute(){
        for(String caller:java.util.List.of("gateway","identity","commerce","education","support"))
            assertFalse(policy.allows(caller,"PUT","/_modules/user/students/password"));
    }
    @Test void peerCredentialCannotReachAdministrativeOrUnlistedEndpoints(){
        assertFalse(policy.allows("support","PUT","/_modules/user/users/42/password/default"));
        assertFalse(policy.allows("support","GET","/_modules/user/account-privacy/requests"));
        assertFalse(policy.allows("support","POST","/_modules/user/api/v2/admin/user/account-privacy/deletion"));
        assertFalse(policy.allows("identity","POST","/_modules/pay/pay-orders"));
        assertFalse(policy.allows("education","PUT","/_modules/promotion/user-coupons/use"));
        assertFalse(policy.allows("commerce","POST","/_modules/exam/question-biz/list"));
        assertFalse(policy.allows("identity","GET","/_modules/user/new-unreviewed-rpc"));
    }
    @Test void reviewedRpcInventoryPreservesLegitimateCallsAndSensitiveOwners(){
        assertTrue(policy.allows("support","GET","/_modules/user/users/list"));
        assertTrue(policy.allows("identity","POST","/_modules/user/users/detail/true"));
        assertTrue(policy.allows("identity","POST","/_modules/user/internal/v2/users/password/initial"));
        assertTrue(policy.allows("commerce","POST","/_modules/pay/pay-orders"));
        assertTrue(policy.allows("commerce","PUT","/_modules/promotion/internal/v2/coupon-reservations/42"));
        assertTrue(policy.allows("education","POST","/_modules/exam/question-biz/list"));
        assertTrue(policy.allows("education","GET","/_modules/media/internal/v2/media/fixture/duration"));
    }
    @Test void gatewayUsesReviewedPublicTargetsAndOnlyExplicitAuthenticationRpcExceptions(){
        assertTrue(policy.allows("gateway","GET","/_modules/auth/jwks"));
        assertTrue(policy.allows("gateway","GET","/_modules/auth/internal/v2/sessions/fixture/validate"));
        assertTrue(policy.allows("gateway","POST","/_modules/auth/accounts/mfa/enroll"));
        assertTrue(policy.allows("gateway","PUT","/_modules/user/users/42/password/default"));
        assertTrue(policy.allows("gateway","GET","/_modules/user/account-privacy/requests"));
        assertFalse(policy.allows("gateway","GET","/_modules/user/internal/v2/users/42/session-identity"));
        assertFalse(policy.allows("gateway","POST","/_modules/user/users/detail/true"));
        assertFalse(policy.allows("gateway","POST","/_modules/pay/pay-orders"));
    }
    @Test void observabilityCanOnlyScrapeMetricsAndNonCanonicalPathsFailClosed(){
        assertTrue(policy.allows("observability","GET","/actuator/prometheus"));
        assertTrue(policy.allows("observability","GET","/_modules/user/actuator/prometheus"));
        assertFalse(policy.allows("observability","GET","/_modules/user/users/list"));
        assertFalse(policy.allows("gateway","GET","/_modules/user/../auth/jwks"));
        assertFalse(policy.allows("gateway","GET","/_modules/user/%2e%2e/auth/jwks"));
        assertFalse(ServiceRpcPolicy.canonical("/actuator/health/../prometheus"));
    }
}

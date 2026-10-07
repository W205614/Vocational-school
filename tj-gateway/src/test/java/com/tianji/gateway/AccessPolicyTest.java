package com.tianji.gateway;
import com.tianji.gateway.config.AccessPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccessPolicyTest {
 @Test void unknownMethodAndRouteNeverInheritALoginGrant(){var policy=new AccessPolicy();assertTrue(policy.declares("GET","/api/v2/notes"));assertTrue(policy.declares("GET","/api/v2/admin/course/courses/page"));assertFalse(policy.declares("GET","/api/v2/admin/user/users/list"));assertFalse(policy.declares("DELETE","/api/v2/auth/accounts/login"));assertFalse(policy.declares("GET","/api/v2/services/user/users/list"));assertFalse(policy.declares("GET","/api/v2/services/user/internal/v2/users/1/session-identity"));assertFalse(policy.declares("POST","/api/v2/unconfigured-action"));}
 @Test void declaredParameterizedRoutesMatchTheirMethodOnly(){var policy=new AccessPolicy();assertTrue(policy.declares("DELETE","/api/v2/auth/accounts/sessions/123"));assertFalse(policy.declares("POST","/api/v2/auth/accounts/sessions/123"));assertTrue(policy.declares("GET","/api/v2/teacher/exam-attempts/page"));}
}

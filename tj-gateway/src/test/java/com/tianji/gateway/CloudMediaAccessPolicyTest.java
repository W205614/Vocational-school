package com.tianji.gateway;
import com.tianji.gateway.config.AccessPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CloudMediaAccessPolicyTest {
 @Test void cloudContentRequiresItsDeclaredGetRouteAndCanonicalUuid(){
  var policy=new AccessPolicy();String valid="23456789-2345-2345-2345-23456789abcd";
  assertTrue(policy.declares("GET","/api/v2/services/media/cloud-content/"+valid));
  assertFalse(policy.declares("POST","/api/v2/services/media/cloud-content/"+valid));
  assertFalse(policy.declares("GET","/api/v2/services/media/cloud-content/not-a-uuid"));
  assertFalse(policy.declares("GET","/api/v2/services/media/cloud-content/"+valid+"/extra"));
  assertFalse(policy.declares("GET","/api/v2/services/media/cloud-content/%2e%2e"));
 }
}

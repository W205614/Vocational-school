package com.tianji.common.reliability;
import com.tianji.common.autoconfigure.reliability.AcceptanceFaults;import org.junit.jupiter.api.Test;import org.springframework.mock.env.MockEnvironment;import static org.junit.jupiter.api.Assertions.*;
class AcceptanceFaultsTest{
 @Test void disabledNeedsNoProfile(){assertDoesNotThrow(()->new AcceptanceFaults(new MockEnvironment()).afterPublish("anything"));}
 @Test void refusesProductionFault(){var env=new MockEnvironment().withProperty("tj.acceptance.fault.point","after-publish").withProperty("tj.acceptance.fault.business-key","order:1:paid");env.setActiveProfiles("production");assertThrows(IllegalStateException.class,()->new AcceptanceFaults(env));}
 @Test void exactKeyAndExplicitProfileRequired(){var env=new MockEnvironment().withProperty("tj.acceptance.fault.point","after-consume");env.setActiveProfiles("acceptance");assertThrows(IllegalStateException.class,()->new AcceptanceFaults(env));}
}

package com.zhixuetang.common.reliability;

import com.zhixuetang.common.autoconfigure.reliability.AcceptanceFaults;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceFaultsTest {
    @Test
    void disabledNeedsNoProfile() {
        assertDoesNotThrow(
                () -> new AcceptanceFaults(new MockEnvironment()).afterPublish("anything"));
    }

    @Test
    void refusesProductionFault() {
        var env =
                new MockEnvironment()
                        .withProperty("zxt.acceptance.fault.point", "after-publish")
                        .withProperty("zxt.acceptance.fault.business-key", "order:1:paid");
        env.setActiveProfiles("production");
        assertThrows(IllegalStateException.class, () -> new AcceptanceFaults(env));
    }

    @Test
    void exactKeyAndExplicitProfileRequired() {
        var env = new MockEnvironment().withProperty("zxt.acceptance.fault.point", "after-consume");
        env.setActiveProfiles("acceptance");
        assertThrows(IllegalStateException.class, () -> new AcceptanceFaults(env));
    }
}

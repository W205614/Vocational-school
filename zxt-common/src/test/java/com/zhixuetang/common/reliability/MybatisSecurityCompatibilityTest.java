package com.zhixuetang.common.reliability;

import java.util.Map;
import java.util.Properties;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MybatisSecurityCompatibilityTest {
    @Test
    void preservesTheClassEntryPointRequiredByMybatisPlus() {
        assertTrue(MetaClass.forClass(Properties.class, new DefaultReflectorFactory()).hasGetter("empty"));
    }

    @Test
    void runsActualUpdatedExpressionEngineAndLegacyConversionApi() throws Exception {
        assertEquals(Boolean.TRUE, OgnlCache.getValue("name != null and count > 0", Map.of("name", "course", "count", 2)));
        assertEquals(7, org.apache.ibatis.ognl.OgnlOps.convertValue("7", Integer.class));
        var version = new Properties();
        try (var input = getClass().getClassLoader().getResourceAsStream("META-INF/maven/ognl/ognl/pom.properties")) {
            assertNotNull(input);
            version.load(input);
        }
        assertEquals("3.4.16", version.getProperty("version"));
    }
}

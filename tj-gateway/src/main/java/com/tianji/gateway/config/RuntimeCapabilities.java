package com.tianji.gateway.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Map;

/** Capabilities reflect delivered adapters, never an operator's claim that credentials exist. */
@Component
public class RuntimeCapabilities implements InitializingBean {
    private final Environment environment;
    public RuntimeCapabilities(Environment environment){this.environment=environment;}
    @Override public void afterPropertiesSet(){
        if(!environment.getProperty("tj.simulators.enabled",Boolean.class,false))
            throw new IllegalStateException("REAL mode is unsupported: cloud media upload and the cloud video player are not implemented. Use an explicit local simulator profile until both adapters are delivered.");
    }
    public Map<String,Boolean> media(){
        boolean simulated=environment.getProperty("tj.simulators.enabled",Boolean.class,false);
        return Map.of("localUpload",simulated && environment.getProperty("tj.simulators.storage",Boolean.class,false),
                "localPlayback",simulated && environment.getProperty("tj.simulators.video",Boolean.class,false),
                "cloudUpload",false,"cloudPlayback",false);
    }
}

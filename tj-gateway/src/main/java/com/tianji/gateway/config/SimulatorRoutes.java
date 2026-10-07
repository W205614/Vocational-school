package com.tianji.gateway.config;
import com.tianji.common.domain.R;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@Configuration(proxyBeanMethods=false)
public class SimulatorRoutes {
 @Bean @ConditionalOnProperty(name="tj.simulators.enabled",havingValue="true") public RouteLocator localSimulatorRouteLocator(RouteLocatorBuilder builder,Environment environment){
  if(Arrays.stream(environment.getActiveProfiles()).noneMatch(Set.of("acceptance","local-simulator")::contains))throw new IllegalStateException("Local simulators are forbidden outside explicit local profiles");
  return builder.routes().route("local-simulator",r->r.path("/api/v2/simulator/**").filters(f->f.prefixPath(java.util.Objects.toString(java.net.URI.create(environment.getRequiredProperty("tj.simulators.url")).getPath(),""))).uri(environment.getRequiredProperty("tj.simulators.url"))).build();
 }
 @RestController public static class EnvironmentController {
  private final Environment environment;EnvironmentController(Environment environment){this.environment=environment;}
  @GetMapping("/api/v2/environment") public R<?> status(){boolean simulated=environment.getProperty("tj.simulators.enabled",Boolean.class,false);
   return R.ok(Map.of("mode",simulated?"SIMULATED":"REAL","payment",simulated?"SIMULATED":environment.getProperty("tj.thirdparty.payment.configured",Boolean.class,false)?"CONFIGURED":"UNCONFIGURED","storage",mode("storage"),"video",mode("video"),"sms",mode("sms"))).requestId(UUID.randomUUID().toString());
  }
  private String mode(String service){if(environment.getProperty("tj.simulators."+service,Boolean.class,false))return "SIMULATED";return environment.getProperty("tj.thirdparty."+service+".configured",Boolean.class,false)?"CONFIGURED":"UNCONFIGURED";}
 }
}

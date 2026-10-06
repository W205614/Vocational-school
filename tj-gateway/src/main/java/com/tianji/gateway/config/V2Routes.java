package com.tianji.gateway.config;
import org.springframework.context.annotation.*;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.core.env.Environment;
import java.util.*;
@Configuration
public class V2Routes {
 public static final Map<String,String> SERVICES=Map.ofEntries(
  Map.entry("auth","auth-service"),Map.entry("user","user-service"),Map.entry("course","course-service"),
  Map.entry("learning","learning-service"),Map.entry("promotion","promotion-service"),Map.entry("trade","trade-service"),
  Map.entry("exam","exam-service"),Map.entry("media","media-service"),Map.entry("message","message-service"),
  Map.entry("remark","remark-service"),Map.entry("search","search-service"),Map.entry("pay","pay-service"),Map.entry("data","data-service"));
 @Bean public RouteLocator v2RouteLocator(RouteLocatorBuilder builder,Environment environment){
  var routes=builder.routes();
  SERVICES.forEach((alias,name)->{
   String uri=environment.getProperty("tj.routes."+alias,"lb://"+name);
   routes.route("v2-operation-"+alias,r->r.path("/api/v2/operations/"+alias+"/**").filters(f->f.rewritePath("/api/v2/operations/"+alias+"/(?<id>.*)","/api/v2/operations/${id}")).uri(uri));
   routes.route("v2-events-"+alias,r->r.path("/api/v2/admin/events/"+alias+"/**").filters(f->f.rewritePath("/api/v2/admin/events/"+alias+"/(?<rest>.*)","/api/v2/admin/events/${rest}")).uri(uri));
   routes.route("v2-operation-failures-"+alias,r->r.path("/api/v2/admin/operation-failures/"+alias+"/**").filters(f->f.rewritePath("/api/v2/admin/operation-failures/"+alias+"(?<rest>/.*)?","/api/v2/admin/operation-failures${rest}")).uri(uri));
   routes.route("v2-failures-"+alias,r->r.path("/api/v2/admin/consumer-failures/"+alias+"/**").filters(f->f.rewritePath("/api/v2/admin/consumer-failures/"+alias+"(?<rest>/.*)?","/api/v2/admin/consumer-failures${rest}")).uri(uri));
   routes.route("v2-legacy-"+alias,r->r.path("/api/v2/services/"+alias+"/**","/api/v2/admin/"+alias+"/**").filters(f->f.rewritePath("/api/v2/(services|admin)/"+alias+"/(?<rest>.*)","/${rest}")).uri(uri));
  });
  routes.route("v2-login",r->r.path("/api/v2/auth/accounts/**").filters(f->f.rewritePath("/api/v2/auth/(?<rest>.*)","/${rest}")).uri(environment.getProperty("tj.routes.auth","lb://auth-service")));
  routes.route("v2-learning",r->r.path("/api/v2/favorites/**","/api/v2/notes/**","/api/v2/sign-ins/**","/api/v2/admin/points-projections/**").uri(environment.getProperty("tj.routes.learning","lb://learning-service")));
  routes.route("v2-promotion",r->r.path("/api/v2/coupons/**","/api/v2/coupon-exchanges/**").uri(environment.getProperty("tj.routes.promotion","lb://promotion-service")));
  routes.route("v2-trade",r->r.path("/api/v2/orders/**","/api/v2/admin/dashboard","/api/v2/admin/payment-conflicts/**","/api/v2/admin/compensations/**").uri(environment.getProperty("tj.routes.trade","lb://trade-service")));
  routes.route("v2-message-delivery",r->r.path("/api/v2/admin/deliveries/**").uri(environment.getProperty("tj.routes.message","lb://message-service")));
  routes.route("v2-media-upload",r->r.path("/api/v2/admin/media-upload").uri(environment.getProperty("tj.routes.media","lb://media-service")));
  routes.route("v2-storage-cleanup",r->r.path("/api/v2/admin/storage-cleanups/**").uri(environment.getProperty("tj.routes.media","lb://media-service")));
  routes.route("v2-search-projection",r->r.path("/api/v2/admin/search-projections/**").uri(environment.getProperty("tj.routes.search","lb://search-service")));
  routes.route("v2-exam",r->r.path("/api/v2/exam-papers/**","/api/v2/exam-attempts/**","/api/v2/admin/exam-papers/**","/api/v2/teacher/exam-attempts/**").uri(environment.getProperty("tj.routes.exam","lb://exam-service")));
  return routes.build();
 }
}

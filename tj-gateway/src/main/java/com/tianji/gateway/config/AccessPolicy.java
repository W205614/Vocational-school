package com.tianji.gateway.config;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Version-controlled method/path inventory. Unknown endpoints always fail closed. */
@Component public final class AccessPolicy {
 private final List<String> rules;
 private final AntPathMatcher matcher=new AntPathMatcher();
 public AccessPolicy(){try(var input=new ClassPathResource("access-policy.txt").getInputStream()){rules=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().filter(s->!s.isBlank()).toList();}catch(Exception e){throw new IllegalStateException("Access policy is required",e);}}
 public boolean declares(String method,String path){String key=method+":"+path;return rules.stream().anyMatch(p->{if(!matcher.match(p,key))return false;var variables=matcher.extractUriTemplateVariables(p,key);for(var v:variables.entrySet()){String name=v.getKey(),value=v.getValue();if(name.equals("key")){if(!value.matches("[a-zA-Z0-9_.-]{1,100}"))return false;continue;}if(name.equals("month")){if(!value.matches("[0-9]{4}-[0-9]{2}"))return false;continue;}if(name.equals("kind")){if(!Set.of("refund","payment","sms","notice").contains(value))return false;continue;}if(name.equals("code")){if(!value.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8,10}"))return false;continue;}if(name.equals("hidden")){if(!value.matches("true|false"))return false;continue;}boolean workflow=p.contains("/operations/") || p.contains("/events/") || p.contains("-failures/") || p.contains("/storage-cleanups/") || p.contains("/accounts/sessions/") || p.contains("/deliveries/");if(!value.matches(workflow?"[0-9a-f-]{1,36}":"[0-9]{1,20}"))return false;}return true;});}
}

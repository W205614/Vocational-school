package com.tianji.common.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.AntPathMatcher;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reviewed method/path inventories. New Feign methods never acquire production access automatically. */
public final class ServiceRpcPolicy {
    private record Rule(Set<String> callers,String method,String path){}
    private static final Set<String> METHODS=Set.of("GET","POST","PUT","DELETE","PATCH");
    private final List<Rule> rpc=load("META-INF/tianji/service-rpc-policy.txt");
    private final List<Rule> gateway=load("META-INF/tianji/gateway-module-policy.txt");
    private final AntPathMatcher matcher=new AntPathMatcher();
    public boolean allows(String caller,String method,String path){
        if(caller==null || method==null || !METHODS.contains(method) || !canonical(path))return false;
        if(Set.of("observability","metrics","prometheus").contains(caller))return "GET".equals(method) && (path.equals("/actuator/prometheus") || path.matches("/_modules/[a-z]+/actuator/prometheus"));
        if("gateway".equals(caller)){
            if(matches(rpc,caller,method,path))return true; // only explicit JWKS/session verification RPCs
            if(path.contains("/internal/") || privateLegacy(method,path))return false;
            return matches(gateway,caller,method,path);
        }
        return matches(rpc,caller,method,path);
    }
    public static boolean canonical(String path){return path!=null && path.startsWith("/") && !path.contains("%") && !path.contains("\\") && !path.contains(";") && !path.contains("//") && !path.matches(".*(?:^|/)\\.\\.?(?:/|$).*") && path.chars().noneMatch(c->c<32 || c==127);}
    private boolean matches(List<Rule> rules,String caller,String method,String path){return rules.stream().anyMatch(rule->rule.callers().contains(caller) && rule.method().equals(method) && matcher.match(rule.path(),path));}
    private boolean privateLegacy(String method,String path){
        return path.startsWith("/_modules/pay/pay-orders") || path.startsWith("/_modules/pay/refund-orders") ||
                matcher.match("/_modules/user/users/detail/*",path) ||
                path.equals("/_modules/promotion/user-coupons/use") || path.equals("/_modules/promotion/user-coupons/refund") ||
                ("POST".equals(method) && (path.equals("/_modules/exam/question-biz/list") || path.equals("/_modules/message/message")));
    }
    private static List<Rule> load(String resource){
        try(var input=new ClassPathResource(resource).getInputStream()){
            List<Rule> rules=new ArrayList<>();
            for(String raw:new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().toList()){
                String line=raw.trim();if(line.isBlank() || line.startsWith("#"))continue;String[] parts=line.split("\\|",-1);
                if(parts.length!=3 || !METHODS.contains(parts[1]) || !parts[2].startsWith("/_modules/"))throw new IllegalStateException("Invalid reviewed service RPC policy");
                Set<String> callers=Set.copyOf(Arrays.asList(parts[0].split(",")));if(callers.isEmpty() || callers.stream().anyMatch(c->!c.matches("[a-z][a-z0-9-]{1,40}")))throw new IllegalStateException("Invalid service RPC caller");
                rules.add(new Rule(callers,parts[1],parts[2]));
            }
            if(rules.isEmpty())throw new IllegalStateException("Reviewed service RPC policy must not be empty");return List.copyOf(rules);
        }catch(java.io.IOException error){throw new IllegalStateException("Reviewed service RPC policy is unavailable",error);}
    }
}

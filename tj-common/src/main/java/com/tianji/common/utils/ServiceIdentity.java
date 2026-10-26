package com.tianji.common.utils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Directed service credentials; production files contain only this workload's edges. */
public final class ServiceIdentity {
    public record Credential(String caller, String token) {}
    private record Keyring(String fingerprint, Map<String,String> outbound, Map<String,List<String>> inbound) {}
    private static final Map<String,String> OWNERS=Map.ofEntries(
        Map.entry("auth","identity"),Map.entry("user","identity"),Map.entry("trade","commerce"),
        Map.entry("pay","commerce"),Map.entry("promotion","commerce"),Map.entry("course","education"),
        Map.entry("learning","education"),Map.entry("exam","education"),Map.entry("remark","education"),
        Map.entry("media","support"),Map.entry("search","support"),Map.entry("message","support"),Map.entry("data","support"));
    private static final ServiceIdentity CURRENT=new ServiceIdentity(System.getenv());
    private final boolean directed;
    private final String name, shared;
    private final Path file;
    private volatile Keyring cached;

    public ServiceIdentity(Map<String,String> environment) {
        String mode=environment.getOrDefault("TJ_INTERNAL_AUTH_MODE","shared-token");
        if(!Set.of("shared-token","service-tokens").contains(mode))throw new IllegalStateException("Unsupported service identity mode");
        directed="service-tokens".equals(mode);shared=environment.get("TJ_INTERNAL_TOKEN");
        name=environment.get("TJ_SERVICE_ID");
        if(directed && (name==null || !name.matches("[a-z][a-z0-9-]{1,40}")))throw new IllegalStateException("Service identity name is required");
        String location=environment.get("TJ_SERVICE_TOKENS_FILE");
        if(directed && (location==null || location.isBlank()))throw new IllegalStateException("Service identity file is required");
        file=directed?Path.of(location):null;
    }
    public static ServiceIdentity current(){return CURRENT;}
    public boolean directed(){return directed;}
    public void validate(){if(directed)keyring();else if(!valid(shared))throw new IllegalStateException("Service identity is not configured");}
    public Credential outgoing(String target) {
        if(!directed){if(!valid(shared))throw new IllegalStateException("Service identity is not configured");return new Credential(null,shared);}
        URI uri=URI.create(target);String path=Objects.toString(uri.getPath(),"");
        var matcher=java.util.regex.Pattern.compile("(?:^|/)_modules/([a-z]+)(?:/|$)").matcher(path);
        String peer;
        if(matcher.find())peer=OWNERS.get(matcher.group(1));
        else {String host=uri.getHost();peer=host==null?null:host.split("\\.")[0].replaceFirst("-service$","").replaceFirst("^app-","");peer=OWNERS.getOrDefault(peer,peer);}
        String token=peer==null?null:keyring().outbound().get(peer);
        if(!valid(token))throw new IllegalStateException("No credential for destination service");
        return new Credential(name,token);
    }
    public boolean accepts(String caller,String token) {
        if(!valid(token))return false;
        if(!directed)return valid(shared) && equal(shared,token);
        if(caller==null || !caller.matches("[a-z][a-z0-9-]{1,40}"))return false;
        boolean accepted=false;
        for(String candidate:keyring().inbound().getOrDefault(caller,List.of()))accepted|=equal(candidate,token);
        return accepted;
    }
    private static boolean equal(String left,String right){return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),right.getBytes(StandardCharsets.UTF_8));}
    private static boolean valid(String value){return value!=null && value.matches("[A-Za-z0-9_.:-]{32,512}");}
    private Keyring keyring() {
        try {
            var attributes=Files.readAttributes(file,BasicFileAttributes.class);
            if(!attributes.isRegularFile() || attributes.size()>65536)throw new IllegalStateException("Invalid service identity file");
            String fingerprint=attributes.lastModifiedTime()+":"+attributes.size()+":"+attributes.fileKey();
            var existing=cached;if(existing!=null && existing.fingerprint().equals(fingerprint))return existing;
            synchronized(this) {
                var json=JsonUtils.parseObj(Files.readString(file,StandardCharsets.UTF_8));
                var out=json.getJSONObject("outbound");var in=json.getJSONObject("inbound");
                if(out==null || in==null || out.size()>32 || in.size()>32)throw new IllegalStateException("Invalid service identity keyring");
                Map<String,String> outbound=new HashMap<>();Map<String,List<String>> inbound=new HashMap<>();
                for(var entry:out.entrySet()) {
                    if(!entry.getKey().matches("[a-z][a-z0-9-]{1,40}") || !(entry.getValue() instanceof String value) || !valid(value))throw new IllegalStateException("Invalid outbound service credential");
                    outbound.put(entry.getKey(),value);
                }
                for(var entry:in.entrySet()) {
                    var values=in.getJSONArray(entry.getKey());
                    if(!entry.getKey().matches("[a-z][a-z0-9-]{1,40}") || values==null || values.isEmpty() || values.size()>2)throw new IllegalStateException("Invalid inbound service credential");
                    List<String> tokens=new ArrayList<>();
                    for(Object candidate:values){if(!(candidate instanceof String value) || !valid(value))throw new IllegalStateException("Invalid inbound service credential");tokens.add(value);}
                    inbound.put(entry.getKey(),List.copyOf(tokens));
                }
                cached=new Keyring(fingerprint,Map.copyOf(outbound),Map.copyOf(inbound));return cached;
            }
        } catch(java.io.IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Service identity keyring is unavailable or invalid");
        }
    }
}

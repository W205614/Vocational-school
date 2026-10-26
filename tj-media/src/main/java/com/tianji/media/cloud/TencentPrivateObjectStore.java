package com.tianji.media.cloud;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.*;
import com.qcloud.cos.exception.CosServiceException;
import tools.jackson.databind.json.JsonMapper;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** COS objects stay private; students never receive a provider GET URL. */
public final class TencentPrivateObjectStore implements CloudObjectStore {
    private final COSClient client;
    private final String bucket;
    public TencentPrivateObjectStore(COSClient client,String bucket){this.client=client;this.bucket=bucket;}
    @Override public void verifyPrivateBucket(){
        var acl=client.getBucketAcl(bucket);
        // COS has both AllUsers and AuthenticatedUsers global groups. The SDK's
        // enum need not enumerate every server-side URI, so accept only explicit UIN grants.
        if(acl.getGrantsAsList().stream().anyMatch(grant->!(grant.getGrantee() instanceof UinGrantee)||wildcard(grant.getGrantee().getIdentifier())))throw new IllegalStateException("Cloud media bucket must not grant global group access");
        try{
            String policy=client.getBucketPolicy(bucket).getPolicyText();
            if(policy!=null&&!policy.isBlank()&&publicAllow(JsonMapper.builder().build().readValue(policy,Map.class)))throw new IllegalStateException("Cloud media bucket policy must not allow anonymous access");
        }catch(CosServiceException error){if(error.getStatusCode()!=404)throw error;}
    }
    static boolean publicAllow(Map<?,?> policy){
        Object statements=policy.get("Statement");if(statements==null)statements=policy.get("statement");
        if(statements instanceof Map<?,?> row)statements=List.of(row);
        if(!(statements instanceof List<?> list))throw new IllegalStateException("Unrecognized COS bucket policy");
        for(Object item:list)if(item instanceof Map<?,?> row){
            String effect=Objects.toString(row.containsKey("Effect")?row.get("Effect"):row.get("effect"),"");
            Object principal=row.containsKey("Principal")?row.get("Principal"):row.get("principal");
            if("allow".equalsIgnoreCase(effect)&&(principal==null||row.containsKey("NotPrincipal")||row.containsKey("notPrincipal")||row.containsKey("notprincipal")||wildcard(principal)))return true;
        }return false;
    }
    private static boolean wildcard(Object value){
        if(value instanceof String text){String lower=text.toLowerCase(Locale.ROOT);return text.contains("*")||lower.contains("anyone")||lower.contains("/groups/")||lower.contains("allusers")||lower.contains("authenticatedusers");}
        if(value instanceof Map<?,?> map)return map.values().stream().anyMatch(TencentPrivateObjectStore::wildcard);
        return value instanceof Collection<?> list&&list.stream().anyMatch(TencentPrivateObjectStore::wildcard);
    }
    @Override public Upload uploadUrl(String key,long size,Instant expiry){
        var request=new GeneratePresignedUrlRequest(bucket,key,HttpMethodName.PUT);
        request.setExpiration(Date.from(expiry));request.setContentType("application/octet-stream");
        request.putCustomRequestHeader("x-cos-acl","private");request.putCustomRequestHeader("content-length",Long.toString(size));
        return new Upload(client.generatePresignedUrl(request).toExternalForm(),Map.of("Content-Type","application/octet-stream","x-cos-acl","private"));
    }
    @Override public ObjectInfo head(String key){var row=client.getObjectMetadata(bucket,key);return new ObjectInfo(row.getContentLength(),row.getContentType(),row.getVersionId(),row.getETag());}
    @Override public InputStream read(String key,String version,String etag,long first,long last){
        if((version==null||version.isBlank()||version.equals("null"))&&(etag==null||etag.isBlank()))throw new IllegalArgumentException("An immutable object version or ETag is required");
        var request=new GetObjectRequest(bucket,key);if(version!=null&&!version.isBlank())request.setVersionId(version);if(etag!=null&&!etag.isBlank())request.setMatchingETagConstraints(List.of(etag));
        if(first>=0)request.setRange(first,last);return client.getObject(request).getObjectContent();
    }
    @Override public ObjectInfo putReady(String key,Path file){
        var request=new PutObjectRequest(bucket,key,file.toFile()).withCannedAcl(CannedAccessControlList.Private);
        try{
            var metadata=new ObjectMetadata();metadata.setContentType("video/mp4");metadata.setContentLength(Files.size(file));metadata.setContentMD5(contentMd5(file));request.setMetadata(metadata);
            request.putCustomRequestHeader("x-cos-forbid-overwrite","true");
            var result=client.putObject(request);
            if(result.getETag()==null||result.getETag().isBlank())throw new IllegalStateException("COS did not return an object ETag");
            // Bind to this PUT response, never a later HEAD of a potentially overwritten key.
            return new ObjectInfo(Files.size(file),"video/mp4",result.getVersionId(),result.getETag());
        }catch(IOException error){throw new IllegalStateException("Could not read the validated media output",error);}
    }
    private static String contentMd5(Path file)throws IOException{
        // COS Content-MD5 is a transport checksum. SHA-256 remains the task's content integrity check.
        try{var digest=java.security.MessageDigest.getInstance("MD5");try(var input=Files.newInputStream(file)){byte[] bytes=new byte[65536];int count;while((count=input.read(bytes))!=-1)digest.update(bytes,0,count);}return Base64.getEncoder().encodeToString(digest.digest());}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    @Override public ObjectInfo putImage(String key,byte[] bytes,String contentType){
        if(key==null||!key.matches("covers/[0-9a-f]{64}\\.(png|jpg)")||bytes==null||bytes.length<1||bytes.length>5*1024*1024
                ||!Objects.equals(contentType,key.endsWith(".png")?"image/png":"image/jpeg"))throw new IllegalArgumentException("A bounded validated course image is required");
        var metadata=new ObjectMetadata();metadata.setContentType(contentType);metadata.setContentLength(bytes.length);
        try{metadata.setContentMD5(Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("MD5").digest(bytes)));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
        var request=new PutObjectRequest(bucket,key,new ByteArrayInputStream(bytes),metadata).withCannedAcl(CannedAccessControlList.Private);
        request.putCustomRequestHeader("x-cos-forbid-overwrite","true");
        try{
            var result=client.putObject(request);
            if(result.getETag()==null||result.getETag().isBlank())throw new IllegalStateException("COS did not return an image ETag");
            return new ObjectInfo(bytes.length,contentType,result.getVersionId(),result.getETag());
        }catch(CosServiceException error){
            // Content-addressed covers are reusable. The caller verifies the pinned
            // existing bytes before publishing the path; other failures still fail closed.
            if(error.getStatusCode()!=409)throw error;
            return head(key);
        }
    }
    @Override public void delete(String key){client.deleteObject(bucket,key);}
}

package com.tianji.auth.service.impl;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** RFC 6238, SHA-1/6 digits/30 seconds. Secrets are bound to their account with AES-GCM AAD. */
public final class TotpSecrets {
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final String activeKeyId;
    private final String legacyKeyId;
    private final Map<String,byte[]> keys;
    private final SecureRandom random=new SecureRandom();
    public TotpSecrets(String encodedKey) {
        this(encodedKey,Map.of(),null);
    }
    public TotpSecrets(String encodedKey,Map<String,String> decryptionKeys,String legacyKeyId) {
        byte[] active=decodeKey(encodedKey);activeKeyId=keyId(active);
        if(decryptionKeys.size()>2)throw new IllegalArgumentException("MFA supports at most three overlapping encryption keys");
        var configured=new LinkedHashMap<String,byte[]>();configured.put(activeKeyId,active);
        for(var entry:decryptionKeys.entrySet()){
            byte[] previous=decodeKey(entry.getValue());
            if(!keyId(previous).equals(entry.getKey()) || configured.putIfAbsent(entry.getKey(),previous)!=null)
                throw new IllegalArgumentException("MFA decryption key identifiers must match distinct AES keys");
        }
        this.keys=Map.copyOf(configured);this.legacyKeyId=legacyKeyId==null?activeKeyId:legacyKeyId;
        if(!keys.containsKey(this.legacyKeyId))throw new IllegalArgumentException("MFA legacy key identifier is not configured");
    }
    private static byte[] decodeKey(String encoded){
        byte[] decoded;
        try{decoded=Base64.getDecoder().decode(encoded);}catch(RuntimeException error){throw new IllegalArgumentException("MFA encryption key must be base64");}
        if(decoded.length!=32)throw new IllegalArgumentException("MFA encryption key must contain exactly 32 bytes");return decoded;
    }
    private static String keyId(byte[] key){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 is unavailable",impossible);}
    }
    public String activeKeyId(){return activeKeyId;}
    public String generate() {
        byte[] bytes=new byte[20];random.nextBytes(bytes);return base32(bytes);
    }
    public String encrypt(long user,String secret) {
        try {
            byte[] iv=new byte[12];random.nextBytes(iv);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.get(activeKeyId),"AES"),new GCMParameterSpec(128,iv));
            cipher.updateAAD(("v1\n"+activeKeyId+"\n"+user).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            return "v1."+activeKeyId+"."+Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array());
        }catch(Exception error){throw new IllegalStateException("Cannot encrypt MFA secret",error);}
    }
    public String decrypt(long user,String encoded) {
        try {
            String id=legacyKeyId,payload=encoded;byte[] aad=Long.toString(user).getBytes(StandardCharsets.UTF_8);
            if(encoded.contains(".")){
                String[] parts=encoded.split("\\.",-1);
                if(parts.length!=3 || !parts[0].equals("v1") || !keys.containsKey(parts[1]))throw new IllegalArgumentException();
                id=parts[1];payload=parts[2];aad=("v1\n"+id+"\n"+user).getBytes(StandardCharsets.UTF_8);
            }
            byte[] bytes=Base64.getDecoder().decode(payload);if(bytes.length<29)throw new IllegalArgumentException();
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(keys.get(id),"AES"),new GCMParameterSpec(128,bytes,0,12));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.US_ASCII);
        }catch(Exception error){throw new IllegalStateException("Cannot decrypt MFA secret; check the configured key",error);}
    }
    public String reencrypt(long user,String encoded){
        String secret=decrypt(user,encoded);
        return encoded.startsWith("v1."+activeKeyId+".")?encoded:encrypt(user,secret);
    }
    public static long matchingStep(String secret,String code,long epochSeconds,long previousStep) {
        if(code==null || !code.matches("[0-9]{6}"))return -1;
        long at=epochSeconds/30;
        for(long step=at-1;step<=at+1;step++)if(step>previousStep && MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),code(secret,step,6).getBytes(StandardCharsets.US_ASCII)))return step;
        return -1;
    }
    public static String code(String secret,long step,int digits) {
        try {
            var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(decode(secret),"HmacSHA1"));
            byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());int offset=hash[hash.length-1]&15;
            int value=((hash[offset]&127)<<24)|((hash[offset+1]&255)<<16)|((hash[offset+2]&255)<<8)|(hash[offset+3]&255);
            return String.format(java.util.Locale.ROOT,"%0"+digits+"d",value%(int)Math.pow(10,digits));
        }catch(Exception error){throw new IllegalStateException("Cannot calculate TOTP",error);}
    }
    static String base32(byte[] data) {
        StringBuilder out=new StringBuilder();int bits=0,buffer=0;
        for(byte value:data){buffer=(buffer<<8)|(value&255);bits+=8;while(bits>=5){bits-=5;out.append(ALPHABET.charAt((buffer>>bits)&31));}}
        if(bits>0)out.append(ALPHABET.charAt((buffer<<(5-bits))&31));return out.toString();
    }
    private static byte[] decode(String text) {
        var out=new java.io.ByteArrayOutputStream();int bits=0,buffer=0;
        for(char c:text.toCharArray()){int n=ALPHABET.indexOf(c);if(n<0)throw new IllegalArgumentException("Invalid base32 secret");buffer=(buffer<<5)|n;bits+=5;if(bits>=8){bits-=8;out.write((buffer>>bits)&255);}}
        return out.toByteArray();
    }
}

package com.tianji.auth.common.util;

import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;

/** Explicitly trusted public keys; key ids identify the encoded public key, never a caller-supplied URL. */
public final class JwtVerificationKeys {
    private final Map<String, JWTSigner> signers;
    private final List<Map<String, String>> jwks;

    public JwtVerificationKeys(Collection<? extends PublicKey> keys) {
        if (keys.isEmpty() || keys.size() > 8) throw new IllegalArgumentException("JWT verification requires 1 to 8 keys");
        var trusted = new LinkedHashMap<String, JWTSigner>();
        var published = new ArrayList<Map<String, String>>();
        for (PublicKey key : keys) {
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048)
                throw new IllegalArgumentException("JWT verification requires RSA keys of at least 2048 bits");
            String id = id(key);
            if (trusted.putIfAbsent(id, JWTSignerUtil.createSigner("rs256", key)) != null)
                throw new IllegalArgumentException("Duplicate JWT verification key");
            published.add(Map.of("kty", "RSA", "alg", "RS256", "use", "sig", "kid", id,
                    "n", unsigned(rsa.getModulus()), "e", unsigned(rsa.getPublicExponent())));
        }
        signers = Collections.unmodifiableMap(trusted);
        jwks = List.copyOf(published);
    }

    public static String id(PublicKey key) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key.getEncoded())); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes);
    }

    public String jwks() { return JSONUtil.toJsonStr(Map.of("keys", jwks)); }
    public JWTSigner first() { return signers.values().iterator().next(); }

    public boolean verify(JWT jwt) {
        if (!"RS256".equalsIgnoreCase(Objects.toString(jwt.getHeader("alg"), ""))) return false;
        Object id = jwt.getHeader("kid");
        if (id != null) {
            JWTSigner signer = signers.get(id);
            return signer != null && jwt.setSigner(signer).verify();
        }
        // Tokens issued before kid was introduced remain valid only while their key is explicitly trusted.
        for (JWTSigner signer : signers.values()) if (jwt.setSigner(signer).verify()) return true;
        return false;
    }

    public static JwtVerificationKeys parse(String document) throws GeneralSecurityException {
        if (document.length() > 32768) throw new IllegalArgumentException("JWT key response is too large");
        var keys = JSONUtil.parseObj(document).getJSONArray("keys");
        if (keys == null || keys.isEmpty() || keys.size() > 8) throw new IllegalArgumentException("Invalid JWT key set");
        var decoded = new ArrayList<PublicKey>();
        for (int index = 0; index < keys.size(); index++) {
            var jwk = keys.getJSONObject(index);
            if (!"RSA".equals(jwk.getStr("kty")) || !"RS256".equals(jwk.getStr("alg")) || !"sig".equals(jwk.getStr("use")))
                throw new IllegalArgumentException("Unexpected JWT key type or use");
            var decoder = Base64.getUrlDecoder();
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, decoder.decode(jwk.getStr("n"))), new BigInteger(1, decoder.decode(jwk.getStr("e")))));
            if (!id(key).equals(jwk.getStr("kid"))) throw new IllegalArgumentException("JWT key id does not match its public key");
            decoded.add(key);
        }
        return new JwtVerificationKeys(decoded);
    }
}

package com.zhixuetang.auth.service.impl;

import com.zhixuetang.common.exceptions.UnauthorizedException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Encrypt refresh JWTs independently of signing-key rotation, using the existing private seed. */
@Component
public final class RefreshCookieCipher {
    private static final byte[] CONTEXT =
            "zhixuetang/auth/refresh-cookie/v1".getBytes(StandardCharsets.US_ASCII);
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RefreshCookieCipher(Environment environment) {
        this(environment.getProperty("zxt.auth.cookie.encryption-key",
                environment.getProperty("ZXT_INTERNAL_TOKEN", "")));
    }

    public RefreshCookieCipher(String seed) {
        if (seed.length() < 32)
            throw new IllegalArgumentException("Refresh cookie encryption requires a private seed of at least 32 characters");
        try {
            var derive = Mac.getInstance("HmacSHA256");
            derive.init(new SecretKeySpec(seed.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            key = new SecretKeySpec(derive.doFinal(CONTEXT), "AES");
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot initialize refresh cookie encryption", error);
        }
    }

    public String encrypt(String value) {
        // Leave room for the AEAD nonce/tag and Base64 within a browser cookie.
        if (value.length() > 2048)
            throw new IllegalArgumentException("Refresh token exceeds the encrypted cookie size budget");
        byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
        if (plaintext.length > 2048)
            throw new IllegalArgumentException("Refresh token exceeds the encrypted cookie size budget");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(CONTEXT);
            byte[] encrypted = cipher.doFinal(plaintext);
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(Math.addExact(iv.length, encrypted.length)).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot encrypt refresh cookie", error);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.length() > 4096 || !value.startsWith("v1."))
            throw new UnauthorizedException("登录状态已失效，请重新登录");
        try {
            byte[] payload = Base64.getUrlDecoder().decode(value.substring(3));
            if (payload.length < 29) throw new IllegalArgumentException();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, payload, 0, 12));
            cipher.updateAAD(CONTEXT);
            return new String(cipher.doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw new UnauthorizedException("登录状态已失效，请重新登录");
        }
    }
}

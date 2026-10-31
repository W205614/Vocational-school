package com.tianji.auth.security;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.tianji.auth.common.util.JwtVerificationKeys;
import com.tianji.auth.config.AuthConfig;
import com.tianji.auth.controller.JwkController;
import com.tianji.auth.service.impl.SessionStore;
import com.tianji.auth.util.JwtTool;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtKeyRotationTest {
    private KeyPair key() throws Exception { var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair(); }
    private LoginUserDTO user() {var user=new LoginUserDTO();user.setUserId(42L);user.setRoleId(2L);user.setAuthVersion(3L);user.setSessionId("rotation-session");return user;}

    @Test void overlapAcceptsOldAccessAndRefreshAndNewTokensIdentifyTheirKey() throws Exception {
        var old=key();var next=key();var sessions=mock(SessionStore.class);
        when(sessions.rotate(any(),any())).thenReturn(new SessionStore.Rotation("refresh-jti",Instant.now().plusSeconds(600)));
        var before=new JwtTool(old,sessions,new JwtVerificationKeys(List.of(old.getPublic())));
        var overlap=new JwtVerificationKeys(List.of(next.getPublic(),old.getPublic()));
        var after=new JwtTool(next,sessions,overlap);
        String oldAccess=before.createToken(user()),oldRefresh=before.createRefreshToken(user());
        assertEquals(JwtVerificationKeys.id(old.getPublic()),JWT.of(oldAccess).getHeader("kid"));
        assertTrue(overlap.verify(JWT.of(oldAccess)));
        assertEquals("rotation-session",after.parseRefreshToken(oldRefresh).getSessionId());
        assertEquals("refresh-jti",after.parseRefreshToken(oldRefresh).getRefreshJti());
        assertTrue(overlap.verify(JWT.of(after.createToken(user()))));
        var retired=new JwtTool(next,sessions,new JwtVerificationKeys(List.of(next.getPublic())));
        assertThrows(UnauthorizedException.class,()->retired.parseRefreshToken(oldRefresh));
        assertFalse(new JwtVerificationKeys(List.of(next.getPublic())).verify(JWT.of(oldAccess)));
    }

    @Test void legacyTokensRequireTrustedKeyAndUnknownKidOrWrongAlgorithmIsRejected() throws Exception {
        var pair=key();var trusted=new JwtVerificationKeys(List.of(pair.getPublic()));
        String legacy=JWT.create().setPayload("user",Map.of("userId",42)).setSigner(JWTSignerUtil.createSigner("rs256",pair)).sign();
        assertTrue(trusted.verify(JWT.of(legacy)));
        String wrongId=JWT.create().setHeader("kid","unknown").setSigner(JWTSignerUtil.createSigner("rs256",pair)).sign();
        assertFalse(trusted.verify(JWT.of(wrongId)));
        String hmac=JWT.create().setKey(pair.getPublic().getEncoded()).sign();
        assertFalse(trusted.verify(JWT.of(hmac)));
        String published=trusted.jwks();assertFalse(published.contains("private"));
        assertTrue(JwtVerificationKeys.parse(published).verify(JWT.of(legacy)));
        assertThrows(IllegalArgumentException.class,()->JwtVerificationKeys.parse(published.replace(JwtVerificationKeys.id(pair.getPublic()),"wrong")));
        assertThrows(IllegalArgumentException.class,()->new JwtVerificationKeys(List.of(pair.getPublic(),pair.getPublic())));
    }

    @Test void expiredRefreshStillFailsDuringKeyOverlap() throws Exception {
        var pair=key();var next=key();var sessions=mock(SessionStore.class);
        when(sessions.rotate(any(),any())).thenReturn(new SessionStore.Rotation("expired",Instant.now().minusSeconds(60)));
        var tool=new JwtTool(pair,sessions,new JwtVerificationKeys(List.of(pair.getPublic())));
        var overlap=new JwtTool(next,sessions,new JwtVerificationKeys(List.of(next.getPublic(),pair.getPublic())));
        assertThrows(UnauthorizedException.class,()->overlap.parseRefreshToken(tool.createRefreshToken(user())));
    }

    @Test void keystoreAllowlistPublishesOverlapWithoutChangingLegacyEndpoint(@TempDir Path directory) throws Exception {
        Path store=directory.resolve("signing.jks");
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        for(String alias:List.of("old","next")){
            var process=new ProcessBuilder(executable,"-genkeypair","-alias",alias,"-keyalg","RSA","-keysize","2048","-validity","1","-dname","CN=Rotation Fixture","-storetype","JKS","-keystore",store.toString(),"-storepass","rotation-fixture-pass","-keypass","rotation-fixture-pass").redirectErrorStream(true).start();
            String output=new String(process.getInputStream().readAllBytes());assertEquals(0,process.waitFor(),output);
        }
        var properties=new KeyProperties();var settings=properties.getKeyStore();settings.setLocation(new FileSystemResource(store));settings.setPassword("rotation-fixture-pass");settings.setSecret("rotation-fixture-pass");settings.setAlias("old");
        var config=new AuthConfig();var active=config.keyPair(properties);
        var keys=config.jwtVerificationKeys(active,properties,new MockEnvironment().withProperty("tj.auth.jwt.verification-aliases","next"));
        var endpoint=new JwkController(active,keys);
        assertEquals(Base64.getEncoder().encodeToString(active.getPublic().getEncoded()),endpoint.getJwk("legacy"));
        assertTrue(JwtVerificationKeys.parse(endpoint.getJwk("jwks")).verify(JWT.of(JWT.create().setSigner(JWTSignerUtil.createSigner("rs256",active)).sign())));
        assertThrows(IllegalStateException.class,()->config.jwtVerificationKeys(active,properties,new MockEnvironment().withProperty("tj.auth.jwt.verification-aliases","missing")));
    }
}

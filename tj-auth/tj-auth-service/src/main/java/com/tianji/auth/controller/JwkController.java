package com.tianji.auth.controller;

import cn.hutool.core.codec.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;

import java.security.KeyPair;
import com.tianji.auth.common.util.JwtVerificationKeys;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("jwks")
@Hidden
public class JwkController {

    private final KeyPair keyPair;
    private final JwtVerificationKeys verificationKeys;

    @Autowired
    public JwkController(KeyPair keyPair, JwtVerificationKeys verificationKeys) {
        this.keyPair = keyPair;
        this.verificationKeys = verificationKeys;
    }

    @GetMapping
    public String getJwk(@RequestParam(defaultValue="legacy") String format){
        if ("jwks".equals(format)) return verificationKeys.jwks();
        if (!"legacy".equals(format)) throw new com.tianji.common.exceptions.BadRequestException("不支持的公钥格式");
        // The default preserves the deployed SDK contract during a receiver-first upgrade.
        return Base64.encode(keyPair.getPublic().getEncoded());
    }
}

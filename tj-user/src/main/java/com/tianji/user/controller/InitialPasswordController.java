package com.tianji.user.controller;
import com.tianji.api.dto.user.InitialPasswordDTO;
import com.tianji.common.utils.InternalAuth;
import com.tianji.user.service.impl.TemporaryCredentialService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
@RestController
public class InitialPasswordController {
 private final TemporaryCredentialService credentials;
 public InitialPasswordController(TemporaryCredentialService credentials){this.credentials=credentials;}
 @PostMapping("/internal/v2/users/password/initial") public void complete(@Valid @RequestBody InitialPasswordDTO form){InternalAuth.requireService();credentials.complete(form);}
}

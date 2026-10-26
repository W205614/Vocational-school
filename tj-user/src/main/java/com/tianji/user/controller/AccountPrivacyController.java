package com.tianji.user.controller;

import com.tianji.user.service.impl.AccountPrivacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/account-privacy")
public class AccountPrivacyController {
    private final AccountPrivacyService privacy;
    public record Credential(String password,String confirmation){@Override public String toString(){return "Credential[REDACTED]";}}
    public record Consent(String policyVersion){}
    @PostMapping("/export") public ResponseEntity<?> export(@RequestBody Credential form){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(privacy.export(form.password()));}
    @PostMapping("/deletion") public ResponseEntity<?> deletion(@RequestBody Credential form){return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(privacy.deactivate(form.password(),form.confirmation()));}
    @PostMapping("/consent") public void consent(@RequestBody Consent form){privacy.consent(form.policyVersion());}
    @GetMapping("/requests") public ResponseEntity<?> pending(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(privacy.pending());}
    @PutMapping("/requests/{id}") public void resolve(@PathVariable String id,@RequestBody AccountPrivacyService.Resolution form){privacy.resolve(id,form);}
}

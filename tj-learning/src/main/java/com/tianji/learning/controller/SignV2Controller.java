package com.tianji.learning.controller;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.impl.SignRecordServiceImpl.Request;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/sign-ins")
public class SignV2Controller {
    private final OperationStore operations;
    private final ISignRecordService signs;
    @PostMapping public ResponseEntity<OperationStore.View> sign(@RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"SIGN_IN",key,new Request(LocalDate.now())));
    }
    @GetMapping public Object list() {return signs.querySignRecords();}
}

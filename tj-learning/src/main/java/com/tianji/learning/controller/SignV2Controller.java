package com.tianji.learning.controller;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.impl.SignRecordServiceImpl.Request;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/v2/sign-ins")
public class SignV2Controller {
    private final OperationStore operations;
    private final ISignRecordService signs;
    private final java.time.Clock clock;
    public SignV2Controller(OperationStore operations,ISignRecordService signs,@org.springframework.beans.factory.annotation.Qualifier("learningClock") java.time.Clock clock){this.operations=operations;this.signs=signs;this.clock=clock;}
    @PostMapping public ResponseEntity<OperationStore.View> sign(@RequestHeader("Idempotency-Key") String key) {
        var now=java.time.LocalDateTime.now(clock);
        return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"SIGN_IN",key,java.util.Map.of("action","SIGN_IN"),new Request(now.toLocalDate(),now)));
    }
    @GetMapping public Object list() {return signs.querySignRecords();}
}

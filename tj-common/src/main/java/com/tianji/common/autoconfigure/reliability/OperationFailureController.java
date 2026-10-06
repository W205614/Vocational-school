package com.tianji.common.autoconfigure.reliability;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/admin/operation-failures")
public class OperationFailureController {
 private final OperationStore operations;
 @GetMapping public Object list(){UserContext.requireAdmin();return operations.failures();}
 @PostMapping("/{id}/replay") public void replay(@PathVariable String id,@RequestParam long version){UserContext.requireAdmin();operations.replayFailure(id,version);}
}

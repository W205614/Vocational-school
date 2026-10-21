package com.tianji.common.autoconfigure.reliability;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/admin/operation-failures")
public class OperationFailureController {
 private final OperationStore operations;
 @GetMapping public Object list(){UserContext.requireAdmin();return operations.failures();}
 @GetMapping("/page") public FailurePage page(@RequestParam(defaultValue="1")int pageNo,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String identifier,@RequestParam(required=false)String errorCode){UserContext.requireAdmin();return operations.failurePage(pageNo,pageSize,identifier,errorCode);}
 @PostMapping("/{id}/replay") public void replay(@PathVariable String id,@RequestParam long version){UserContext.requireAdmin();operations.replayFailure(id,version);}
}

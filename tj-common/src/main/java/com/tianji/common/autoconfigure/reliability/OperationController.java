package com.tianji.common.autoconfigure.reliability;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v2/operations")
public class OperationController {
    private final OperationStore store;
    public OperationController(OperationStore store) { this.store=store; }
    @GetMapping("/{id}") public OperationStore.View get(@PathVariable String id) {
        Long user=UserContext.getUser();
        if(user==null) throw new UnauthorizedException("请先登录");
        return store.get(id,user);
    }
}

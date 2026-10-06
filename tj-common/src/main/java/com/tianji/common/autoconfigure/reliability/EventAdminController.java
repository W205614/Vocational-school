package com.tianji.common.autoconfigure.reliability;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.ConflictException;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v2/admin/events")
public class EventAdminController {
    private final OutboxStore store;
    public EventAdminController(OutboxStore store) {this.store=store;}
    @GetMapping("/failures") public List<Map<String,Object>> failures(@RequestParam(defaultValue="20") int limit) {UserContext.requireAdmin();return store.failures(limit);}
    @PostMapping("/{id}/replay") public void replay(@PathVariable String id) {UserContext.requireAdmin();if(store.replay(id)!=1) throw new ConflictException("事件不是可重放的失败状态");}
}

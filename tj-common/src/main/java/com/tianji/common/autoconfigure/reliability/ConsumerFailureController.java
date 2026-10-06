package com.tianji.common.autoconfigure.reliability;
import com.tianji.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v2/admin/consumer-failures")
public class ConsumerFailureController {
 private final ConsumerFailureStore store;
 public ConsumerFailureController(ConsumerFailureStore store){this.store=store;}
 @GetMapping public List<Map<String,Object>> list(@RequestParam(defaultValue="20")int limit){UserContext.requireAdmin();return store.failures(limit);}
 @PostMapping("/{id}/replay") public void replay(@PathVariable String id,@RequestParam long version){UserContext.requireAdmin();store.replay(id,version);}
}

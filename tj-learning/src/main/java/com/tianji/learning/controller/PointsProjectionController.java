package com.tianji.learning.controller;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/admin/points-projections")
public class PointsProjectionController {
 private final JdbcTemplate jdbc;
 @GetMapping public List<Map<String,Object>> status(){UserContext.requireAdmin();return jdbc.queryForList("SELECT board_month,COUNT(*) users,SUM(version>processed_version) pending FROM points_projection GROUP BY board_month ORDER BY board_month DESC LIMIT 100");}
 @PostMapping("/{month}/rebuild") public void rebuild(@PathVariable String month){UserContext.requireAdmin();if(!month.matches("[0-9]{6}"))throw new BadRequestException("月份无效");jdbc.update("UPDATE points_projection SET processed_version=-1 WHERE board_month=?",month);}
}

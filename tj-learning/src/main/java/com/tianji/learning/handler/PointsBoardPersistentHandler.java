package com.tianji.learning.handler;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.service.*;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
@Component @RequiredArgsConstructor
public class PointsBoardPersistentHandler {
 private final IPointsBoardSeasonService seasons;private final IPointsBoardService boards;private final JdbcTemplate jdbc;
 @XxlJob("createTableJob") public void createPointsBoardTableOfLastSeason(){Integer season=seasons.querySeasonByTime(LocalDateTime.now().minusMonths(1));if(season!=null)boards.createPointsBoardTableBySeason(season);}
 @XxlJob("savePointsBoard2DB") public void savePointsBoard2DB(){
  var time=LocalDateTime.now().minusMonths(1);Integer season=seasons.querySeasonByTime(time);if(season==null)return;
  boards.createPointsBoardTableBySeason(season);String table="points_board_"+season;
  String key=RedisConstants.POINTS_BOARD_KEY_PREFIX+time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
  int shards=Math.max(1,XxlJobHelper.getShardTotal()),page=Math.max(0,XxlJobHelper.getShardIndex())+1;
  for(;;page+=shards){var batch=boards.queryCurrentBoardList(key,page,100);if(batch.isEmpty())break;
   for(var row:batch)jdbc.update("INSERT INTO "+table+"(id,user_id,points) VALUES(?,?,?) ON DUPLICATE KEY UPDATE id=VALUES(id),points=VALUES(points)",row.getRank(),row.getUserId(),row.getPoints());
  }
 }
 @XxlJob("cleanPointsBoardFromRedis") public void cleanPointsBoardFromRedis(){/* Durable projections are retained for rebuild and history. */}
}

package com.tianji.learning.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.*;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Service @RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper,PointsBoard> implements IPointsBoardService {
 private final JdbcTemplate jdbc;private final UserClient users;
 private final LearningSeasonService seasons;
 private java.time.Clock clock=java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai"));
 @org.springframework.beans.factory.annotation.Autowired public void setClock(@org.springframework.beans.factory.annotation.Qualifier("learningClock") java.time.Clock clock){this.clock=clock;}
 private static final DateTimeFormatter MONTH=DateTimeFormatter.ofPattern("yyyyMM");
 @Override public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query){
  query.validate();long user=UserContext.requireUser();
  String begin=LocalDate.now(clock).format(MONTH),end=begin;
  if(query.getSeason()!=null && query.getSeason()!=0){
   var rows=jdbc.queryForList("SELECT begin_time,end_time FROM points_board_season WHERE id=?",query.getSeason());
   if(rows.isEmpty())throw new BadRequestException("赛季不存在");
   begin=LocalDate.parse(rows.getFirst().get("begin_time").toString()).format(MONTH);
   end=LocalDate.parse(rows.getFirst().get("end_time").toString()).format(MONTH);
  }
  if(begin.equals(end)) {
   LearningSeasonService.ensureSeason(jdbc,LearningSeasonService.parseMonth(begin));var board=seasons.board(begin,query);
   var result=new PointsBoardVO();result.setMonth(begin);result.setStatus(board.get("status").toString());result.setRank(((Number)board.get("myRank")).intValue());result.setPoints(((Number)board.get("myPoints")).intValue());result.setTotal(((Number)board.get("total")).longValue());result.setGapToPrevious(((Number)board.get("gapToPrevious")).intValue());
   List<PointsBoardItemVO> items=new ArrayList<>();for(Object value:(List<?>)board.get("list")){Map<?,?> row=(Map<?,?>)value;var item=new PointsBoardItemVO();item.setName(row.get("name").toString());item.setRank(((Number)row.get("rank")).intValue());item.setPoints(((Number)row.get("points")).intValue());items.add(item);}result.setBoardList(items);return result;
  }
  String ranked="SELECT user_id,SUM(points) points,ROW_NUMBER() OVER(ORDER BY SUM(points) DESC,MAX(reached_at) ASC,user_id ASC) rank_no FROM points_projection WHERE board_month BETWEEN ? AND ? GROUP BY user_id";
  var mine=jdbc.queryForList("SELECT * FROM ("+ranked+") ranked WHERE user_id=?",begin,end,user);
  var result=new PointsBoardVO();result.setPoints(mine.isEmpty()?0:((Number)mine.getFirst().get("points")).intValue());result.setRank(mine.isEmpty()?0:((Number)mine.getFirst().get("rank_no")).intValue());
  var rows=jdbc.queryForList("SELECT * FROM ("+ranked+") ranked ORDER BY rank_no LIMIT ? OFFSET ?",begin,end,query.getPageSize(),query.from());
  Map<Long,String> names=new HashMap<>();
  if(!rows.isEmpty()){var ids=rows.stream().map(r->((Number)r.get("user_id")).longValue()).toList();var details=users.queryUserByIds(ids);if(details!=null)details.forEach(u->names.put(u.getId(),u.getName()));}
  List<PointsBoardItemVO> items=new ArrayList<>();
  for(var row:rows){long id=((Number)row.get("user_id")).longValue();var item=new PointsBoardItemVO();item.setName(names.getOrDefault(id,"用户 "+id));item.setPoints(((Number)row.get("points")).intValue());item.setRank(((Number)row.get("rank_no")).intValue());items.add(item);}
  result.setBoardList(items);result.setStatus("LEGACY_READONLY");result.setLegacyReadonly(true);return result;
 }
 @Override public void createPointsBoardTableBySeason(Integer season){
  if(season==null || season<1)throw new BadRequestException("赛季无效");
  Integer ready=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,"points_board_"+season);
  if(ready==null || ready!=1)throw new com.tianji.common.exceptions.ServiceUnavailableException("赛季归档表尚未初始化，请由迁移流程创建");
 }
 @Override public List<PointsBoard> queryCurrentBoardList(String key,Integer pageNo,Integer pageSize){
  if(pageNo==null || pageNo<1 || pageSize==null || pageSize<1 || pageSize>1000)throw new BadRequestException("榜单分页无效");
  if(key==null || !key.matches(".*[0-9]{6}$"))throw new BadRequestException("榜单月份无效");
  String month=key.substring(key.length()-6);
   long offset=(long)(pageNo-1)*pageSize;
   return jdbc.query("SELECT user_id,points FROM points_projection WHERE board_month=? ORDER BY points DESC,reached_at ASC,user_id ASC LIMIT ? OFFSET ?",(rs,n)->new PointsBoard().setUserId(rs.getLong("user_id")).setPoints(rs.getInt("points")).setRank(Math.toIntExact(offset+n+1)),month,pageSize,offset);
 }
}

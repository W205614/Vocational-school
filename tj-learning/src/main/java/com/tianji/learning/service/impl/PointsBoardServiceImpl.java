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
 private static final DateTimeFormatter MONTH=DateTimeFormatter.ofPattern("yyyyMM");
 @Override public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query){
  query.validate();long user=UserContext.requireUser();
  String begin=LocalDate.now().format(MONTH),end=begin;
  if(query.getSeason()!=null && query.getSeason()!=0){
   var rows=jdbc.queryForList("SELECT begin_time,end_time FROM points_board_season WHERE id=?",query.getSeason());
   if(rows.isEmpty())throw new BadRequestException("赛季不存在");
   begin=LocalDate.parse(rows.getFirst().get("begin_time").toString()).format(MONTH);
   end=LocalDate.parse(rows.getFirst().get("end_time").toString()).format(MONTH);
  }
  String ranked="SELECT user_id,SUM(points) points,ROW_NUMBER() OVER(ORDER BY SUM(points) DESC,CAST(user_id AS CHAR) DESC) rank_no FROM points_projection WHERE board_month BETWEEN ? AND ? GROUP BY user_id";
  var mine=jdbc.queryForList("SELECT * FROM ("+ranked+") ranked WHERE user_id=?",begin,end,user);
  var result=new PointsBoardVO();result.setPoints(mine.isEmpty()?0:((Number)mine.getFirst().get("points")).intValue());result.setRank(mine.isEmpty()?0:((Number)mine.getFirst().get("rank_no")).intValue());
  var rows=jdbc.queryForList("SELECT * FROM ("+ranked+") ranked ORDER BY rank_no LIMIT ? OFFSET ?",begin,end,query.getPageSize(),query.from());
  Map<Long,String> names=new HashMap<>();
  if(!rows.isEmpty()){var ids=rows.stream().map(r->((Number)r.get("user_id")).longValue()).toList();var details=users.queryUserByIds(ids);if(details!=null)details.forEach(u->names.put(u.getId(),u.getName()));}
  List<PointsBoardItemVO> items=new ArrayList<>();
  for(var row:rows){long id=((Number)row.get("user_id")).longValue();var item=new PointsBoardItemVO();item.setName(names.getOrDefault(id,"用户 "+id));item.setPoints(((Number)row.get("points")).intValue());item.setRank(((Number)row.get("rank_no")).intValue());items.add(item);}
  result.setBoardList(items);return result;
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
  return jdbc.query("SELECT user_id,points,ROW_NUMBER() OVER(ORDER BY points DESC,CAST(user_id AS CHAR) DESC) rank_no FROM points_projection WHERE board_month=? ORDER BY rank_no LIMIT ? OFFSET ?",(rs,n)->new PointsBoard().setUserId(rs.getLong("user_id")).setPoints(rs.getInt("points")).setRank(rs.getInt("rank_no")),month,pageSize,(long)(pageNo-1)*pageSize);
 }
}

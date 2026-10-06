package com.tianji.common.reliability;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PageQueryTest {
 @Test void rejectsUnboundedAndOverflowingPages() {
  assertThrows(BadRequestException.class,()->new PageQuery().setPageSize(101).from());
  assertThrows(BadRequestException.class,()->new PageQuery().setPageNo(Integer.MAX_VALUE).setPageSize(100).from());
  assertThrows(BadRequestException.class,()->new PageQuery().setPageSize(0).from());
 }
 @Test void acceptsOnlyEndpointSorts() {
  assertThrows(BadRequestException.class,()->new PageQuery().setSortBy("id desc; drop table user").toMpPage("create_time",false));
  assertThrows(BadRequestException.class,()->new PageQuery().setSortBy("password").toMpPage("create_time",false));
  assertEquals("create_time",new PageQuery().toMpPage("create_time",false).orders().getFirst().getColumn());
 }
}

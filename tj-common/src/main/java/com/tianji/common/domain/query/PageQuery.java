package com.tianji.common.domain.query;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.constants.Constant;
import com.tianji.common.exceptions.BadRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import jakarta.validation.constraints.*;
import java.util.*;
@Data
@Schema(description="分页请求参数")
@Accessors(chain=true)
public class PageQuery {
    public static final Integer DEFAULT_PAGE_SIZE=20,DEFAULT_PAGE_NUM=1;
    @Min(1) private Integer pageNo=DEFAULT_PAGE_NUM;
    @Min(1) @Max(100) private Integer pageSize=DEFAULT_PAGE_SIZE;
    private Boolean isAsc=true;
    private String sortBy;
    public void validate() {
        if(pageNo==null || pageNo<1 || pageSize==null || pageSize<1 || pageSize>100 ||
                (long)(pageNo-1)*pageSize>Integer.MAX_VALUE) throw new BadRequestException("分页参数无效，每页最多 100 条");
    }
    public void validateSort(Set<String> allowed) {
        if(sortBy!=null && !sortBy.isBlank() && !allowed.contains(sortBy)) throw new BadRequestException("不支持该排序字段");
    }
    public int from() {validate();return (pageNo-1)*pageSize;}
    public <T> Page<T> toMpPage(OrderItem... items) {
        validate();
        Page<T> page=new Page<>(pageNo,pageSize);
        Set<String> allowed=new HashSet<>();
        if(items!=null) for(OrderItem item:items) allowed.add(item.getColumn());
        if(allowed.isEmpty()) allowed=Set.of("id","create_time","update_time");
        validateSort(allowed);
        if(sortBy!=null && !sortBy.isBlank()) page.addOrder(new OrderItem().setColumn(sortBy).setAsc(Boolean.TRUE.equals(isAsc)));
        else if(items!=null) for(OrderItem item:items) page.addOrder(item);
        return page;
    }
    public <T> Page<T> toMpPage(String defaultSortBy,boolean ascending) {
        validate();validateSort(Set.of(defaultSortBy));
        return new Page<T>(pageNo,pageSize).addOrder(new OrderItem().setColumn(sortBy==null || sortBy.isBlank()?defaultSortBy:sortBy).setAsc(sortBy==null || sortBy.isBlank()?ascending:Boolean.TRUE.equals(isAsc)));
    }
    public <T> Page<T> toMpPageDefaultSortByCreateTimeDesc() {return toMpPage(Constant.DATA_FIELD_NAME_CREATE_TIME,false);}
}

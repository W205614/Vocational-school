package com.tianji.api.dto.remark;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class LikeTimesDTO {
    private Long bizId;
    private Integer likeTimes;
    private Long version;
    public static LikeTimesDTO of(Long id,Integer count) {return new LikeTimesDTO(id,count,null);}
    public static LikeTimesDTO of(Long id,Integer count,Long version) {return new LikeTimesDTO(id,count,version);}
}

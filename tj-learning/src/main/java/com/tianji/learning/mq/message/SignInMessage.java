package com.tianji.learning.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SignInMessage {
    private Long userId;
    private Integer points;
    private java.time.LocalDateTime occurredAt;
    public static SignInMessage of(Long userId,Integer points){return of(userId,points,null);}
}

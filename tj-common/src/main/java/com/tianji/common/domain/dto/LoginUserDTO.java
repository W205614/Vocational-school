package com.tianji.common.domain.dto;

import lombok.Data;

@Data
public class LoginUserDTO {
    private Long userId;
    private Long roleId;
    private Boolean rememberMe;
    private String sessionId;
    private Long authVersion;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String refreshJti;
}

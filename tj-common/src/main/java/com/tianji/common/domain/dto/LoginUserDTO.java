package com.tianji.common.domain.dto;

import lombok.Data;

@Data
public class LoginUserDTO {
    private Long userId;
    private Long roleId;
    /** Account category from the identity source; configurable role IDs are not account types. */
    private Integer userType;
    private Boolean rememberMe;
    private String sessionId;
    private Long authVersion;
    private Boolean mustChangePassword;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String refreshJti;
}

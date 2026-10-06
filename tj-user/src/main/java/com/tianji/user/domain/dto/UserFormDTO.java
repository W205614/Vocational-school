package com.tianji.user.domain.dto;

import com.tianji.api.dto.user.UserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotNull;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "修改用户信息的表单，带有密码")
public class UserFormDTO extends UserDTO {
    @Schema(description = "原始密码", example = "123321")
    private String oldPassword;
    @Schema(description = "新密码", example = "123321")
    private String password;
}

package com.tianji.api.dto.user;
import jakarta.validation.constraints.*;
/** Credentials are intentionally excluded from toString and logs. */
public record InitialPasswordDTO(@NotBlank @Size(max=100) String username,
 @NotBlank @Size(max=72) String oldPassword,@NotBlank @Size(min=8,max=72) String newPassword,
 @NotBlank @Pattern(regexp="student|admin") String audience) {
 @Override public String toString(){return "InitialPasswordDTO[credentials=REDACTED]";}
}

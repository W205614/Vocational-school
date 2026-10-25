package com.tianji.api.dto.user;
import java.time.LocalDateTime;
/** Returned once to an administrator; never persisted in plaintext. */
public record TemporaryCredentialDTO(String userId,String username,String temporaryPassword,LocalDateTime expiresAt) {
 @Override public String toString(){return "TemporaryCredentialDTO[userId="+userId+",credentials=REDACTED]";}
}

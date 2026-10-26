package com.tianji.auth.security;
import com.tianji.auth.service.impl.*;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdministratorMfaServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SessionStore sessions=mock(SessionStore.class);
    private final String key=Base64.getEncoder().encodeToString(new byte[32]);
    private AdministratorMfaService service(MockEnvironment env){var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));return new AdministratorMfaService(jdbc,new TransactionTemplate(manager),sessions,env);}
    private LoginUserDTO administrator(){var user=new LoginUserDTO();user.setUserId(42L);user.setRoleId(1L);return user;}
    @Test void requiredMfaCannotStartWithoutAnExternalKey(){assertThrows(IllegalStateException.class,()->service(new MockEnvironment().withProperty("tj.auth.mfa.required","true")));}
    @Test void unconfiguredLocalDemoDoesNotQueryNewTable(){assertFalse(service(new MockEnvironment()).verifyLogin(administrator(),null));verifyNoInteractions(jdbc,sessions);}
    @Test void productionRejectsAdministratorWithoutConfirmedEnrollment(){
        when(jdbc.queryForList(anyString(),eq(42L))).thenReturn(List.of());
        assertThrows(ForbiddenException.class,()->service(new MockEnvironment().withProperty("tj.auth.mfa.required","true").withProperty("TJ_MFA_ENCRYPTION_KEY",key)).verifyLogin(administrator(),"123456"));verifyNoInteractions(sessions);
    }
    @Test void replayedTimeStepCannotIssueAnotherLogin(){
        String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";long now=1234567890L,step=now/30;
        String cipher=new TotpSecrets(key).encrypt(42,secret);
        when(jdbc.queryForList(anyString(),eq(42L))).thenReturn(List.of(Map.of("enabled",1,"epoch",now,"secret_ciphertext",cipher,"last_step",step)));
        assertThrows(UnauthorizedException.class,()->service(new MockEnvironment().withProperty("tj.auth.mfa.required","true").withProperty("TJ_MFA_ENCRYPTION_KEY",key)).verifyLogin(administrator(),TotpSecrets.code(secret,step,6)));
        verify(jdbc,never()).update(anyString(),anyLong(),anyLong());
    }
    @Test void enrolledCodeAdvancesPersistentStep(){
        String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";long now=1234567890L,step=now/30;
        when(jdbc.queryForList(anyString(),eq(42L))).thenReturn(List.of(Map.of("enabled",1,"epoch",now,"secret_ciphertext",new TotpSecrets(key).encrypt(42,secret),"last_step",step-1)));
        assertTrue(service(new MockEnvironment().withProperty("TJ_MFA_ENCRYPTION_KEY",key)).verifyLogin(administrator(),TotpSecrets.code(secret,step,6)));
        verify(jdbc).update("UPDATE auth_mfa SET last_step=? WHERE user_id=?",step,42L);
    }
}

package com.tianji.user.reliability;
import com.tianji.user.service.impl.*;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PublicAccountContractTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final PasswordEncoder passwords=mock(PasswordEncoder.class);
    private final AccountVerificationService proof=mock(AccountVerificationService.class);
    private PublicAccountService service(boolean enabled){
        var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new PublicAccountService(jdbc,new TransactionTemplate(manager),passwords,proof,new MockEnvironment().withProperty("tj.accounts.public.enabled",Boolean.toString(enabled)).withProperty("tj.accounts.public.sms-delivery-mode","provider"));
    }
    @Test void publicFlowsAreClosedUntilExplicitlyConfigured(){
        assertThrows(ServiceUnavailableException.class,()->service(false).requestCode("13800010001","REGISTER","127.0.0.1"));verifyNoInteractions(proof,jdbc);
    }
    @Test void registrationRequiresCurrentExplicitConsentBeforeConsumingCode(){
        assertThrows(BadRequestException.class,()->service(true).register(new PublicAccountService.Registration("13800010001","123456","fixture-password","old",true)));
        assertThrows(BadRequestException.class,()->service(true).register(new PublicAccountService.Registration("13800010001","123456","fixture-password","2026-10-26",false)));
        verifyNoInteractions(proof,jdbc);
    }
    @Test void passwordAndPurposeValidationRejectInvalidInput(){
        assertThrows(BadRequestException.class,()->PublicAccountService.validatePassword("tiny"));
        assertThrows(BadRequestException.class,()->PublicAccountService.validatePassword("密".repeat(25)));
        assertThrows(BadRequestException.class,()->AccountVerificationService.validate("13800010001",null));
        assertThrows(BadRequestException.class,()->AccountVerificationService.validate("not-phone","REGISTER"));
        assertThrows(BadRequestException.class,()->AccountVerificationService.validate("13800010001","ADMIN_RESET"));
    }
    @Test void recoveryOnlyConsumesRecoveryProofAndNeverReactivatesFrozenAccounts(){
        when(passwords.encode("fixture-new-password")).thenReturn("fixture-hash");
        service(true).recover(new PublicAccountService.Recovery("13800010001","123456","fixture-new-password"));
        verify(proof).consume("13800010001","RECOVER","123456");
        verify(jdbc).update("UPDATE `user` SET password=?,must_change_password=0,temporary_password_expires_at=NULL WHERE cell_phone=? AND type=2 AND status=1","fixture-hash","13800010001");
    }
    @Test void credentialsAreRedactedFromDiagnosticStrings(){
        assertFalse(new PublicAccountService.Registration("13800010001","123456","secret-password","v",true).toString().contains("secret"));
        assertFalse(new PublicAccountService.Recovery("13800010001","123456","secret-password").toString().contains("secret"));
    }
}

package com.tianji.auth.security;

import com.tianji.auth.maintenance.MfaSecretMaintenance;
import com.tianji.auth.service.impl.AdministratorMfaService;
import com.tianji.auth.service.impl.SessionStore;
import com.tianji.auth.service.impl.TotpSecrets;
import com.tianji.common.domain.dto.LoginUserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class MfaSecretRotationDatabaseTest {
    private static final String SECRET="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String LEGACY="AAECAwQFBgcICQoLz45pFkezza+FIWGxrEyUXRMp8t74082giJLXX9x6RT5tDEhdAaMfZaO8NLnmxiqV";
    private final String oldKey=Base64.getEncoder().encodeToString(new byte[32]);
    private final String newKey=encodedKey((byte)7);
    private final TotpSecrets previous=new TotpSecrets(oldKey);
    private final TotpSecrets rotated=new TotpSecrets(newKey,Map.of(previous.activeKeyId(),oldKey),previous.activeKeyId());
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;

    private static String encodedKey(byte value){byte[] bytes=new byte[32];Arrays.fill(bytes,value);return Base64.getEncoder().encodeToString(bytes);}

    @BeforeEach void setup()throws Exception{
        String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
        String options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        String password=System.getenv("ACCEPTANCE_DB_PASSWORD");
        new JdbcTemplate(new DriverManagerDataSource(base+options,"root",password)).execute("CREATE DATABASE IF NOT EXISTS acceptance_mfa_rotation");
        var source=new DriverManagerDataSource(base+"acceptance_mfa_rotation"+options,"root",password);jdbc=new JdbcTemplate(source);
        assertEquals("acceptance_mfa_rotation",jdbc.queryForObject("SELECT DATABASE()",String.class));
        Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("deploy/acceptance/migrations")))root=root.getParent();
        for(String statement:Files.readString(root.resolve("deploy/acceptance/migrations/tj_auth/V003__sessions.sql")).split(";"))if(!statement.isBlank())jdbc.execute(statement.replace("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
        for(String statement:Files.readString(root.resolve("deploy/acceptance/migrations/tj_auth/V005__administrator_mfa.sql")).split(";"))if(!statement.isBlank()){
            if(statement.contains("ALTER TABLE") && jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='auth_session' AND column_name='mfa_verified'",Integer.class)>0)continue;
            jdbc.execute(statement.replace("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
        }
        transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update("DELETE FROM auth_session");jdbc.update("DELETE FROM auth_mfa");
    }

    private void insert(long user,String cipher,int enabled,long step){
        jdbc.update("INSERT INTO auth_mfa(user_id,secret_ciphertext,enabled,pending_until,last_step) VALUES(?,?,?,NOW(3)+INTERVAL 10 MINUTE,?)",user,cipher,enabled,step);
    }
    private List<Map<String,Object>> state(){return jdbc.queryForList("SELECT user_id,enabled,pending_until,last_step,created_at,updated_at FROM auth_mfa ORDER BY user_id");}

    @Test void atomicReencryptionPreservesEnrollmentAndReplayStateAndIsRepeatable(){
        insert(42,LEGACY,0,-1);insert(43,previous.encrypt(43,SECRET),1,123);insert(44,rotated.encrypt(44,SECRET),1,456);
        var before=state();var result=MfaSecretMaintenance.reencrypt(jdbc,transaction,rotated);
        assertEquals(3,result.examined());assertEquals(2,result.reencrypted());assertEquals(before,state());
        var currentOnly=new TotpSecrets(newKey);
        for(var row:jdbc.queryForList("SELECT user_id,secret_ciphertext FROM auth_mfa"))assertEquals(SECRET,currentOnly.decrypt(((Number)row.get("user_id")).longValue(),row.get("secret_ciphertext").toString()));
        var after=jdbc.queryForList("SELECT * FROM auth_mfa ORDER BY user_id");
        assertEquals(0,MfaSecretMaintenance.reencrypt(jdbc,transaction,rotated).reencrypted());assertEquals(after,jdbc.queryForList("SELECT * FROM auth_mfa ORDER BY user_id"));
    }

    @Test void anUnknownOrWrongKeyRollsBackEarlierRowReencryption(){
        insert(42,LEGACY,0,-1);insert(43,new TotpSecrets(encodedKey((byte)9)).encrypt(43,SECRET),1,123);
        var before=jdbc.queryForList("SELECT * FROM auth_mfa ORDER BY user_id");
        assertThrows(IllegalStateException.class,()->MfaSecretMaintenance.reencrypt(jdbc,transaction,rotated));
        assertEquals(before,jdbc.queryForList("SELECT * FROM auth_mfa ORDER BY user_id"));
        assertThrows(IllegalStateException.class,()->MfaSecretMaintenance.reencrypt(jdbc,transaction,new TotpSecrets(newKey)));
        assertEquals(before,jdbc.queryForList("SELECT * FROM auth_mfa ORDER BY user_id"));
    }

    @Test void pendingConfirmationAndEnabledLoginStillWorkAfterRetiringTheOldKey(){
        long step=jdbc.queryForObject("SELECT FLOOR(UNIX_TIMESTAMP(NOW())/30)",Long.class);
        insert(42,LEGACY,0,-1);insert(43,previous.encrypt(43,SECRET),1,step-1);
        MfaSecretMaintenance.reencrypt(jdbc,transaction,rotated);
        var sessions=new SessionStore(jdbc,transaction);
        var service=new AdministratorMfaService(jdbc,transaction,sessions,new MockEnvironment().withProperty("tj.auth.mfa.required","true").withProperty("TJ_MFA_ENCRYPTION_KEY",newKey));
        service.confirm(42,TotpSecrets.code(SECRET,step,6));
        var identity=new LoginUserDTO();identity.setUserId(43L);identity.setRoleId(1L);
        assertTrue(service.verifyLogin(identity,TotpSecrets.code(SECRET,step,6)));
        assertEquals(1,jdbc.queryForObject("SELECT enabled FROM auth_mfa WHERE user_id=42",Integer.class));
        assertEquals(step,jdbc.queryForObject("SELECT last_step FROM auth_mfa WHERE user_id=43",Long.class));
    }
}

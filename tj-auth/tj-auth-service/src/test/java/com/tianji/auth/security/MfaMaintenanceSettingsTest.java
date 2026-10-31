package com.tianji.auth.security;

import com.tianji.auth.maintenance.MfaSecretMaintenance.Settings;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MfaMaintenanceSettingsTest {
    private Settings settings(String environment,String host,String database,String username){
        return new Settings("tianji-mfa-maintenance-v1",environment,host,3306,database,username,"fixture-password",
                Base64.getEncoder().encodeToString(new byte[32]),Map.of(),null,null,null);
    }
    @Test void onlyExplicitIsolatedTargetsAreAccepted(){
        assertTrue(settings("isolated-test","127.0.0.1","acceptance_mfa_rotation","root").testEnvironment("127.0.0.1","acceptance_mfa_rotation"));
        assertFalse(settings("isolated-production-candidate","candidate-db.example.invalid","tj_auth","mfa_maintenance").testEnvironment("candidate-db.example.invalid","tj_auth"));
        assertThrows(IllegalArgumentException.class,()->settings("isolated-test","127.0.0.1","tj_auth","root").testEnvironment("127.0.0.1","tj_auth"));
        assertThrows(IllegalArgumentException.class,()->settings("isolated-test","candidate-db.example.invalid","acceptance_mfa_rotation","root").testEnvironment("candidate-db.example.invalid","acceptance_mfa_rotation"));
        assertThrows(IllegalArgumentException.class,()->settings("isolated-production-candidate","127.0.0.1","tj_auth","mfa_maintenance").testEnvironment("127.0.0.1","tj_auth"));
        assertThrows(IllegalArgumentException.class,()->settings("isolated-production-candidate","candidate-db.example.invalid","tj_auth","root").testEnvironment("candidate-db.example.invalid","tj_auth"));
        assertThrows(IllegalArgumentException.class,()->settings("daily","candidate-db.example.invalid","tj_auth","mfa_maintenance").testEnvironment("candidate-db.example.invalid","tj_auth"));
        assertThrows(IllegalArgumentException.class,()->settings("isolated-test","127.0.0.1","acceptance_mfa_rotation","root").testEnvironment("localhost","acceptance_mfa_rotation"));
        assertThrows(IllegalArgumentException.class,()->settings("isolated-test","127.0.0.1","acceptance_mfa_rotation","root").testEnvironment("127.0.0.1","acceptance_other"));
    }
}

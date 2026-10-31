package com.tianji.auth.maintenance;

import com.tianji.auth.service.impl.TotpSecrets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Explicit maintenance entry point; it never starts the application or changes MFA enrollment state. */
public final class MfaSecretMaintenance {
    private MfaSecretMaintenance() {}

    public record Settings(String schema,String environment,String host,int port,String database,
                           String username,String password,String activeKey,Map<String,String> decryptionKeys,
                           String legacyKeyId,String trustStoreUrl,String trustStorePassword) {
        @Override public String toString(){return "MfaMaintenanceSettings[REDACTED]";}

        public boolean testEnvironment(String expectedHost,String expectedDatabase){
            if(!"tianji-mfa-maintenance-v1".equals(schema) || !expectedHost.equals(host) || !expectedDatabase.equals(database)
                    || database==null || !database.matches("[A-Za-z0-9_]+") || port<1 || port>65535
                    || username==null || username.isBlank() || password==null || password.isBlank())
                throw new IllegalArgumentException("MFA maintenance target does not match the explicit configuration");
            if("isolated-test".equals(environment)){
                if(!Set.of("127.0.0.1","localhost","::1").contains(host) || !database.startsWith("acceptance_"))
                    throw new IllegalArgumentException("MFA test maintenance requires an isolated loopback acceptance database");
                return true;
            }
            if(!"isolated-production-candidate".equals(environment) || !database.equals("tj_auth")
                    || host==null || !host.matches("(?=.{1,253}$)(?=.*[A-Za-z])[A-Za-z0-9][A-Za-z0-9.-]*\\.[A-Za-z0-9.-]+")
                    || host.endsWith(".localhost") || Set.of("root","admin").contains(username.toLowerCase(java.util.Locale.ROOT)))
                throw new IllegalArgumentException("MFA maintenance requires an explicit isolated candidate and dedicated database identity");
            return false;
        }

        public TotpSecrets secrets(){return new TotpSecrets(activeKey,decryptionKeys==null?Map.of():decryptionKeys,legacyKeyId);}
    }

    public record Result(String status,int examined,int reencrypted,String activeKeyId) {}

    public static Result reencrypt(JdbcTemplate jdbc,TransactionTemplate transaction,TotpSecrets secrets){
        return transaction.execute(status->{
            var rows=jdbc.queryForList("SELECT user_id,secret_ciphertext FROM auth_mfa ORDER BY user_id FOR UPDATE");
            int changed=0;
            for(var row:rows){
                long user=((Number)row.get("user_id")).longValue();String original=row.get("secret_ciphertext").toString();
                String rewritten=secrets.reencrypt(user,original);
                if(!rewritten.equals(original)){
                    // Keep enrollment/anti-replay state and its modification timestamp intact.
                    if(jdbc.update("UPDATE auth_mfa SET secret_ciphertext=?,updated_at=updated_at WHERE user_id=?",rewritten,user)!=1)
                        throw new IllegalStateException("MFA maintenance lost a locked record");
                    changed++;
                }
            }
            return new Result("APPLIED",rows.size(),changed,secrets.activeKeyId());
        });
    }

    public static void main(String[] args){
        try{
            if(args.length!=3)throw new IllegalArgumentException("Use the protected MFA maintenance wrapper");
            var json=new JsonMapper();Settings settings;
            try(var input=Files.newInputStream(Path.of(args[0]))){settings=json.readValue(input,Settings.class);}
            boolean test=settings.testEnvironment(args[1],args[2]);TotpSecrets secrets=settings.secrets();
            String url="jdbc:"+new URI("mysql",null,settings.host(),settings.port(),"/"+settings.database(),null,null);
            var source=new DriverManagerDataSource(url,settings.username(),settings.password());
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            Properties options=new Properties();options.setProperty("sslMode",test?"DISABLED":"VERIFY_IDENTITY");
            options.setProperty("connectTimeout","10000");options.setProperty("socketTimeout","30000");
            options.setProperty("autoReconnect","false");
            if(test)options.setProperty("allowPublicKeyRetrieval","true");
            if(settings.trustStoreUrl()!=null){
                if(!"file".equals(URI.create(settings.trustStoreUrl()).getScheme()))throw new IllegalArgumentException("A local reviewed truststore is required");
                options.setProperty("trustCertificateKeyStoreUrl",settings.trustStoreUrl());
                if(settings.trustStorePassword()!=null)options.setProperty("trustCertificateKeyStorePassword",settings.trustStorePassword());
            }
            source.setConnectionProperties(options);var jdbc=new JdbcTemplate(source);
            if(!args[2].equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))throw new IllegalStateException("Connected MFA database differs from the target");
            if(!test){
                var cipher=jdbc.queryForMap("SHOW SESSION STATUS LIKE 'Ssl_cipher'");
                if(cipher.get("Value")==null || cipher.get("Value").toString().isBlank())throw new IllegalStateException("Candidate MFA database connection is not encrypted");
            }
            System.out.println("MFA_MAINTENANCE_RESULT="+json.writeValueAsString(reencrypt(jdbc,new TransactionTemplate(new DataSourceTransactionManager(source)),secrets)));
        }catch(Exception error){
            System.err.println("MFA maintenance failed: "+error.getClass().getSimpleName()+"; inspect the private target before retrying");
            System.exit(1);
        }
    }
}

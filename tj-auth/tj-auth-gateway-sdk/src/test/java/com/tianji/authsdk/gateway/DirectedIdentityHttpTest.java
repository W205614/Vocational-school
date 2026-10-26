package com.tianji.authsdk.gateway;

import com.sun.net.httpserver.HttpServer;
import com.tianji.authsdk.gateway.util.*;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.utils.ServiceIdentity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.env.MockEnvironment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real local sockets prove both non-Feign auth HTTP clients carry the configured receiver credential. */
class DirectedIdentityHttpTest {
    @TempDir Path temporary;
    private HttpServer server;private MockEnvironment environment;private AtomicReference<String> caller,token;private AtomicInteger requests;private String publicKey;
    private static final String FIXTURE="fixture-gateway-identity-token-0123456789abcdef";
    @BeforeEach void start()throws Exception{
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);publicKey=Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        caller=new AtomicReference<>();token=new AtomicReference<>();requests=new AtomicInteger();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/_modules/auth",exchange->{
            caller.set(exchange.getRequestHeaders().getFirst("X-TJ-Service"));token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));requests.incrementAndGet();
            String content=exchange.getRequestURI().getPath().endsWith("/jwks")?publicKey:"{\"code\":200,\"data\":null}";byte[] bytes=content.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var body=exchange.getResponseBody()){body.write(bytes);}
        });server.start();environment=new MockEnvironment().withProperty("tj.routes.auth","http://127.0.0.1:"+server.getAddress().getPort()+"/_modules/auth");
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    private ServiceIdentity directed()throws Exception{
        Path file=temporary.resolve("identities.json");Files.writeString(file,"{\"outbound\":{\"identity\":\""+FIXTURE+"\"},\"inbound\":{}}");
        return new ServiceIdentity(Map.of("TJ_INTERNAL_AUTH_MODE","service-tokens","TJ_SERVICE_ID","gateway","TJ_SERVICE_TOKENS_FILE",file.toString()));
    }
    private LoginUserDTO user(){var user=new LoginUserDTO();user.setUserId(42L);user.setRoleId(1L);user.setAuthVersion(0L);user.setSessionId(UUID.randomUUID().toString());return user;}
    @Test void directedSessionValidationUsesCallerAndReceiverTokenThenCaches()throws Exception{
        var verifier=new SessionVerifier(environment,mock(DiscoveryClient.class),directed());var identity=user();verifier.validate(identity);verifier.validate(identity);
        assertEquals("gateway",caller.get());assertEquals(FIXTURE,token.get());assertEquals(1,requests.get());
    }
    @Test void directedJwksFetchUsesCallerAndReceiverToken()throws Exception{
        var holder=new JwtSignerHolder(mock(DiscoveryClient.class),environment,directed());holder.refresh();assertNotNull(holder.getJwtSigner());assertEquals("gateway",caller.get());assertEquals(FIXTURE,token.get());
    }
    @Test void localSharedTokenCompatibilityDoesNotInventAServiceCaller(){
        var shared=new ServiceIdentity(Map.of("TJ_INTERNAL_TOKEN",FIXTURE));new SessionVerifier(environment,mock(DiscoveryClient.class),shared).validate(user());assertNull(caller.get());assertEquals(FIXTURE,token.get());
        var holder=new JwtSignerHolder(mock(DiscoveryClient.class),environment,shared);holder.refresh();assertNotNull(holder.getJwtSigner());assertNull(caller.get());assertEquals(FIXTURE,token.get());
    }
    @Test void redirectCannotExfiltrateServiceCredential()throws Exception{
        AtomicInteger redirected=new AtomicInteger();var receiver=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);receiver.createContext("/",exchange->{redirected.incrementAndGet();exchange.sendResponseHeaders(200,0);exchange.close();});receiver.start();
        server.removeContext("/_modules/auth");server.createContext("/_modules/auth",exchange->{exchange.getResponseHeaders().set("Location","http://127.0.0.1:"+receiver.getAddress().getPort()+"/capture");exchange.sendResponseHeaders(302,-1);exchange.close();});
        try {
            assertThrows(com.tianji.common.exceptions.ServiceUnavailableException.class,()->new SessionVerifier(environment,mock(DiscoveryClient.class),directed()).validate(user()));
            var holder=new JwtSignerHolder(mock(DiscoveryClient.class),environment,directed());holder.refresh();assertNull(holder.getJwtSigner());assertEquals(0,redirected.get());
        }finally{receiver.stop(0);}
    }
    @Test void untrustedHttpsCertificateIsRejectedBeforeSendingCredential()throws Exception{
        Path store=temporary.resolve("server.p12");String keytool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"keytool.exe":"keytool").toString();
        var process=new ProcessBuilder(keytool,"-genkeypair","-alias","fixture","-storetype","PKCS12","-keystore",store.toString(),"-storepass","fixture-password","-keypass","fixture-password","-dname","CN=localhost","-ext","SAN=IP:127.0.0.1,DNS:localhost","-validity","1","-keyalg","RSA","-keysize","2048").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());
        var keys=java.security.KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(store)){keys.load(input,"fixture-password".toCharArray());}
        var managers=javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());managers.init(keys,"fixture-password".toCharArray());var ssl=javax.net.ssl.SSLContext.getInstance("TLS");ssl.init(managers.getKeyManagers(),null,null);
        var https=com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);https.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(ssl));AtomicInteger received=new AtomicInteger();https.createContext("/_modules/auth",exchange->{received.incrementAndGet();exchange.sendResponseHeaders(200,0);exchange.close();});https.start();
        environment.setProperty("tj.routes.auth","https://127.0.0.1:"+https.getAddress().getPort()+"/_modules/auth");
        try {
            assertThrows(com.tianji.common.exceptions.ServiceUnavailableException.class,()->new SessionVerifier(environment,mock(DiscoveryClient.class),directed()).validate(user()));
            var holder=new JwtSignerHolder(mock(DiscoveryClient.class),environment,directed());holder.refresh();assertNull(holder.getJwtSigner());assertEquals(0,received.get());
        }finally{https.stop(0);}
    }
}

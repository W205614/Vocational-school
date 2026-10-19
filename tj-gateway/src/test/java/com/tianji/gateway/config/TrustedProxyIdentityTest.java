package com.tianji.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import java.net.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class TrustedProxyIdentityTest {
    private static InetAddress ip(String value)throws Exception{return InetAddress.getByName(value);}
    private MockServerHttpRequest request(String peer,String client,String proto)throws Exception {
        return MockServerHttpRequest.get("http://gateway/api/v2/auth/accounts/login")
                .remoteAddress(new InetSocketAddress(ip(peer),8080)).header("X-Forwarded-For",client).header("X-Forwarded-Proto",proto).build();
    }
    @Test void distinctClientsBehindTheSameTrustedNginxKeepTheirOwnLoginQuotaIdentity()throws Exception {
        var identity=new TrustedProxyIdentity(List.of("web-student"),30,name->new InetAddress[]{InetAddress.getByName("172.20.0.2")},System::nanoTime);
        assertEquals(new TrustedProxyIdentity.Identity("203.0.113.10","https"),identity.identify(request("172.20.0.2","203.0.113.10","https")));
        assertEquals(new TrustedProxyIdentity.Identity("203.0.113.11","http"),identity.identify(request("172.20.0.2","203.0.113.11","http")));
    }
    @Test void directCallerCannotSpoofForwardedIdentityOrProtocol()throws Exception {
        var identity=new TrustedProxyIdentity(List.of("172.20.0.2"),30,InetAddress::getAllByName,System::nanoTime);
        assertEquals(new TrustedProxyIdentity.Identity("172.20.0.3","http"),identity.identify(request("172.20.0.3","203.0.113.10","https")));
    }
    @Test void trustedProxyRejectsHeaderChainsDuplicateHeadersHostnamesAndInvalidProtocol()throws Exception {
        var identity=new TrustedProxyIdentity(List.of("172.20.0.2"),30,InetAddress::getAllByName,System::nanoTime);
        for(String value:List.of("203.0.113.10, 198.51.100.1","attacker.example","203.0.113.999"))
            assertEquals("172.20.0.2",identity.identify(request("172.20.0.2",value,"https")).address());
        assertEquals("http",identity.identify(request("172.20.0.2","203.0.113.10","ftp")).protocol());
        var duplicate=request("172.20.0.2","203.0.113.10","https").mutate().headers(headers->headers.add("X-Forwarded-For","198.51.100.1")).build();
        assertEquals("172.20.0.2",identity.identify(duplicate).address());
    }
    @Test void dnsCacheRefreshesContainerAddressAndDoesNotRetainStaleTrustAfterFailure()throws Exception {
        AtomicLong clock=new AtomicLong();AtomicInteger calls=new AtomicInteger();
        var identity=new TrustedProxyIdentity(List.of("web-student"),1,name->{
            int call=calls.incrementAndGet();if(call>=3)throw new UnknownHostException();
            return new InetAddress[]{InetAddress.getByName(call==1?"172.20.0.2":"172.20.0.4")};
        },clock::get);
        assertEquals("203.0.113.10",identity.identify(request("172.20.0.2","203.0.113.10","https")).address());
        identity.identify(request("172.20.0.2","203.0.113.11","https"));assertEquals(1,calls.get());
        clock.addAndGet(1_000_000_001L);
        assertEquals("172.20.0.2",identity.identify(request("172.20.0.2","203.0.113.10","https")).address());
        assertEquals("203.0.113.10",identity.identify(request("172.20.0.4","203.0.113.10","https")).address());
        clock.addAndGet(1_000_000_001L);
        assertEquals("172.20.0.4",identity.identify(request("172.20.0.4","203.0.113.10","https")).address());
    }
    @Test void ipv6LiteralIsAcceptedWithoutResolvingClientSuppliedHostnames()throws Exception {
        var identity=new TrustedProxyIdentity(List.of("172.20.0.2"),30,InetAddress::getAllByName,System::nanoTime);
        assertEquals(ip("2001:db8::1").getHostAddress(),identity.identify(request("172.20.0.2","2001:db8::1","https")).address());
    }
    @Test void blanketNetworksAndInvalidCacheSettingsCannotConfigureTrust(){
        assertThrows(IllegalArgumentException.class,()->new TrustedProxyIdentity(List.of("172.20.0.0/16"),30,InetAddress::getAllByName,System::nanoTime));
        assertThrows(IllegalArgumentException.class,()->new TrustedProxyIdentity(List.of("*"),30,InetAddress::getAllByName,System::nanoTime));
        assertThrows(IllegalArgumentException.class,()->new TrustedProxyIdentity(List.of(),0,InetAddress::getAllByName,System::nanoTime));
        assertThrows(IllegalStateException.class,()->new TrustedProxyIdentity(new org.springframework.mock.env.MockEnvironment().withProperty("server.forward-headers-strategy","framework")));
    }
}

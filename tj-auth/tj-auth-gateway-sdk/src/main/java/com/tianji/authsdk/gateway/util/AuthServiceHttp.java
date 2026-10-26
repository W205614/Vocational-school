package com.tianji.authsdk.gateway.util;

import cn.hutool.http.HttpRequest;
import com.tianji.common.utils.ServiceIdentity;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/** Explicit JDK TLS checks override legacy Hutool defaults; redirects must not carry service credentials. */
final class AuthServiceHttp {
    private AuthServiceHttp(){}
    static HttpRequest get(String uri,ServiceIdentity identity,int timeout){
        var credential=identity.outgoing(uri);
        var request=HttpRequest.get(uri).setFollowRedirects(false)
                .setSSLSocketFactory((SSLSocketFactory)SSLSocketFactory.getDefault())
                .setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
                .header("X-Internal-Token",credential.token()).timeout(timeout);
        if(credential.caller()!=null)request.header("X-TJ-Service",credential.caller());
        return request;
    }
}

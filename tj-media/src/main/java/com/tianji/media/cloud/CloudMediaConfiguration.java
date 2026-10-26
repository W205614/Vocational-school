package com.tianji.media.cloud;

import com.qcloud.cos.*;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="tj.cloud-media.enabled",havingValue="true")
public class CloudMediaConfiguration {
    @Bean public CloudMediaSettings cloudMediaSettings(Environment env){return CloudMediaSettings.from(env);}
    @Bean(destroyMethod="shutdown") public COSClient cloudMediaCosClient(CloudMediaSettings settings){
        var config=new ClientConfig(new Region(settings.region()));config.setHttpProtocol(HttpProtocol.https);
        config.setConnectionTimeout(3000);config.setConnectionRequestTimeout(3000);config.setSocketTimeout(15000);config.setMaxConnectionsCount(16);config.setMaxErrorRetry(1);
        return new COSClient(new BasicCOSCredentials(settings.secretId(),settings.secretKey()),config);
    }
    @Bean public CloudObjectStore cloudObjectStore(@org.springframework.beans.factory.annotation.Qualifier("cloudMediaCosClient") COSClient cloudMediaCosClient,CloudMediaSettings settings){return new TencentPrivateObjectStore(cloudMediaCosClient,settings.bucket());}
    @Bean public ApplicationRunner privateCloudBucketPreflight(CloudObjectStore store){return args->store.verifyPrivateBucket();}
    @Bean public CloudUploadService cloudUploadService(JdbcTemplate jdbc,PlatformTransactionManager manager,CloudObjectStore store,CloudMediaSettings settings){return new CloudUploadService(jdbc,manager,store,settings);}
    @Bean @ConditionalOnProperty(name="tj.cloud-media.role",havingValue="api",matchIfMissing=true)
    public CloudCosMediaStorage cloudCosMediaStorage(JdbcTemplate jdbc,CloudObjectStore store,CloudMediaSettings settings){return new CloudCosMediaStorage(jdbc,store,settings);}
}

package com.tianji.media.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.qcloud.vod.VodUploadClient;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tianji.media.storage.IFileStorage;
import com.tianji.media.storage.IMediaStorage;
import com.tianji.media.storage.tencent.TencentFileStorage;
import com.tianji.media.storage.tencent.TencentMediaStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties({TencentProperties.class})
public class TencentConfig {

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "TENCENT")
    public VodClient tencentVodClient(TencentProperties properties){
        // 1.授权信息
        Credential cred = new Credential(
                properties.getSecretId(), properties.getSecretKey());
        // 2.配置超时时间
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setConnTimeout(1);
        httpProfile.setReadTimeout(10);
        httpProfile.setWriteTimeout(10);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        // 2.初始化客户端
        return new VodClient(cred, properties.getVod().getRegion(), clientProfile);
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "TENCENT")
    public VodUploadClient tencentVodUploadClient(TencentProperties properties){
        // 1.初始化客户端
        return new VodUploadClient(properties.getSecretId(), properties.getSecretKey());
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "TENCENT")
    public IMediaStorage tencentMediaStorage(VodClient tencentVodClient, TencentProperties properties){
        return new TencentMediaStorage(tencentVodClient, properties);
    }

    @Bean(destroyMethod="shutdown")
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "TENCENT")
    public COSClient tencentCosClient(TencentProperties properties){
        // 1.授权信息
        COSCredentials cred = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        // 2.基本配置
        Region region = new Region(properties.getCos().getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        // 3.初始化客户端
        return new COSClient(cred, clientConfig);
    }

    @Bean(destroyMethod="shutdownNow")
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "TENCENT")
    public TransferManager transferManager(COSClient tencentCosClient, TencentProperties properties,@org.springframework.beans.factory.annotation.Qualifier("tencentUploadExecutor") org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor managed){
        // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
        // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
        ExecutorService threadPool = managed.getThreadPoolExecutor();

        // 传入一个 threadPool, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
        TransferManager transferManager = new TransferManager(tencentCosClient, threadPool);

        // 设置高级接口的配置项
        // 分块上传阈值和分块大小分别为 5MB 和 1MB
        TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartUploadThreshold(properties.getCos().getMultipartUploadThreshold());
        transferManagerConfiguration.setMinimumUploadPartSize(properties.getCos().getMinimumUploadPartSize());
        transferManager.setConfiguration(transferManagerConfiguration);

        return transferManager;
    }
    @Bean @ConditionalOnProperty(prefix="tj.platform",name="file",havingValue="TENCENT")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor tencentUploadExecutor(){
        var pool=new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();pool.setCorePoolSize(2);pool.setMaxPoolSize(4);pool.setQueueCapacity(16);pool.setThreadNamePrefix("tencent-upload-");pool.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());pool.setWaitForTasksToCompleteOnShutdown(true);pool.setAwaitTerminationSeconds(30);return pool;
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "TENCENT")
    public IFileStorage tencentFileStorage(
            COSClient tencentCosClient, TransferManager transferManager, TencentProperties properties){
        return new TencentFileStorage(tencentCosClient, transferManager, properties);
    }
}

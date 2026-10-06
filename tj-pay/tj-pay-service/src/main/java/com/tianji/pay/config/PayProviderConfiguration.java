package com.tianji.pay.config;
import com.tianji.pay.third.IPayService;
import com.tianji.pay.third.wx.config.WxPayProperties;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ListableBeanFactory;
import java.util.Map;
@Configuration @EnableConfigurationProperties(WxPayProperties.class)
public class PayProviderConfiguration {
 @Bean(name="payServiceChannels") public Map<String,IPayService> channels(ListableBeanFactory factory){return Map.copyOf(factory.getBeansOfType(IPayService.class));}
}

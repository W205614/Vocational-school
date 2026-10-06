package com.tianji.pay.sdk.constants;

import com.tianji.common.utils.StringUtils;
import lombok.Getter;

@Getter
public enum PayChannel {
    wxPay("微信支付"),
    aliPay("支付宝支付"),
    mockPay("本地模拟支付"),
    ;

    private final String desc;

    PayChannel(String desc) {
        this.desc = desc;
    }

    public static String desc(String value){
        if (StringUtils.isBlank(value)) {
            return "";
        }
        for(PayChannel channel:values())if(channel.name().equals(value))return channel.getDesc();
        return "未知支付渠道";
    }
}

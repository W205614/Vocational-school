package com.tianji.pay.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.alipay.easysdk.factory.Factory;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.pay.domain.po.PayOrder;
import com.tianji.pay.domain.po.RefundOrder;
import com.tianji.pay.sdk.constants.PayConstants;
import com.tianji.pay.sdk.constants.PayErrorInfo;
import com.tianji.pay.sdk.dto.PayResultDTO;
import com.tianji.pay.sdk.dto.RefundResultDTO;
import com.tianji.pay.service.INotifyService;
import com.tianji.pay.service.IPayOrderService;
import com.tianji.pay.service.IRefundOrderService;
import com.tianji.pay.third.ali.AliPayService;
import com.tianji.pay.third.model.RefundStatus;
import com.tianji.pay.third.wx.config.WxPayProperties;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.exception.NotFoundException;
import com.wechat.pay.contrib.apache.httpclient.exception.ParseException;
import com.wechat.pay.contrib.apache.httpclient.exception.ValidationException;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationHandler;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl implements INotifyService {
    private final org.springframework.beans.factory.ObjectProvider<CertificatesManager> certificates;
    private final org.springframework.core.env.Environment environment;
    private final WxPayProperties properties;
    private final IPayOrderService payOrderService;
    private final ProviderSettlementService settlement;
    private final IRefundOrderService refundOrderService;
    private final AliNotificationVerifier aliNotifications;

    @Override
    public void handleWxPayNotify(NotificationRequest request) {
        // 1.尝试校验微信通知请求参数，安全校验
        Notification notification = checkWxNotifyRequest(request);
        if (notification == null || !StringUtils.equals(notification.getEventType(), "TRANSACTION.SUCCESS")) {
            // 通知类型不是支付成功，不做处理
            return;
        }
        // 2.解析回调加密的数据
        String decryptData = notification.getDecryptData();
        JSONObject data = JsonUtils.parseObj(decryptData);

        // 3.获取用于业务校验的基本信息
        // 3.1.交易单号
        Long tradingOrderNo = data.getLong("out_trade_no");
        // 3.2.订单金额
        JSONObject amountObject = data.getJSONObject("amount");
        Integer amount = amountObject == null ? null : amountObject.getInt("total");
        // 3.3.订单支付时间
        LocalDateTime successTime = data.getLocalDateTime("success_time", LocalDateTime.now());

        // 4.校验通知数据，主要是业务校验、幂等校验
        settlement.paid(tradingOrderNo,amount,successTime);
    }


    @Override
    public void handleWxPayRefundNotify(NotificationRequest request) {
        // 1.尝试校验微信通知请求参数，安全校验
        Notification notification = checkWxNotifyRequest(request);
        if (notification == null || !StringUtils.equalsAny(
                notification.getEventType(), "REFUND.SUCCESS", "REFUND.ABNORMAL", "REFUND.CLOSED")) {
            // 通知类型错误，直接返回
            log.debug("微信退款通知类型异常");
            return;
        }

        // 2.解析通知数据
        String decryptData = notification.getDecryptData();
        JSONObject data = JsonUtils.parseObj(decryptData);
        // 2.1.退款单号
        Long refundOrderNo = data.getLong("out_refund_no");
        if (refundOrderNo == null) {
            log.error("微信通知数据有误，缺少退款单号");
            throw new BadRequestException("微信通知数据有误，缺少退款单号");
        }
        // 2.2.退款状态
        String statusStr = notification.getEventType();
        RefundStatus status = handleWxRefundStatus(statusStr);

        // 3.幂等性校验
        settlement.refund(refundOrderNo,status.getValue(),null,data.getStr("msg"),null);
    }

    @Override
    public void handleAliPayNotify(Map<String, String> request) {
        var payment=aliNotifications.verify(request);
        if(payment.paid())settlement.paid(payment.number(),payment.cents(),payment.occurred(),PayConstants.ALI_CHANNEL_CODE);
    }


    private RefundStatus handleWxRefundStatus(String statusStr) {
        if (StringUtils.equalsAny(statusStr, "REFUND.CLOSED", "REFUND.ABNORMAL")) {
            return RefundStatus.FAILED;
        }
        if ("REFUND.SUCCESS".equals(statusStr)) {
            return RefundStatus.SUCCESS;
        }
        return RefundStatus.UN_KNOWN;
    }


    @Nullable
    private Notification checkWxNotifyRequest(NotificationRequest request) {
        var certificatesManager=certificates.getIfAvailable();
        if(certificatesManager==null)throw new BadRequestException("微信支付渠道未配置");
        try {
            Verifier verifier = certificatesManager.getVerifier(properties.getMchId());
            String apiV3Key = properties.getApiV3Key();
            NotificationHandler handler = new NotificationHandler(verifier, apiV3Key.getBytes(StandardCharsets.UTF_8));
            // 验签和解析请求体
            return handler.parse(request);

        } catch (NotFoundException e) {
            log.error("找不到商户{}的校验证书", properties.getMchId(), e);
            return null;
        } catch (ValidationException e) {
            log.error("微信回调结果校验失败", e);
            throw new BadRequestException(400, "微信回调结果校验失败", e);
        } catch (ParseException e) {
            log.error("微信回调结果解析失败", e);
            throw new BadRequestException(400, "微信回调结果解析失败", e);
        } catch (RuntimeException e) {
            log.error("微信回调结果处理失败", e);
            throw new BadRequestException(400, "微信回调结果处理失败", e);
        }
    }

}

package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignRecordVO;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecords() {
        // 1. 签到
        // 1.1 获取登录用户
        Long userId = UserContext.getUser();
        // 1.2 获取日期
        LocalDate now = LocalDate.now();
        // 1.3 拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 1.4 计算offset
        int offset = now.getDayOfMonth() - 1;
        // 1.5 保存签到信息
        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtils.isTrue(exits)) {
            throw new BizIllegalException("不允许重复签到!");
        }

        // 2. 计算连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());

        // 3. 计算签到得分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
            case 14:
                rewardPoints = 20;
            case 28:
                rewardPoints = 40;
                break;
        }

        // 4. 保存积分明细记录
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1)
        );
        // 5. 封装返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    @Override
    public SignRecordVO querySignRecords() {
        //1、获取当前登录用户id
        Long userId = UserContext.getUser();

        //2、拼接key
        LocalDate now = LocalDate.now(); //当前时间的年月
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;

        //3、查询签到记录
        Boolean getBit = redisTemplate.opsForValue().getBit(key, now.getDayOfMonth() - 1);
        if (Boolean.FALSE.equals(getBit)) {
            throw new BizIllegalException("签到记录不存在！");
        }
        List<Byte> signRecords = signRecordDays(key, now.getDayOfMonth()); //获取签到记录
        log.debug("转换后签到记录：{}", signRecords); // 实际输出列表

        //4.封装返回
        SignRecordVO vo = new SignRecordVO();
        vo.setSignDays(countSignDays(key, now.getDayOfMonth()));
        vo.setSignRecords(signRecords);
        return vo;
    }

    private List<Byte> signRecordDays(String key, int dayOfMonth) {
        //1、获取本月第一天到今天所有的签到数据  bitField得到的是十进制
        List<Long> bitField = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField)) {
            return CollUtils.emptyList();
        }
        Long num = bitField.get(0); //本月第一天到今天所有的签到数据  拿到十进制数据
        log.debug("本月第一天到今天所有的签到数据：{}", num);

        //2、num转二进制，每天签到情况（与运算、右移） 0未签到，1已签到
        List<Byte> list = new ArrayList<>();
        for (int day = 0; day < dayOfMonth; day++) {
            list.add((byte) (num & 1));
            num = num >>> 1;
        }
        Collections.reverse(list); // 按日期顺序调整
        return list;
    }

    private int countSignDays(String key, int len) {
        // 1. 获取本月从第一天开始, 到今天为止的所有签到记录
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        int num = result.get(0).intValue();
        // 2. 定义一个计数器
        int count = 0;
        // 3. 循环, 与1做与运算, 得到最后一个bit, 判断是否为0, 为0则终止, 为1则继续
        while ((num & 1) == 1) {
            // 4. 计数器+1
            count++;
            // 5. 把数字右移一位, 最后一位被舍弃, 倒数第二位成了最后一位
            num >>>= 1; // 无符号右移
        }
        return count;
    }
}

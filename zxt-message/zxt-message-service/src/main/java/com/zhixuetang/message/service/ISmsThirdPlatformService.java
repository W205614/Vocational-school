package com.zhixuetang.message.service;

import com.zhixuetang.message.domain.dto.SmsThirdPlatformDTO;
import com.zhixuetang.message.domain.dto.SmsThirdPlatformFormDTO;
import com.zhixuetang.message.domain.query.SmsThirdPlatformPageQuery;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.message.domain.po.SmsThirdPlatform;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * <p>
 * 第三方云通讯平台 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface ISmsThirdPlatformService extends IService<SmsThirdPlatform> {

    List<SmsThirdPlatform> queryAllPlatform();

    Long saveSmsThirdPlatform(SmsThirdPlatformFormDTO thirdPlatformDTO);

    void updateSmsThirdPlatform(SmsThirdPlatformFormDTO thirdPlatformDTO);

    PageDTO<SmsThirdPlatformDTO> querySmsThirdPlatforms(SmsThirdPlatformPageQuery query);

    SmsThirdPlatformDTO querySmsThirdPlatform(Long id);
}

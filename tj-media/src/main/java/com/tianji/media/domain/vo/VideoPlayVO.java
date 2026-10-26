package com.tianji.media.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "视频播放的签名信息")
public class VideoPlayVO {
    private String provider;
    private String playUrl;
    @Schema(description = "受控播放地址到期时间，Unix epoch 毫秒")
    private Long expiresAt;
    @Schema(description = "视频唯一标示", example = "12412534535143242")
    private String fileId;
    @Schema(description = "视频封面", example = "xxx.xxx.xxx")
    private String signature;
}

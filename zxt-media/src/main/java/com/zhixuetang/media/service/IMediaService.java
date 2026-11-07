package com.zhixuetang.media.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.media.domain.dto.MediaDTO;
import com.zhixuetang.media.domain.dto.MediaUploadResultDTO;
import com.zhixuetang.media.domain.po.Media;
import com.zhixuetang.media.domain.query.MediaQuery;
import com.zhixuetang.media.domain.vo.MediaVO;
import com.zhixuetang.media.domain.vo.VideoPlayVO;

/**
 * <p>
 * 媒资表，主要是视频文件 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-30
 */
public interface IMediaService extends IService<Media> {

    String getUploadSignature();

    VideoPlayVO getPlaySignatureBySectionId(Long fileId);

    MediaDTO save(MediaUploadResultDTO mediaResult);

    void updateMediaProcedureResult(Media media);

    void deleteMedia(String fileId);

    VideoPlayVO getPlaySignatureByMediaId(Long mediaId);

    PageDTO<MediaVO> queryMediaPage(MediaQuery query);
}

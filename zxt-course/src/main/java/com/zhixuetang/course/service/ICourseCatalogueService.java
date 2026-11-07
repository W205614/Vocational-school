package com.zhixuetang.course.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.api.dto.course.CatalogueDTO;
import com.zhixuetang.api.dto.course.MediaQuoteDTO;
import com.zhixuetang.api.dto.course.SectionInfoDTO;
import com.zhixuetang.course.domain.po.CourseCatalogue;
import com.zhixuetang.course.domain.vo.CataSimpleInfoVO;
import com.zhixuetang.course.domain.vo.CataVO;

import java.util.List;

/**
 * <p>
 * 目录草稿 服务类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-19
 */
public interface ICourseCatalogueService extends IService<CourseCatalogue> {

    /**
     * 查询线上课程目录
     *
     * @param courseId 课程id
     * @return 课程目录
     */
    List<CatalogueDTO> queryCourseCatalogues(Long courseId, Boolean withPractice);

    /** Published directory metadata only, with no private exam or media enrichment. */
    List<CatalogueDTO> queryPublicCourseCatalogues(Long courseId);

    /**
     * 批量统计媒资id引用次数
     *
     * @param mediaIds 媒资id
     * @return 媒资引用次数
     */
    List<MediaQuoteDTO> countMediaUserInfo(List<Long> mediaIds);

    /**
     * 获取简单的小节信息，
     *
     * @param sectionId 小节id
     * @return 课程id，媒资id，是否支持免费试看，免费试看时长
     */
    SectionInfoDTO getSimpleSectionInfo(Long sectionId);

    /**
     * 根据课程id获取课程的目录列表
     *
     * @param courseId 课程id
     * @return 课程的目录列表
     */
    List<CataSimpleInfoVO> getCatasIndexList(Long courseId);

    List<CataSimpleInfoVO> getManyCataSimpleInfo(List<Long> ids);

    CataSimpleInfoVO querySectionInfoById(Long id);

    List<CataVO> queryCourseCataloguesVO(Long courseId, Boolean withPractice);
}

package com.zhixuetang.learning.service;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.learning.domain.dto.ReplyDTO;
import com.zhixuetang.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.learning.domain.query.ReplyPageQuery;
import com.zhixuetang.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-04
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void saveReply(ReplyDTO dto);

    PageDTO<ReplyVO> queryReplyVOPage(ReplyPageQuery query, Boolean isAmin);

    void hiddenReplyAdmin(Long id, Boolean hidden);

    ReplyVO queryReplyVOByIdAdmin(Long id);
}

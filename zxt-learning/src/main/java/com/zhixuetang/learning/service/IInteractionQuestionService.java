package com.zhixuetang.learning.service;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.learning.domain.dto.QuestionFormDTO;
import com.zhixuetang.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.learning.domain.query.QuestionAdminPageQuery;
import com.zhixuetang.learning.domain.query.QuestionPageQuery;
import com.zhixuetang.learning.domain.vo.QuestionAdminVO;
import com.zhixuetang.learning.domain.vo.QuestionVO;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-04
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    void saveQuestion(QuestionFormDTO questionDTO);

    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    QuestionVO queryQuestionById(Long id);

    PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query);

    void updateQuestion(Long id, QuestionFormDTO dto);

    void deleteQuestionById(Long id);

    void hiddenQuestionAdmin(Long id, Boolean hidden);

    QuestionAdminVO queryQuestionAdminVOById(Long id);
}

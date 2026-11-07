package com.zhixuetang.exam.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.api.dto.exam.QuestionDTO;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.exam.domain.dto.QuestionFormDTO;
import com.zhixuetang.exam.domain.po.Question;
import com.zhixuetang.exam.domain.query.QuestionPageQuery;
import com.zhixuetang.exam.domain.vo.QuestionDetailVO;
import com.zhixuetang.exam.domain.vo.QuestionPageVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 题目 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
public interface IQuestionService extends IService<Question> {

    void addQuestion(QuestionFormDTO questionFormDTO);

    void updateQuestion(QuestionFormDTO questionDTO);

    void deleteQuestionById(Long id);

    PageDTO<QuestionPageVO> queryQuestionByPage(QuestionPageQuery query);

    QuestionDetailVO queryQuestionDetailById(Long id);

    List<QuestionDTO> queryQuestionByIds(List<Long> ids);

    Map<Long, Integer> countQuestionNumOfCreater(List<Long> createrIds);

    List<QuestionDTO> queryQuestionByBizId(Long bizId);

    Boolean checkNameValid(String name);

    Map<Long, Integer> queryQuestionScores(List<Long> ids);
}

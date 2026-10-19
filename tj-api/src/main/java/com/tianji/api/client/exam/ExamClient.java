package com.tianji.api.client.exam;

import com.tianji.api.dto.exam.QuestionBizDTO;
import com.tianji.api.dto.exam.QuestionDTO;
import com.tianji.api.dto.exam.ExamSettlementPage;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient("exam-service")
public interface ExamClient {

    @GetMapping("/internal/v1/exams/settlement-readiness")
    ExamSettlementPage settlementReadiness(@RequestParam("month") String month,
                                           @RequestParam("cursor") long cursor,
                                           @RequestParam(value="upperBound",required=false) Long upperBound);

    @PostMapping("/internal/v1/exams/learning-overview")
    Map<String,Integer> learningOverview(@RequestBody List<Long> courseIds);

    @PostMapping("/question-biz/list")
    void saveQuestionBizInfoBatch(@RequestBody Iterable<QuestionBizDTO> qbs);

    @GetMapping("/question-biz/biz/list")
    List<QuestionBizDTO> queryQuestionIdsByBizIds(@RequestParam("ids") Iterable<Long> bizIds);

    @GetMapping("/question-biz/scores")
    Map<Long, Integer> queryQuestionScoresByBizIds(@RequestParam("ids") Iterable<Long> bizIds);

    @GetMapping("/questions/list")
    List<QuestionDTO> queryQuestionByIds(@Parameter(description = "要查询的题目的id集合") @RequestParam("ids") Iterable<Long> ids);

    @GetMapping("/questions/numOfTeacher")
    Map<Long, Integer> countSubjectNumOfTeacher(@RequestParam("ids") Iterable<Long> createrIds);

    @GetMapping("/questions/scores")
    Map<Long, Integer> queryQuestionScores(
            @Parameter(description = "要查询的题目的id集合") @RequestParam("ids") Iterable<Long> ids);
}

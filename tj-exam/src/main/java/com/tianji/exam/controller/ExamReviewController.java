package com.tianji.exam.controller;

import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.service.impl.ExamReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/exam-review")
public class ExamReviewController {
    private final ExamReviewService review;private final OperationStore operations;
    public record Response(String response,Long version) {}
    public record State(Boolean mastered,Long version) {}
    @GetMapping("/page") public Map<String,Object> page(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long courseId,@RequestParam(required=false) Integer type,@RequestParam(required=false) Boolean mastered){return review.page(UserContext.requireUser(),pageNo,pageSize,courseId,type,mastered);}
    @GetMapping("/{attemptId}/{questionId}") public Map<String,Object> detail(@PathVariable long attemptId,@PathVariable long questionId){return review.detail(UserContext.requireUser(),attemptId,questionId);}
    @PostMapping("/{attemptId}/{questionId}/reviews") public ResponseEntity<OperationStore.View> answer(@PathVariable long attemptId,@PathVariable long questionId,@RequestBody Response form,@RequestHeader("Idempotency-Key") String key){return submit(key,new ExamReviewService.Command("REVIEW",attemptId,questionId,form.response(),null,form.version()));}
    @PutMapping("/{attemptId}/{questionId}/state") public ResponseEntity<OperationStore.View> state(@PathVariable long attemptId,@PathVariable long questionId,@RequestBody State form,@RequestHeader("Idempotency-Key") String key){return submit(key,new ExamReviewService.Command("STATE",attemptId,questionId,null,form.mastered(),form.version()));}
    private ResponseEntity<OperationStore.View> submit(String key,ExamReviewService.Command command){return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"EXAM_REVIEW",key,command));}
}

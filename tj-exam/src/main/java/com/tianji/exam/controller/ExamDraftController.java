package com.tianji.exam.controller;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.service.impl.ExamDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/exam-attempts/{id}/draft")
public class ExamDraftController {
    private final ExamDraftService drafts;
    @GetMapping public ExamDraftService.View read(@PathVariable long id) {return drafts.read(id,UserContext.requireUser());}
    @PutMapping public ExamDraftService.View save(@PathVariable long id,@RequestBody ExamDraftService.Save form) {return drafts.save(id,UserContext.requireUser(),form);}
}

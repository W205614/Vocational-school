package com.tianji.exam.reliability;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import com.tianji.exam.service.impl.ExamWorkflowService;
class ObjectiveScoringTest {
 @Test void scoresOnlyTheCompleteAnswerSet(){assertEquals(10,ExamWorkflowService.scoreObjective("2,1","1,2",10));for(String input:new String[]{"","1","1,2,3","1,1,2","x,2"})assertEquals(0,ExamWorkflowService.scoreObjective(input,"1,2",10));}
}

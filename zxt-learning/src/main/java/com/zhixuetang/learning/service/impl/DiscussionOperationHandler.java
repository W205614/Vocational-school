package com.zhixuetang.learning.service.impl;

import com.zhixuetang.common.autoconfigure.reliability.OperationHandler;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.learning.domain.dto.*;
import com.zhixuetang.learning.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class DiscussionOperationHandler implements OperationHandler {
    public record Request(String action, Long role, QuestionFormDTO question, ReplyDTO reply) {}

    private final IInteractionQuestionService questions;
    private final IInteractionReplyService replies;
    private final JsonMapper json;

    @Override
    public String kind() {
        return "DISCUSSION_CREATE";
    }

    @Override
    public Object execute(String id, long user, String payload) {
        Request request = json.readValue(payload, Request.class);
        UserContext.setUser(user);
        UserContext.setRole(request.role());
        try {
            switch (request.action()) {
                case "QUESTION" -> questions.saveQuestion(request.question());
                case "REPLY" -> replies.saveReply(request.reply());
                default -> throw new com.zhixuetang.common.exceptions.BadRequestException("互动操作无效");
            }
            return java.util.Map.of("saved", true);
        } finally {
            UserContext.removeUser();
        }
    }
}

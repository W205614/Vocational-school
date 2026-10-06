package com.tianji.message.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.message.domain.dto.UserInboxDTO;
import com.tianji.message.domain.dto.UserInboxFormDTO;
import com.tianji.message.domain.query.UserInboxQuery;
import com.tianji.message.service.IUserInboxService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 用户通知记录 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
@Tag(name = "用户收件箱接口")
@RestController
@RequestMapping("/inboxes")
@RequiredArgsConstructor
public class UserInboxController {

    private final IUserInboxService inboxService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @PutMapping("/{id}/read") public void read(@PathVariable long id){
        long user=com.tianji.common.utils.UserContext.requireUser();
        if(jdbc.update("UPDATE user_inbox SET is_read=1 WHERE id=? AND user_id=?",id,user)!=1 && jdbc.queryForObject("SELECT COUNT(*) FROM user_inbox WHERE id=? AND user_id=?",Integer.class,id,user)!=1)
            throw new com.tianji.common.exceptions.BadRequestException("消息不存在");
    }

    @PostMapping
    @Operation(summary = "发送私信")
    public Long sentMessageToUser(@RequestBody UserInboxFormDTO userInboxFormDTO){
        return inboxService.sentMessageToUser(userInboxFormDTO);
    }

    @Operation(summary = "分页查询收件箱")
    @GetMapping
    public PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query){
        return inboxService.queryUserInBoxesPage(query);
    }
}

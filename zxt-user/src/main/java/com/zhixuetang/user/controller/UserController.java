package com.zhixuetang.user.controller;

import com.zhixuetang.api.dto.user.LoginFormDTO;
import com.zhixuetang.api.dto.user.UserDTO;
import com.zhixuetang.common.domain.dto.LoginUserDTO;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.utils.BeanUtils;
import com.zhixuetang.common.utils.CollUtils;
import com.zhixuetang.user.constants.UserErrorInfo;
import com.zhixuetang.user.domain.dto.UserFormDTO;
import com.zhixuetang.user.domain.po.User;
import com.zhixuetang.user.domain.po.UserDetail;
import com.zhixuetang.user.domain.vo.UserDetailVO;
import com.zhixuetang.user.enums.UserStatus;
import com.zhixuetang.user.service.IUserDetailService;
import com.zhixuetang.user.service.IUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Hidden;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("users")
@Tag(name = "用户管理接口")
public class UserController {

    @Autowired private IUserService userService;
    @Autowired private IUserDetailService detailService;

    @Operation(summary = "新增用户，一般是员工或教师")
    @PostMapping
    public com.zhixuetang.api.dto.user.TemporaryCredentialDTO saveUser(
            @Valid @RequestBody UserDTO userDTO) {
        com.zhixuetang.common.utils.UserContext.requireAdmin();
        userDTO.setId(null);
        return userService.saveUser(userDTO);
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{id}")
    public void updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        com.zhixuetang.common.utils.UserContext.requireAdmin();
        userDTO.setId(id);
        userService.updateUser(userDTO);
    }

    @Operation(summary = "更新当前登录用户信息，可修改密码")
    @PutMapping
    public void updateCurrentUser(@Valid @RequestBody UserFormDTO userDTO) {
        userService.updateUserWithPassword(userDTO);
    }

    @PutMapping("/{id}/password/default")
    @Operation(summary = "重置密码")
    public com.zhixuetang.api.dto.user.TemporaryCredentialDTO resetPassword(
            @Parameter(description = "要重置的用户的id", example = "1") @PathVariable("id") Long userId) {
        com.zhixuetang.common.utils.UserContext.requireAdmin();
        if (java.util.Objects.equals(userId, com.zhixuetang.common.utils.UserContext.requireUser()))
            throw new BadRequestException("当前账号请使用个人设置中的修改密码，避免丢失临时凭据");
        return userService.resetPassword(userId);
    }

    @PutMapping("/{id}/status/{status}")
    @Operation(summary = "修改用户状态, status=0为禁用，status=1为正常")
    public void updateUserStatus(
            @Parameter(description = "要重置的用户的id", example = "1") @PathVariable("id") Long userId,
            @Parameter(description = "状态", example = "1") @PathVariable("status") Integer status) {
        com.zhixuetang.common.utils.UserContext.requireAdmin();
        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.of(status));
        userService.updateById(user);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping(value = "/me")
    public UserDetailVO me() {
        return userService.myInfo();
    }

    @Operation(summary = "根据id查询用户信息")
    @GetMapping("/{id}")
    public UserDTO queryUserById(@Parameter(description = "用户id") @PathVariable("id") Long id) {
        com.zhixuetang.common.utils.InternalAuth.requireService();
        UserDetail userDetail = detailService.queryById(id);
        return BeanUtils.copyBean(
                userDetail, UserDTO.class, (d, u) -> u.setType(d.getType().getValue()));
    }

    /**
     * 登录结构
     * @param loginDTO 登录表单
     * @param isStaff 是否是后台登录
     * @return 登录用户信息
     */
    @Hidden
    @PostMapping("/detail/{isStaff}")
    public LoginUserDTO queryUserDetail(
            @Valid @RequestBody LoginFormDTO loginDTO, @PathVariable("isStaff") boolean isStaff) {
        com.zhixuetang.common.utils.InternalAuth.requireService();
        return userService.queryUserDetail(loginDTO, isStaff);
    }

    /**
     * <h1>根据id批量查询用户信息</h1>
     *
     * @param ids 用户id集合
     * @return 用户集合
     */
    @Hidden
    @GetMapping("/list")
    public List<UserDTO> queryUserByIds(
            @Parameter(description = "用户id的列表") @RequestParam("ids") List<Long> ids) {
        com.zhixuetang.common.utils.InternalAuth.requireService();
        if (ids.size() > 100) throw new BadRequestException("最多查询100个用户");
        if (CollUtils.isEmpty(ids)) {
            return CollUtils.emptyList();
        }
        // 1.查询列表
        List<UserDetail> list = detailService.queryByIds(ids);
        // 2.转换
        return BeanUtils.copyList(list, UserDTO.class, (d, u) -> u.setType(d.getType().getValue()));
    }

    /**
     * 查询用户类型
     *
     * @param id 用户id
     * @return 用户类型，0-普通学员，1-老师，2-其他员工
     */
    @Hidden
    @GetMapping("/{id}/type")
    public Integer queryUserType(@PathVariable("id") Long id) {
        com.zhixuetang.common.utils.InternalAuth.requireService();
        User user = userService.getById(id);
        if (user == null) {
            throw new BadRequestException(UserErrorInfo.Msg.USER_ID_NOT_EXISTS);
        }
        return user.getType().getValue();
    }

    @Hidden
    @GetMapping("/ids")
    public Long exchangeUserIdWithPhone(@RequestParam("phone") String phone) {
        com.zhixuetang.common.utils.InternalAuth.requireService();
        User user = userService.lambdaQuery().eq(User::getCellPhone, phone).one();
        if (user == null) {
            throw new BadRequestException(UserErrorInfo.Msg.USER_ID_NOT_EXISTS);
        }
        return user.getId();
    }

    @Operation(summary = "检查用户手机号是否存在")
    @GetMapping("checkCellphone")
    public Boolean checkCellPhone(@RequestParam("cellphone") String cellPhone) {
        return userService
                        .lambdaQuery()
                        .eq(User::getCellPhone, cellPhone)
                        // .in(User::getType, UserType.STAFF, UserType.TEACHER)
                        .count()
                <= 0;
    }
}

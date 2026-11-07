package com.zhixuetang.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixuetang.api.cache.RoleCache;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.common.enums.UserType;
import com.zhixuetang.common.utils.BeanUtils;
import com.zhixuetang.user.domain.po.UserDetail;
import com.zhixuetang.user.domain.query.UserPageQuery;
import com.zhixuetang.user.domain.vo.StaffVO;
import com.zhixuetang.user.service.IStaffService;
import com.zhixuetang.user.service.IUserDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 员工详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements IStaffService {

    private final IUserDetailService detailService;
    private final RoleCache roleCache;

    @Override
    public PageDTO<StaffVO> queryStaffPage(UserPageQuery query) {
        // 1.搜索
        Page<UserDetail> p =
                detailService.queryUserDetailByPage(query, UserType.STAFF, UserType.TEACHER);
        // 2.处理vo
        return PageDTO.of(
                p,
                u -> {
                    StaffVO v = BeanUtils.toBean(u, StaffVO.class);
                    v.setRoleName(
                            u.getType() == UserType.TEACHER
                                    ? com.zhixuetang.user.constants.UserConstants.TEACHER_ROLE_NAME
                                    : roleCache.getRoleName(u.getRoleId()));
                    return v;
                });
    }
}

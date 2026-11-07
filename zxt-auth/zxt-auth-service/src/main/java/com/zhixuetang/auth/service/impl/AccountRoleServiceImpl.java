package com.zhixuetang.auth.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zhixuetang.auth.domain.po.AccountRole;
import com.zhixuetang.auth.mapper.AccountRoleMapper;
import com.zhixuetang.auth.service.IAccountRoleService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 账户、角色关联表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-16
 */
@Service
public class AccountRoleServiceImpl extends ServiceImpl<AccountRoleMapper, AccountRole>
        implements IAccountRoleService {}

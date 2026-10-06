package com.tianji.auth.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.auth.common.domain.PrivilegeRoleDTO;
import com.tianji.auth.domain.po.Privilege;
import com.tianji.auth.domain.po.RolePrivilege;
import com.tianji.auth.mapper.PrivilegeMapper;
import com.tianji.auth.service.IPrivilegeService;
import com.tianji.auth.service.IRolePrivilegeService;
import com.tianji.auth.service.IRoleService;
import com.tianji.auth.util.PrivilegeCache;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.CollUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.auth.common.constants.AuthErrorInfo.Msg.*;
import static com.tianji.auth.constants.AuthConstants.ADMIN_ROLE_ID;

/**
 * <p>
 * 权限表，包括菜单权限和访问路径权限 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-15
 */
@Service
@RequiredArgsConstructor
public class PrivilegeServiceImpl extends ServiceImpl<PrivilegeMapper, Privilege> implements IPrivilegeService {

    private final IRolePrivilegeService rolePrivilegeService;
    private final IRoleService roleService;
    private final PrivilegeCache privilegeCache;

    @Override
    public Page<Privilege> listPrivilegesByPage(PageQuery pageQuery) {
        // 1.分页查询
        return page(pageQuery.toMpPage("id",true));
    }

    @Override
    @Transactional
    public void savePrivilege(Privilege p) {
        privilegeCache.lockMutation();
        p.setMethod(p.getMethod().toUpperCase());
        // 1.判断是否存在
        Integer count = Math.toIntExact(lambdaQuery()
                .eq(Privilege::getMethod, p.getMethod())
                .eq(Privilege::getUri, p.getUri())
                .count());
        if(count > 0){
            // 已经存在，结束
            throw new CommonException(PRIVILEGE_EXISTS);
        }
        // 2.新增权限数据
        save(p);
        // 3.给超级管理员加权限
        RolePrivilege rolePrivilege = new RolePrivilege();
        rolePrivilege.setPrivilegeId(p.getId());
        rolePrivilege.setRoleId(ADMIN_ROLE_ID);
        rolePrivilegeService.save(rolePrivilege);

        // 4.添加缓存
        privilegeCache.cacheSinglePrivilege(p, CollUtils.singletonSet(ADMIN_ROLE_ID));
    }

    @Override
    @Transactional
    public void removePrivilegeById(Long id) {
        privilegeCache.lockMutation();
        // 删除权限
        removeById(id);
        // 删除角色权限关联
        rolePrivilegeService.removeByPrivilegeId(id);
        // 删除缓存
        privilegeCache.removePrivilegeCacheById(id);
    }

    @Override
    public List<PrivilegeRoleDTO> listPrivilegeRoles() {
        // 1.查询所有权限
        List<Privilege> privileges = list();
        // 2.查询所有角色
        List<RolePrivilege> rpList = rolePrivilegeService.list();
        // 3.按照权限将角色分组
        Map<Long, List<RolePrivilege>> rpMap = rpList.stream()
                .collect(Collectors.groupingBy(RolePrivilege::getPrivilegeId));
        // 4.组装权限对应角色
        List<PrivilegeRoleDTO> list = new ArrayList<>(privileges.size());
        for (Privilege p : privileges) {
            // 4.1.根据权限查询角色
            Set<Long> roles = rpMap.getOrDefault(p.getId(),List.of())
                    .stream()
                    .map(RolePrivilege::getRoleId)
                    .collect(Collectors.toSet());
            // 4.2.组装
            PrivilegeRoleDTO prDTO = new PrivilegeRoleDTO();
            prDTO.setId(p.getId());
            prDTO.setRoles(roles);
            prDTO.setAntPath(p.getMethod() + ":" + p.getUri());
            prDTO.setInternal(p.getInternal());
            // 4.3.存入map
            list.add(prDTO);
        }
        return list;
    }

    @Override
    public Set<Long> listPrivilegeByRoleId(Long roleId) {
        List<RolePrivilege> rolePrivileges = rolePrivilegeService.lambdaQuery()
                .eq(RolePrivilege::getRoleId, roleId)
                .list();
        if (CollectionUtil.isEmpty(rolePrivileges)) {
            return Collections.emptySet();
        }
        return rolePrivileges.stream().map(RolePrivilege::getPrivilegeId).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void bindRolePrivileges(Long roleId, List<Long> privilegeIds) {
        privilegeIds=checkedIds(privilegeIds);
        privilegeCache.lockMutation();
        // 1.判断角色是否存在
        boolean roleExists = roleService.exists(roleId);
        if (!roleExists) {
            throw new CommonException(ROLE_NOT_FOUND);
        }
        // 2.判断权限是否存在
        Integer privilegeCount = Math.toIntExact(lambdaQuery().in(Privilege::getId, privilegeIds).count());
        if (privilegeCount != privilegeIds.size()) {
            throw new CommonException(PRIVILEGE_NOT_FOUND);
        }
        // 3.绑定关系
        List<RolePrivilege> rolePrivileges = new ArrayList<>(privilegeCount);
        Set<Long> existing=listPrivilegeByRoleId(roleId);
        for (Long privilegeId : privilegeIds) {
            if(existing.contains(privilegeId))continue;
            rolePrivileges.add(new RolePrivilege(roleId, privilegeId));
        }
        // 4.写入数据库
        if(!rolePrivileges.isEmpty())rolePrivilegeService.saveBatch(rolePrivileges);
        // 5.重置缓存
        privilegeCache.initPrivilegesCache(listPrivilegeRoles());
    }

    @Override
    @Transactional
    public void deleteRolePrivileges(Long roleId, List<Long> privilegeIds) {
        privilegeIds=checkedIds(privilegeIds);
        privilegeCache.lockMutation();
        // 1.删除
        rolePrivilegeService.deleteRolePrivileges(roleId, privilegeIds);
        // 2.移除对应角色权限缓存
        privilegeCache.initPrivilegesCache(listPrivilegeRoles());
    }

    @Override @Transactional
    public void updatePrivilege(Privilege privilege){
        privilegeCache.lockMutation();
        if(getById(privilege.getId())==null)throw new com.tianji.common.exceptions.BadRequestException("权限不存在");
        if(lambdaQuery().eq(Privilege::getMethod,privilege.getMethod()).eq(Privilege::getUri,privilege.getUri()).ne(Privilege::getId,privilege.getId()).exists())throw new CommonException(PRIVILEGE_EXISTS);
        updateById(privilege);privilegeCache.initPrivilegesCache(listPrivilegeRoles());
    }
    private List<Long> checkedIds(List<Long> ids){
        if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(id->id==null || id<=0))throw new com.tianji.common.exceptions.BadRequestException("一次需指定 1 至 100 个权限");
        return ids.stream().distinct().toList();
    }
}

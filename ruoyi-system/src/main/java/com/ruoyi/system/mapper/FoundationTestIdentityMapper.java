package com.ruoyi.system.mapper;

import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Param;

public interface FoundationTestIdentityMapper
{
    SysRole selectRoleByKey(String roleKey);

    Set<String> selectPermissionKeysByRoleId(Long roleId);

    SysDept selectDepartmentByCode(String deptCode);

    SysDept selectDepartmentByName(String deptName);

    int insertDepartment(SysDept department);

    SysUser selectAnyUserByUserName(String userName);

    int insertUser(SysUser user);

    List<Long> selectRoleIdsByUserId(Long userId);

    int deleteRoleLinksByUserId(Long userId);

    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int updateTestUserPlacement(@Param("userId") Long userId, @Param("deptId") Long deptId);
}

package com.ruoyi.system.foundation;

import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.mapper.FoundationTestIdentityMapper;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoundationTestIdentityProvisioningService
{
    private static final String ACTIVE = "0";
    private static final String NOT_DELETED = "0";

    private final FoundationTestIdentityMapper mapper;
    private final FoundationTestPasswordPolicy passwordPolicy;
    private final BCryptPasswordEncoder passwordEncoder;

    public FoundationTestIdentityProvisioningService(FoundationTestIdentityMapper mapper,
        FoundationTestPasswordPolicy passwordPolicy, BCryptPasswordEncoder passwordEncoder)
    {
        this.mapper = mapper;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(rollbackFor = Exception.class)
    public FoundationTestIdentityProvisioningResult provision(String rawPassword)
    {
        passwordPolicy.validate(rawPassword);
        Map<String, SysRole> rolesByKey = validateRoles();
        validateForbiddenRoleBoundary();

        Map<String, SysDept> existingDepartments = preflightDepartments();
        Map<String, SysUser> existingUsers = preflightUsers();
        Counts counts = new Counts();
        Map<String, SysDept> departmentsByCode = provisionDepartments(existingDepartments, counts);
        provisionUsers(rawPassword, existingUsers, departmentsByCode, rolesByKey, counts);
        return counts.result();
    }

    private Map<String, SysRole> validateRoles()
    {
        Map<String, SysRole> rolesByKey = new HashMap<>();
        for (String roleKey : FoundationTestIdentityCatalog.requiredRoleKeys())
        {
            SysRole role = mapper.selectRoleByKey(roleKey);
            if (role == null)
            {
                throw failure(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_ROLE_MISSING, roleKey);
            }
            if (!roleKey.equals(role.getRoleKey()) || !ACTIVE.equals(role.getStatus())
                || !NOT_DELETED.equals(role.getDelFlag()))
            {
                throw failure(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH, roleKey);
            }
            Set<String> expectedPermissions = FoundationTestIdentityCatalog.governanceRolePermissions().get(roleKey);
            if (expectedPermissions != null)
            {
                Set<String> actualPermissions = mapper.selectPermissionKeysByRoleId(role.getRoleId());
                if (!expectedPermissions.equals(actualPermissions == null ? Set.of() : actualPermissions))
                {
                    throw failure(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH, roleKey);
                }
            }
            rolesByKey.put(roleKey, role);
        }
        return rolesByKey;
    }

    private void validateForbiddenRoleBoundary()
    {
        Set<String> requestedRoles = FoundationTestIdentityCatalog.users().stream()
            .map(FoundationTestIdentityCatalog.UserSpec::roleKey).collect(Collectors.toSet());
        if (!java.util.Collections.disjoint(requestedRoles, FoundationTestIdentityCatalog.forbiddenQ003RoleKeys()))
        {
            throw failure(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH, "Q-003");
        }
    }

    private Map<String, SysDept> preflightDepartments()
    {
        Map<String, SysDept> existingByCode = new HashMap<>();
        for (FoundationTestIdentityCatalog.DepartmentSpec spec : FoundationTestIdentityCatalog.departments())
        {
            SysDept byCode = mapper.selectDepartmentByCode(spec.code());
            SysDept byName = mapper.selectDepartmentByName(spec.name());
            if (byCode == null && byName != null)
            {
                throw departmentConflict(spec.code());
            }
            if (byCode != null)
            {
                boolean sameNamedRow = byName == null || Objects.equals(byCode.getDeptId(), byName.getDeptId());
                if (!sameNamedRow || !spec.code().equals(byCode.getDeptCode())
                    || !spec.name().equals(byCode.getDeptName())
                    || !FoundationTestIdentityCatalog.CREATED_BY.equals(byCode.getCreateBy())
                    || !ACTIVE.equals(byCode.getStatus()) || !NOT_DELETED.equals(byCode.getDelFlag()))
                {
                    throw departmentConflict(spec.code());
                }
                existingByCode.put(spec.code(), byCode);
            }
        }
        validateExistingDepartmentTree(existingByCode);
        return existingByCode;
    }

    private void validateExistingDepartmentTree(Map<String, SysDept> existingByCode)
    {
        for (FoundationTestIdentityCatalog.DepartmentSpec spec : FoundationTestIdentityCatalog.departments())
        {
            SysDept department = existingByCode.get(spec.code());
            if (department == null)
            {
                continue;
            }
            SysDept parent = spec.parentCode() == null ? null : existingByCode.get(spec.parentCode());
            if (spec.parentCode() != null && parent == null)
            {
                throw departmentConflict(spec.code());
            }
            Long expectedParentId = parent == null ? 0L : parent.getDeptId();
            String expectedAncestors = parent == null ? "0" : parent.getAncestors() + "," + parent.getDeptId();
            if (!Objects.equals(expectedParentId, department.getParentId())
                || !expectedAncestors.equals(department.getAncestors()))
            {
                throw departmentConflict(spec.code());
            }
        }
    }

    private Map<String, SysUser> preflightUsers()
    {
        Map<String, SysUser> usersByName = new HashMap<>();
        for (FoundationTestIdentityCatalog.UserSpec spec : FoundationTestIdentityCatalog.users())
        {
            List<SysUser> matchingUsers = mapper.selectUsersByUserName(spec.userName());
            SysUser activeMarkedUser = null;
            for (SysUser existing : matchingUsers == null ? List.<SysUser>of() : matchingUsers)
            {
                if (!FoundationTestIdentityCatalog.TEST_USER_TYPE.equals(existing.getUserType())
                    || existing.getRemark() == null
                    || !existing.getRemark().startsWith(FoundationTestIdentityCatalog.USER_MARKER)
                    || !FoundationTestIdentityCatalog.CREATED_BY.equals(existing.getCreateBy()))
                {
                    throw failure(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_USER_CONFLICT,
                        spec.userName());
                }
                if ("2".equals(existing.getDelFlag()))
                {
                    continue;
                }
                if (!NOT_DELETED.equals(existing.getDelFlag()) || !ACTIVE.equals(existing.getStatus())
                    || activeMarkedUser != null)
                {
                    throw failure(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_USER_CONFLICT,
                        spec.userName());
                }
                activeMarkedUser = existing;
            }
            if (activeMarkedUser != null)
            {
                usersByName.put(spec.userName(), activeMarkedUser);
            }
        }
        return usersByName;
    }

    private Map<String, SysDept> provisionDepartments(Map<String, SysDept> existingByCode, Counts counts)
    {
        Map<String, SysDept> departmentsByCode = new HashMap<>();
        for (FoundationTestIdentityCatalog.DepartmentSpec spec : FoundationTestIdentityCatalog.departments())
        {
            SysDept parent = spec.parentCode() == null ? null : departmentsByCode.get(spec.parentCode());
            Long parentId = parent == null ? 0L : parent.getDeptId();
            String ancestors = parent == null ? "0" : parent.getAncestors() + "," + parent.getDeptId();
            SysDept department = existingByCode.get(spec.code());
            if (department == null)
            {
                department = newDepartment(spec, parentId, ancestors);
                requireSingleWrite(mapper.insertDepartment(department), "department");
                requireGeneratedId(department.getDeptId(), "department");
                counts.created++;
            }
            else
            {
                if (!Objects.equals(parentId, department.getParentId())
                    || !ancestors.equals(department.getAncestors()))
                {
                    throw departmentConflict(spec.code());
                }
                counts.reused++;
            }
            departmentsByCode.put(spec.code(), department);
        }
        return departmentsByCode;
    }

    private void provisionUsers(String rawPassword, Map<String, SysUser> existingUsers,
        Map<String, SysDept> departmentsByCode, Map<String, SysRole> rolesByKey, Counts counts)
    {
        for (FoundationTestIdentityCatalog.UserSpec spec : FoundationTestIdentityCatalog.users())
        {
            SysDept department = departmentsByCode.get(spec.departmentCode());
            SysRole role = rolesByKey.get(spec.roleKey());
            SysUser existing = existingUsers.get(spec.userName());
            if (existing == null || !NOT_DELETED.equals(existing.getDelFlag()))
            {
                SysUser user = newUser(spec, department.getDeptId(), passwordEncoder.encode(rawPassword));
                requireSingleWrite(mapper.insertUser(user), "user");
                requireGeneratedId(user.getUserId(), "user");
                requireSingleWrite(mapper.insertUserRole(user.getUserId(), role.getRoleId()), "user-role");
                counts.created += 2;
                continue;
            }

            if (Objects.equals(existing.getDeptId(), department.getDeptId()))
            {
                counts.reused++;
            }
            else
            {
                requireSingleWrite(mapper.updateTestUserPlacement(existing.getUserId(), department.getDeptId()),
                    "user-placement");
                counts.repaired++;
            }

            if (existing.getPwdUpdateDate() == null)
            {
                requireSingleWrite(mapper.activateTestUser(existing.getUserId()), "user-password-activation");
                counts.repaired++;
            }

            List<Long> roleIds = mapper.selectRoleIdsByUserId(existing.getUserId());
            if (roleIds != null && roleIds.size() == 1 && Objects.equals(roleIds.get(0), role.getRoleId()))
            {
                counts.reused++;
            }
            else
            {
                int deletedLinks = mapper.deleteRoleLinksByUserId(existing.getUserId());
                if (roleIds != null && !roleIds.isEmpty() && deletedLinks != roleIds.size())
                {
                    throw failure(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_USER_CONFLICT,
                        spec.userName());
                }
                requireSingleWrite(mapper.insertUserRole(existing.getUserId(), role.getRoleId()), "user-role");
                counts.repaired++;
            }
        }
    }

    private SysDept newDepartment(FoundationTestIdentityCatalog.DepartmentSpec spec, Long parentId,
        String ancestors)
    {
        SysDept department = new SysDept();
        department.setParentId(parentId);
        department.setAncestors(ancestors);
        department.setDeptName(spec.name());
        department.setDeptCode(spec.code());
        department.setOrderNum(spec.orderNum());
        department.setStatus(ACTIVE);
        department.setDelFlag(NOT_DELETED);
        department.setCreateBy(FoundationTestIdentityCatalog.CREATED_BY);
        return department;
    }

    private SysUser newUser(FoundationTestIdentityCatalog.UserSpec spec, Long deptId, String encodedPassword)
    {
        SysUser user = new SysUser();
        user.setDeptId(deptId);
        user.setUserName(spec.userName());
        user.setNickName(spec.nickName());
        user.setUserType(FoundationTestIdentityCatalog.TEST_USER_TYPE);
        user.setEmail("");
        user.setPhonenumber("");
        user.setSex("2");
        user.setAvatar("");
        user.setPassword(encodedPassword);
        user.setPwdUpdateDate(new Date());
        user.setStatus(ACTIVE);
        user.setDelFlag(NOT_DELETED);
        user.setCreateBy(FoundationTestIdentityCatalog.CREATED_BY);
        user.setRemark(FoundationTestIdentityCatalog.USER_MARKER + "|" + spec.roleKey());
        return user;
    }

    private FoundationTestIdentityException departmentConflict(String code)
    {
        return failure(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT, code);
    }

    private FoundationTestIdentityException failure(FoundationTestIdentityErrorCode code, String objectKey)
    {
        return new FoundationTestIdentityException(code, objectKey);
    }

    private void requireSingleWrite(int affectedRows, String objectKey)
    {
        if (affectedRows != 1)
        {
            throw new IllegalStateException("Foundation test identity write failed [" + objectKey + "]");
        }
    }

    private void requireGeneratedId(Long id, String objectKey)
    {
        if (id == null)
        {
            throw new IllegalStateException("Foundation test identity generated key missing [" + objectKey + "]");
        }
    }

    private static final class Counts
    {
        private int created;
        private int reused;
        private int repaired;

        private FoundationTestIdentityProvisioningResult result()
        {
            return new FoundationTestIdentityProvisioningResult(created, reused, repaired);
        }
    }
}

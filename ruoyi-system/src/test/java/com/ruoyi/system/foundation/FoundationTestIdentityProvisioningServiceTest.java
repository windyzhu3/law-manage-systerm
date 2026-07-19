package com.ruoyi.system.foundation;

import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT;
import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH;
import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_ROLE_MISSING;
import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_USER_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.mapper.FoundationTestIdentityMapper;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

class FoundationTestIdentityProvisioningServiceTest
{
    private static final String RAW_PASSWORD = "Foundation!234";
    private static final Map<String, Set<String>> GOVERNANCE_PERMISSIONS = Map.of(
        "foundation_product_owner", Set.of(
            "todo:decision:view", "todo:decision:edit", "todo:admission:view", "todo:admission:edit"),
        "foundation_security_reviewer", Set.of("todo:admission:view", "todo:admission:edit"),
        "foundation_arch_dba_reviewer", Set.of(
            "todo:admission:view", "todo:admission:edit", "todo:admission:export"),
        "foundation_qa_acceptor", Set.of("todo:admission:view", "todo:admission:edit"),
        "foundation_independent_reviewer", Set.of("todo:admission:view", "todo:admission:edit"));

    private FoundationTestIdentityMapper mapper;
    private FoundationTestPasswordPolicy passwordPolicy;
    private BCryptPasswordEncoder passwordEncoder;
    private BCryptPasswordEncoder realPasswordEncoder;
    private FoundationTestIdentityProvisioningService service;
    private Map<String, SysRole> roles;

    @BeforeEach
    void setUp()
    {
        mapper = mock(FoundationTestIdentityMapper.class);
        passwordPolicy = mock(FoundationTestPasswordPolicy.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        realPasswordEncoder = new BCryptPasswordEncoder();
        service = new FoundationTestIdentityProvisioningService(mapper, passwordPolicy, passwordEncoder);
        roles = stubValidRoles();
        when(passwordEncoder.encode(RAW_PASSWORD)).thenAnswer(
            invocation -> realPasswordEncoder.encode(invocation.getArgument(0)));
        when(mapper.insertUserRole(anyLong(), anyLong())).thenReturn(1);
        when(mapper.updateTestUserPlacement(anyLong(), anyLong())).thenReturn(1);
    }

    @Test
    void createsSixDepartmentsTwelveUsersAndTwelveExactRoleLinksOnFirstRun()
    {
        List<SysDept> insertedDepartments = new ArrayList<>();
        List<SysUser> insertedUsers = new ArrayList<>();
        List<UserRoleLink> insertedRoleLinks = new ArrayList<>();
        stubGeneratedKeys(insertedDepartments, insertedUsers);
        doAnswer(invocation -> {
            insertedRoleLinks.add(new UserRoleLink(invocation.getArgument(0), invocation.getArgument(1)));
            return 1;
        }).when(mapper).insertUserRole(anyLong(), anyLong());

        FoundationTestIdentityProvisioningResult result = service.provision(RAW_PASSWORD);

        verify(passwordPolicy).validate(RAW_PASSWORD);
        verify(mapper, times(FoundationTestIdentityCatalog.requiredRoleKeys().size())).selectRoleByKey(anyString());
        for (Map.Entry<String, Set<String>> expected : GOVERNANCE_PERMISSIONS.entrySet())
        {
            verify(mapper).selectPermissionKeysByRoleId(roles.get(expected.getKey()).getRoleId());
        }
        verify(mapper, times(6)).insertDepartment(any(SysDept.class));
        verify(mapper, times(12)).insertUser(any(SysUser.class));
        verify(passwordEncoder, times(12)).encode(RAW_PASSWORD);
        verify(mapper, times(12)).insertUserRole(anyLong(), anyLong());
        assertExactDepartmentPayloads(insertedDepartments);
        assertExactUserAndRolePayloads(insertedDepartments, insertedUsers, insertedRoleLinks);
        assertEquals(30, result.created());
        assertEquals(0, result.reused());
        assertEquals(0, result.repaired());
    }

    @Test
    void cleanSecondRunReusesRowsWithoutEncodingOrRewritingPasswords()
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        stubExistingUsers(departments);

        FoundationTestIdentityProvisioningResult result = service.provision(RAW_PASSWORD);

        verify(passwordEncoder, never()).encode(anyString());
        verify(mapper, never()).insertDepartment(any());
        verify(mapper, never()).insertUser(any());
        verify(mapper, never()).updateTestUserPlacement(anyLong(), anyLong());
        verify(mapper, never()).deleteRoleLinksByUserId(anyLong());
        verify(mapper, never()).insertUserRole(anyLong(), anyLong());
        assertEquals(0, result.created());
        assertEquals(30, result.reused());
        assertEquals(0, result.repaired());
    }

    @Test
    void repairsOnlyMarkedTestUsersDepartmentAndRoleLinks()
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        stubExistingUsers(departments);
        FoundationTestIdentityCatalog.UserSpec target = FoundationTestIdentityCatalog.users().get(1);
        SysUser misplaced = markedUser(800L, target, 999L, "0");
        when(mapper.selectUsersByUserName(target.userName())).thenReturn(List.of(misplaced));
        when(mapper.selectRoleIdsByUserId(misplaced.getUserId())).thenReturn(List.of(998L, 999L));
        when(mapper.deleteRoleLinksByUserId(misplaced.getUserId())).thenReturn(2);
        Long expectedDept = departments.get(target.departmentCode()).getDeptId();
        Long expectedRole = roles.get(target.roleKey()).getRoleId();

        FoundationTestIdentityProvisioningResult result = service.provision(RAW_PASSWORD);

        verify(mapper).updateTestUserPlacement(misplaced.getUserId(), expectedDept);
        verify(mapper).deleteRoleLinksByUserId(misplaced.getUserId());
        verify(mapper).insertUserRole(misplaced.getUserId(), expectedRole);
        verify(passwordEncoder, never()).encode(anyString());
        assertEquals(2, result.repaired());
    }

    @Test
    void abortsRoleRepairWhenDeleteCountDoesNotMatchPersistedLinks()
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        stubExistingUsers(departments);
        FoundationTestIdentityCatalog.UserSpec target = FoundationTestIdentityCatalog.users().get(0);
        SysUser user = markedUser(801L, target, departments.get(target.departmentCode()).getDeptId(), "0");
        when(mapper.selectUsersByUserName(target.userName())).thenReturn(List.of(user));
        when(mapper.selectRoleIdsByUserId(user.getUserId())).thenReturn(List.of(998L, 999L));
        when(mapper.deleteRoleLinksByUserId(user.getUserId())).thenReturn(1);

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_USER_CONFLICT, failure.getCode());
        verify(mapper).deleteRoleLinksByUserId(user.getUserId());
        verify(mapper, never()).insertUserRole(user.getUserId(), roles.get(target.roleKey()).getRoleId());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void recreatesDeletedMarkedTestUserWithoutOverwritingItsRow()
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        stubExistingUsers(departments);
        FoundationTestIdentityCatalog.UserSpec target = FoundationTestIdentityCatalog.users().get(0);
        SysUser deleted = markedUser(700L, target, departments.get(target.departmentCode()).getDeptId(), "2");
        when(mapper.selectUsersByUserName(target.userName())).thenReturn(List.of(deleted));
        doAnswer(invocation -> {
            SysUser inserted = invocation.getArgument(0);
            inserted.setUserId(1700L);
            return 1;
        }).when(mapper).insertUser(any(SysUser.class));

        service.provision(RAW_PASSWORD);

        verify(mapper).insertUser(any(SysUser.class));
        verify(mapper).insertUserRole(1700L, roles.get(target.roleKey()).getRoleId());
        verify(mapper, never()).updateTestUserPlacement(700L,
            departments.get(target.departmentCode()).getDeptId());
        verify(mapper, never()).deleteRoleLinksByUserId(700L);
    }

    @Test
    void reusesOneActiveMarkedUserWhileAllowingDeletedMarkedHistory()
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        stubExistingUsers(departments);
        FoundationTestIdentityCatalog.UserSpec target = FoundationTestIdentityCatalog.users().get(0);
        SysUser active = markedUser(1701L, target, departments.get(target.departmentCode()).getDeptId(), "0");
        SysUser history = markedUser(701L, target, departments.get(target.departmentCode()).getDeptId(), "2");
        when(mapper.selectUsersByUserName(target.userName())).thenReturn(List.of(history, active));
        when(mapper.selectRoleIdsByUserId(active.getUserId()))
            .thenReturn(List.of(roles.get(target.roleKey()).getRoleId()));

        FoundationTestIdentityProvisioningResult result = service.provision(RAW_PASSWORD);

        verify(passwordEncoder, never()).encode(anyString());
        verify(mapper, never()).insertUser(any());
        assertEquals(30, result.reused());
    }

    @Test
    void rejectsRealUsernameBeforeAnyWrite()
    {
        SysUser real = new SysUser();
        real.setUserName(FoundationTestIdentityCatalog.users().get(0).userName());
        real.setUserType("00");
        real.setDelFlag("2");
        when(mapper.selectUsersByUserName(real.getUserName())).thenReturn(List.of(real));

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_USER_CONFLICT, failure.getCode());
        verifyNoWrites();
    }

    @Test
    void rejectsRealDuplicateEvenWhenAnActiveMarkedUserAlsoExists()
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        FoundationTestIdentityCatalog.UserSpec spec = FoundationTestIdentityCatalog.users().get(0);
        SysUser active = markedUser(703L, spec, departments.get(spec.departmentCode()).getDeptId(), "0");
        SysUser deletedReal = new SysUser();
        deletedReal.setUserId(704L);
        deletedReal.setUserName(spec.userName());
        deletedReal.setUserType("00");
        deletedReal.setDelFlag("2");
        when(mapper.selectUsersByUserName(spec.userName())).thenReturn(List.of(active, deletedReal));

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_USER_CONFLICT, failure.getCode());
        verifyNoWrites();
    }

    @Test
    void rejectsMultipleActiveMarkedRowsBeforeAnyWrite()
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        FoundationTestIdentityCatalog.UserSpec spec = FoundationTestIdentityCatalog.users().get(0);
        SysUser first = markedUser(705L, spec, departments.get(spec.departmentCode()).getDeptId(), "0");
        SysUser second = markedUser(706L, spec, departments.get(spec.departmentCode()).getDeptId(), "0");
        when(mapper.selectUsersByUserName(spec.userName())).thenReturn(List.of(first, second));

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_USER_CONFLICT, failure.getCode());
        verifyNoWrites();
    }

    @Test
    void rejectsDisabledMarkedUserBeforeAnyWrite()
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        FoundationTestIdentityCatalog.UserSpec spec = FoundationTestIdentityCatalog.users().get(0);
        SysUser disabled = markedUser(702L, spec, departments.get(spec.departmentCode()).getDeptId(), "0");
        disabled.setStatus("1");
        when(mapper.selectUsersByUserName(spec.userName())).thenReturn(List.of(disabled));

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_USER_CONFLICT, failure.getCode());
        verifyNoWrites();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1", "3"})
    void rejectsMarkedUserWithUnknownDeleteFlagBeforeAnyWrite(String delFlag)
    {
        Map<String, SysDept> departments = stubExistingDepartments();
        FoundationTestIdentityCatalog.UserSpec spec = FoundationTestIdentityCatalog.users().get(0);
        SysUser invalid = markedUser(707L, spec, departments.get(spec.departmentCode()).getDeptId(), delFlag);
        when(mapper.selectUsersByUserName(spec.userName())).thenReturn(List.of(invalid));

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_USER_CONFLICT, failure.getCode());
        verifyNoWrites();
    }

    @Test
    void rejectsUnmarkedSameNameDepartmentBeforeAnyWrite()
    {
        FoundationTestIdentityCatalog.DepartmentSpec spec = FoundationTestIdentityCatalog.departments().get(0);
        SysDept real = department(44L, "REAL_FIRM", spec.name(), 0L, "0", "admin", "0");
        when(mapper.selectDepartmentByName(spec.name())).thenReturn(real);

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT, failure.getCode());
        verifyNoWrites();
    }

    @Test
    void rejectsReservedDepartmentCodeReturnedWithDifferentCase()
    {
        FoundationTestIdentityCatalog.DepartmentSpec spec = FoundationTestIdentityCatalog.departments().get(0);
        SysDept colliding = department(46L, spec.code().toLowerCase(java.util.Locale.ROOT), spec.name(),
            0L, "0", FoundationTestIdentityCatalog.CREATED_BY, "0");
        when(mapper.selectDepartmentByCode(spec.code())).thenReturn(colliding);
        when(mapper.selectDepartmentByName(spec.name())).thenReturn(colliding);

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT, failure.getCode());
        verifyNoWrites();
    }

    @Test
    void rejectsInvalidReservedDepartmentTreeBeforeAnyWrite()
    {
        FoundationTestIdentityCatalog.DepartmentSpec child = FoundationTestIdentityCatalog.departments().get(1);
        SysDept misplaced = department(45L, child.code(), child.name(), 999L, "0,999",
            FoundationTestIdentityCatalog.CREATED_BY, "0");
        when(mapper.selectDepartmentByCode(child.code())).thenReturn(misplaced);
        when(mapper.selectDepartmentByName(child.name())).thenReturn(misplaced);

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT, failure.getCode());
        verifyNoWrites();
    }

    @Test
    void missingRoleUsesStableErrorAndPreventsWrites()
    {
        String roleKey = FoundationTestIdentityCatalog.requiredRoleKeys().iterator().next();
        when(mapper.selectRoleByKey(roleKey)).thenReturn(null);

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));

        assertEquals(FOUNDATION_TEST_IDENTITIES_ROLE_MISSING, failure.getCode());
        verifyNoWrites();
    }

    @Test
    void disabledDeletedOrPermissionDriftedRoleUsesStableMismatch()
    {
        String roleKey = "foundation_product_owner";
        SysRole role = roles.get(roleKey);
        role.setStatus("1");
        assertRoleMismatch();

        reset(mapper);
        roles = stubValidRoles();
        role = roles.get(roleKey);
        role.setDelFlag("2");
        assertRoleMismatch();

        reset(mapper);
        roles = stubValidRoles();
        when(mapper.selectPermissionKeysByRoleId(roles.get(roleKey).getRoleId()))
            .thenReturn(Set.of("todo:admission:view"));
        assertRoleMismatch();
    }

    @Test
    void resultAndTransactionContractExposeOnlyNonSensitiveCounts() throws Exception
    {
        Set<String> components = java.util.Arrays.stream(
                FoundationTestIdentityProvisioningResult.class.getRecordComponents())
            .map(RecordComponent::getName).collect(Collectors.toSet());
        Transactional transactional = FoundationTestIdentityProvisioningService.class
            .getMethod("provision", String.class).getAnnotation(Transactional.class);

        assertEquals(Set.of("created", "reused", "repaired"), components);
        assertTrue(FoundationTestPasswordPolicy.class.isAnnotationPresent(Component.class));
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
        String contract = FoundationTestIdentityProvisioningResult.class.getDeclaredFields().length + " "
            + FoundationTestIdentityProvisioningResult.class;
        assertFalse(contract.toLowerCase().contains("password"));
        assertFalse(contract.toLowerCase().contains("hash"));
    }

    private void assertRoleMismatch()
    {
        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> service.provision(RAW_PASSWORD));
        assertEquals(FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH, failure.getCode());
        verifyNoWrites();
    }

    private void verifyNoWrites()
    {
        verify(mapper, never()).insertDepartment(any());
        verify(mapper, never()).insertUser(any());
        verify(mapper, never()).updateTestUserPlacement(anyLong(), anyLong());
        verify(mapper, never()).deleteRoleLinksByUserId(anyLong());
        verify(mapper, never()).insertUserRole(anyLong(), anyLong());
        verifyNoInteractions(passwordEncoder);
    }

    private Map<String, SysRole> stubValidRoles()
    {
        Map<String, SysRole> valid = new HashMap<>();
        AtomicLong ids = new AtomicLong(100L);
        for (String roleKey : FoundationTestIdentityCatalog.requiredRoleKeys())
        {
            SysRole role = new SysRole();
            role.setRoleId(ids.incrementAndGet());
            role.setRoleKey(roleKey);
            role.setStatus("0");
            role.setDelFlag("0");
            valid.put(roleKey, role);
            when(mapper.selectRoleByKey(roleKey)).thenReturn(role);
            Set<String> permissions = GOVERNANCE_PERMISSIONS.get(roleKey);
            if (permissions != null)
            {
                when(mapper.selectPermissionKeysByRoleId(role.getRoleId())).thenReturn(permissions);
            }
        }
        return valid;
    }

    private void assertExactDepartmentPayloads(List<SysDept> insertedDepartments)
    {
        assertEquals(FoundationTestIdentityCatalog.departments().size(), insertedDepartments.size());
        Map<String, SysDept> departmentsByCode = insertedDepartments.stream()
            .collect(Collectors.toMap(SysDept::getDeptCode, department -> department));
        for (int index = 0; index < FoundationTestIdentityCatalog.departments().size(); index++)
        {
            FoundationTestIdentityCatalog.DepartmentSpec spec = FoundationTestIdentityCatalog.departments().get(index);
            SysDept actual = insertedDepartments.get(index);
            SysDept parent = spec.parentCode() == null ? null : departmentsByCode.get(spec.parentCode());
            assertEquals(spec.code(), actual.getDeptCode());
            assertEquals(spec.name(), actual.getDeptName());
            assertEquals(spec.orderNum(), actual.getOrderNum());
            assertEquals(parent == null ? 0L : parent.getDeptId(), actual.getParentId());
            assertEquals(parent == null ? "0" : parent.getAncestors() + "," + parent.getDeptId(),
                actual.getAncestors());
            assertEquals(FoundationTestIdentityCatalog.CREATED_BY, actual.getCreateBy());
            assertEquals("0", actual.getStatus());
            assertEquals("0", actual.getDelFlag());
        }
    }

    private void assertExactUserAndRolePayloads(List<SysDept> insertedDepartments, List<SysUser> insertedUsers,
        List<UserRoleLink> insertedRoleLinks)
    {
        assertEquals(FoundationTestIdentityCatalog.users().size(), insertedUsers.size());
        assertEquals(FoundationTestIdentityCatalog.users().size(), insertedRoleLinks.size());
        Map<String, SysDept> departmentsByCode = insertedDepartments.stream()
            .collect(Collectors.toMap(SysDept::getDeptCode, department -> department));
        for (int index = 0; index < FoundationTestIdentityCatalog.users().size(); index++)
        {
            FoundationTestIdentityCatalog.UserSpec spec = FoundationTestIdentityCatalog.users().get(index);
            SysUser actual = insertedUsers.get(index);
            UserRoleLink link = insertedRoleLinks.get(index);
            assertEquals(spec.userName(), actual.getUserName());
            assertEquals(spec.nickName(), actual.getNickName());
            assertEquals(departmentsByCode.get(spec.departmentCode()).getDeptId(), actual.getDeptId());
            assertEquals(FoundationTestIdentityCatalog.TEST_USER_TYPE, actual.getUserType());
            assertEquals(FoundationTestIdentityCatalog.USER_MARKER + "|" + spec.roleKey(), actual.getRemark());
            assertEquals(FoundationTestIdentityCatalog.CREATED_BY, actual.getCreateBy());
            assertEquals("0", actual.getStatus());
            assertEquals("0", actual.getDelFlag());
            assertNotEquals(RAW_PASSWORD, actual.getPassword());
            assertTrue(realPasswordEncoder.matches(RAW_PASSWORD, actual.getPassword()));
            assertEquals(actual.getUserId(), link.userId());
            assertEquals(roles.get(spec.roleKey()).getRoleId(), link.roleId());
        }
    }

    private void stubGeneratedKeys(List<SysDept> insertedDepartments, List<SysUser> insertedUsers)
    {
        AtomicLong departmentIds = new AtomicLong(200L);
        doAnswer(invocation -> {
            SysDept department = invocation.getArgument(0);
            insertedDepartments.add(department);
            department.setDeptId(departmentIds.incrementAndGet());
            return 1;
        }).when(mapper).insertDepartment(any(SysDept.class));
        AtomicLong userIds = new AtomicLong(1000L);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            insertedUsers.add(user);
            user.setUserId(userIds.incrementAndGet());
            return 1;
        }).when(mapper).insertUser(any(SysUser.class));
    }

    private Map<String, SysDept> stubExistingDepartments()
    {
        Map<String, SysDept> departments = new HashMap<>();
        long id = 200L;
        for (FoundationTestIdentityCatalog.DepartmentSpec spec : FoundationTestIdentityCatalog.departments())
        {
            Long parentId = spec.parentCode() == null ? 0L : departments.get(spec.parentCode()).getDeptId();
            String ancestors = spec.parentCode() == null ? "0" : "0," + parentId;
            SysDept department = department(++id, spec.code(), spec.name(), parentId, ancestors,
                FoundationTestIdentityCatalog.CREATED_BY, "0");
            departments.put(spec.code(), department);
            when(mapper.selectDepartmentByCode(spec.code())).thenReturn(department);
            when(mapper.selectDepartmentByName(spec.name())).thenReturn(department);
        }
        return departments;
    }

    private void stubExistingUsers(Map<String, SysDept> departments)
    {
        long id = 1000L;
        for (FoundationTestIdentityCatalog.UserSpec spec : FoundationTestIdentityCatalog.users())
        {
            SysUser user = markedUser(++id, spec, departments.get(spec.departmentCode()).getDeptId(), "0");
            when(mapper.selectUsersByUserName(spec.userName())).thenReturn(List.of(user));
            when(mapper.selectRoleIdsByUserId(user.getUserId())).thenReturn(List.of(roles.get(spec.roleKey()).getRoleId()));
        }
    }

    private SysUser markedUser(Long id, FoundationTestIdentityCatalog.UserSpec spec, Long deptId, String delFlag)
    {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setDeptId(deptId);
        user.setUserName(spec.userName());
        user.setNickName(spec.nickName());
        user.setUserType(FoundationTestIdentityCatalog.TEST_USER_TYPE);
        user.setStatus("0");
        user.setDelFlag(delFlag);
        user.setCreateBy(FoundationTestIdentityCatalog.CREATED_BY);
        user.setRemark(FoundationTestIdentityCatalog.USER_MARKER + "|" + spec.roleKey());
        user.setPassword("existing-hash-must-not-change");
        return user;
    }

    private SysDept department(Long id, String code, String name, Long parentId, String ancestors,
        String createBy, String delFlag)
    {
        SysDept department = new SysDept();
        department.setDeptId(id);
        department.setDeptCode(code);
        department.setDeptName(name);
        department.setParentId(parentId);
        department.setAncestors(ancestors);
        department.setStatus("0");
        department.setDelFlag(delFlag);
        department.setCreateBy(createBy);
        return department;
    }

    private record UserRoleLink(Long userId, Long roleId) {}
}

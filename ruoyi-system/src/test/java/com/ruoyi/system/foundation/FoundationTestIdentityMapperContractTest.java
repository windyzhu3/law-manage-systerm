package com.ruoyi.system.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.Test;

class FoundationTestIdentityMapperContractTest
{
    @Test
    void systemUserExposesAndPersistsUserType() throws Exception
    {
        Method setter = SysUser.class.getMethod("setUserType", String.class);
        Method getter = SysUser.class.getMethod("getUserType");
        SysUser user = new SysUser();

        setter.invoke(user, "99");

        assertEquals("99", getter.invoke(user));
        String xml = resource("/mapper/system/SysUserMapper.xml");
        assertTrue(element(xml, "resultMap", "SysUserResult").contains("property=\"userType\""));
        assertTrue(element(xml, "sql", "selectUserVo").contains("u.user_type"));
        assertTrue(element(xml, "select", "selectUserList").contains("u.user_type"));
        assertTrue(element(xml, "select", "selectAllocatedList").contains("u.user_type"));
        assertTrue(element(xml, "select", "selectUnallocatedList").contains("u.user_type"));
        String insert = element(xml, "insert", "insertUser");
        assertTrue(insert.contains("user_type"));
        assertTrue(insert.contains("#{userType}"));
        assertFalse(element(xml, "update", "updateUser").contains("user_type"),
            "Ordinary user updates must not mutate the test identity marker");
    }

    @Test
    void dedicatedMapperExposesOnlyTheApprovedProvisioningBoundary() throws Exception
    {
        Class<?> mapper = Class.forName("com.ruoyi.system.mapper.FoundationTestIdentityMapper");

        assertMethod(mapper, "selectRoleByKey", SysRole.class, String.class);
        assertMethod(mapper, "selectPermissionKeysByRoleId", Set.class, Long.class);
        assertMethod(mapper, "selectDepartmentByCode", SysDept.class, String.class);
        assertMethod(mapper, "selectDepartmentByName", SysDept.class, String.class);
        assertMethod(mapper, "insertDepartment", int.class, SysDept.class);
        assertMethod(mapper, "selectUsersByUserName", List.class, String.class);
        assertMethod(mapper, "insertUser", int.class, SysUser.class);
        assertMethod(mapper, "selectRoleIdsByUserId", List.class, Long.class);
        assertMethod(mapper, "deleteRoleLinksByUserId", int.class, Long.class);
        assertParameterizedMethod(mapper, "insertUserRole", "userId", "roleId");
        assertParameterizedMethod(mapper, "updateTestUserPlacement", "userId", "deptId");
        assertMethod(mapper, "activateTestUser", int.class, Long.class);
        assertEquals(12, mapper.getDeclaredMethods().length,
            "The dedicated mapper must not gain role or role-menu creation powers");
    }

    @Test
    void dedicatedMapperUsesBoundParametersGeneratedKeysAndAllDeleteStates() throws Exception
    {
        String xml = resource("/mapper/system/FoundationTestIdentityMapper.xml");

        assertFalse(xml.contains("${"), "Provisioning SQL must use bound parameters only");
        String allUsers = normalize(element(xml, "select", "selectUsersByUserName"));
        assertTrue(allUsers.contains("where u.user_name = #{username}"));
        assertFalse(allUsers.matches("(?s).*where u\\.user_name = #\\{username}.*and u\\.del_flag = '0'.*"),
            "Deleted real and test usernames must remain visible to conflict handling");
        assertTrue(allUsers.contains("u.user_type"));
        assertTrue(allUsers.contains("u.create_by"));
        assertTrue(allUsers.contains("u.remark"));
        assertFalse(allUsers.contains("limit"));
        assertFalse(allUsers.contains("order by"));
        assertFalse(allUsers.matches("(?s).*\\slike\\s.*"),
            "Marker classification must use exact Java prefix semantics, not SQL wildcard semantics");

        String byCode = normalize(element(xml, "select", "selectDepartmentByCode"));
        String byName = normalize(element(xml, "select", "selectDepartmentByName"));
        assertTrue(byCode.contains("d.dept_code = #{deptcode}"));
        assertTrue(byName.contains("d.dept_name = #{deptname}"));
        assertTrue(byName.contains("count(*) = 1"));
        assertTrue(byName.contains("having count(*) > 0"),
            "Any duplicate same-name department must produce a conflict sentinel row");
        assertTrue(byName.contains("max(d.dept_name) as dept_name"),
            "The duplicate sentinel must retain one non-null property so MyBatis returns an object");
        String departmentColumns = normalize(element(xml, "sql", "selectFoundationDepartment"));
        assertTrue(departmentColumns.contains("d.create_by"));

        String permissions = normalize(element(xml, "select", "selectPermissionKeysByRoleId"));
        assertTrue(permissions.contains("m.menu_type = 'f'"));
        assertFalse(permissions.contains("m.status"),
            "Exact permission comparison must not hide stored grants on disabled buttons");

        String insertDepartment = element(xml, "insert", "insertDepartment");
        assertTrue(insertDepartment.contains("useGeneratedKeys=\"true\""));
        assertTrue(insertDepartment.contains("keyProperty=\"deptId\""));
        assertTrue(insertDepartment.contains("dept_code"));
        assertTrue(insertDepartment.contains("create_by"));
        assertFalse(insertDepartment.contains("remark"), "sys_dept has no remark column");

        String insertUser = element(xml, "insert", "insertUser");
        assertTrue(insertUser.contains("useGeneratedKeys=\"true\""));
        assertTrue(insertUser.contains("keyProperty=\"userId\""));
        for (String required : Set.of("user_type", "password", "status", "del_flag", "create_by", "remark"))
        {
            assertTrue(insertUser.contains(required), () -> "Missing user insert column: " + required);
        }
    }

    private void assertMethod(Class<?> mapper, String name, Class<?> returnType, Class<?>... parameters)
        throws Exception
    {
        assertEquals(returnType, mapper.getMethod(name, parameters).getReturnType());
    }

    private void assertParameterizedMethod(Class<?> mapper, String name, String first, String second) throws Exception
    {
        Method method = mapper.getMethod(name, Long.class, Long.class);
        assertEquals(int.class, method.getReturnType());
        assertEquals(first, method.getParameters()[0].getAnnotation(Param.class).value());
        assertEquals(second, method.getParameters()[1].getAnnotation(Param.class).value());
    }

    private String resource(String path) throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream(path))
        {
            assertNotNull(input, () -> "Missing mapper resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String element(String xml, String tag, String id)
    {
        Pattern pattern = Pattern.compile("<" + tag + "\\b[^>]*\\bid=\\\"" + Pattern.quote(id)
            + "\\\"[^>]*>.*?</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        assertTrue(matcher.find(), () -> "Missing " + tag + "#" + id);
        return matcher.group();
    }

    private String normalize(String value)
    {
        return value.replaceAll("\\s+", " ").toLowerCase();
    }
}

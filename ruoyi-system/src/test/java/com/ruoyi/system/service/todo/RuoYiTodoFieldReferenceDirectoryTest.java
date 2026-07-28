package com.ruoyi.system.service.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysPostService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class RuoYiTodoFieldReferenceDirectoryTest
{
    @Mock ISysUserService users;
    @Mock ISysDeptService departments;
    @Mock ISysPostService posts;
    @Mock ISysRoleService roles;
    @Mock ISysDictTypeService dictionaries;

    @Test void resolvesUserAndDictionaryLabelsWithoutReturningTheirRawValuesAsLabels()
    {
        SysDept dept=new SysDept();dept.setDeptId(103L);dept.setDeptName("销售一部");
        SysUser user=new SysUser();user.setUserId(11L);user.setUserName("sales01");user.setNickName("张三");
        user.setStatus("0");user.setDept(dept);
        when(users.selectUserList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(user));
        SysDictData valid=new SysDictData();valid.setDictValue("VALID");valid.setDictLabel("有效");
        when(dictionaries.selectDictDataByType("law_first_contact_result")).thenReturn(List.of(valid));
        RuoYiTodoFieldReferenceDirectory directory=new RuoYiTodoFieldReferenceDirectory(
                users,departments,posts,roles,dictionaries,List.of());

        var userResult=directory.resolve("USER_ID","SYSTEM_USER",null,List.of(11L),actor());
        var dictResult=directory.resolve("DICT","SYSTEM_DICTIONARY","law_first_contact_result",List.of("VALID"),actor());

        assertThat(userResult.get(11L).displayValue()).isEqualTo("张三");
        assertThat(userResult.get(11L).meta()).containsEntry("deptName","销售一部");
        assertThat(dictResult.get("VALID").displayValue()).isEqualTo("有效");
    }

    @Test void resolvesAnAlreadyReferencedDepartmentEvenWhenListDataScopeDoesNotReturnIt()
    {
        SysDept dept=new SysDept();dept.setDeptId(103L);dept.setDeptName("研发部门");dept.setStatus("0");
        when(departments.selectDeptById(103L)).thenReturn(dept);
        RuoYiTodoFieldReferenceDirectory directory=new RuoYiTodoFieldReferenceDirectory(
                users,departments,posts,roles,dictionaries,List.of());

        var result=directory.resolve("DEPT_ID","SYSTEM_DEPARTMENT",null,List.of(103L),actor());

        assertThat(result.get(103L).displayValue()).isEqualTo("研发部门");
    }

    private Actor actor(){return new Actor(1L,"admin",103L);}
}

package com.ruoyi.system.service.todo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.spi.TodoBusinessDirectoryAccess;
import com.law.todo.spi.TodoFieldReferenceDirectory;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.SysPost;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysPostService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

/** RuoYi-backed semantic label directory used by Todo configuration and simulation. */
@Component
public class RuoYiTodoFieldReferenceDirectory implements TodoFieldReferenceDirectory
{
    private static final List<String> SEMANTICS=List.of("USER_ID","DEPT_ID","POST_ID","ROLE_KEY","DICT","BUSINESS_REF");
    private static final Map<String,String> BUSINESS_DIRECTORY_ALIASES=Map.of(
            "LEAD_DIRECTORY","LEAD",
            "CUSTOMER_DIRECTORY","CUSTOMER",
            "CONTRACT_DIRECTORY","CONTRACT",
            "CASE_DIRECTORY","CASE",
            "MATTER_DIRECTORY","MATTER");
    private final ISysUserService users;private final ISysDeptService departments;private final ISysPostService posts;
    private final ISysRoleService roles;private final ISysDictTypeService dictionaries;
    private final List<TodoBusinessDirectoryAccess> businessDirectories;

    public RuoYiTodoFieldReferenceDirectory(ISysUserService users,ISysDeptService departments,
            ISysPostService posts,ISysRoleService roles,ISysDictTypeService dictionaries,
            List<TodoBusinessDirectoryAccess> businessDirectories)
    {
        this.users=users;this.departments=departments;this.posts=posts;this.roles=roles;this.dictionaries=dictionaries;
        this.businessDirectories=businessDirectories==null?List.of():List.copyOf(businessDirectories);
    }

    @Override public boolean supports(String semanticType,String optionSource)
    {
        if(!SEMANTICS.contains(semanticType))return false;
        String businessType=physicalBusinessType(optionSource);
        return !"BUSINESS_REF".equals(semanticType)||businessDirectories.stream().anyMatch(value->value.supports(businessType));
    }

    @Override public Map<Object,DisplayReference> resolve(String semanticType,String optionSource,String dictType,
            Collection<?> rawValues,Actor actor)
    {
        List<?> candidates=null;
        Map<Object,DisplayReference> result=new LinkedHashMap<>();
        for(Object raw:rawValues==null?List.of():rawValues)
        {
            DisplayReference match=resolveDirect(semanticType,raw);
            if(match==null)
            {
                if(candidates==null)candidates=candidates(semanticType,optionSource,dictType,actor);
                match=resolveOne(semanticType,raw,candidates);
            }
            if(match!=null)result.put(raw,match);
        }
        return Map.copyOf(result);
    }

    @Override public ReferencePage options(String semanticType,String optionSource,String dictType,String keyword,
            int offset,int limit,Actor actor)
    {
        List<DisplayReference> rows=candidates(semanticType,optionSource,dictType,actor).stream()
                .map(value->display(semanticType,value)).filter(java.util.Objects::nonNull)
                .filter(value->keyword==null||keyword.isBlank()||value.displayValue().toLowerCase()
                        .contains(keyword.trim().toLowerCase())).toList();
        int from=Math.min(Math.max(0,offset),rows.size());int to=Math.min(rows.size(),from+Math.max(1,limit));
        return new ReferencePage(rows.subList(from,to),rows.size());
    }

    private List<?> candidates(String semanticType,String optionSource,String dictType,Actor actor)
    {
        return switch(semanticType)
        {
            case "USER_ID" -> users.selectUserList(new SysUser());
            case "DEPT_ID" -> departments.selectDeptList(new SysDept());
            case "POST_ID" -> posts.selectPostList(new SysPost());
            case "ROLE_KEY" -> roles.selectRoleList(new SysRole());
            case "DICT" -> dictType==null?List.of():dictionaries.selectDictDataByType(dictType);
            case "BUSINESS_REF" ->
            {
                String businessType=physicalBusinessType(optionSource);
                yield business(businessType).search(businessType,null,0,100,actor).rows();
            }
            default -> List.of();
        };
    }

    private TodoBusinessDirectoryAccess business(String type)
    {
        return businessDirectories.stream().filter(value->value.supports(type)).findFirst()
                .orElseThrow(()->new IllegalStateException("Business reference directory unavailable: "+type));
    }

    private String physicalBusinessType(String optionSource)
    {return optionSource==null?null:BUSINESS_DIRECTORY_ALIASES.getOrDefault(optionSource,optionSource);}

    private DisplayReference resolveOne(String semanticType,Object raw,List<?> candidates)
    {
        String key=String.valueOf(raw);
        for(Object candidate:candidates)
        {
            DisplayReference display=display(semanticType,candidate);
            if(display!=null&&String.valueOf(display.rawValue()).equals(key))return new DisplayReference(raw,
                    display.displayValue(),display.meta(),display.selectable(),display.restricted(),display.invalidReason());
        }
        return null;
    }

    private DisplayReference resolveDirect(String semanticType,Object raw)
    {
        Long id=longValue(raw);
        Object value=switch(semanticType)
        {
            case "USER_ID" -> id==null?null:users.selectUserById(id);
            case "DEPT_ID" -> id==null?null:departments.selectDeptById(id);
            case "POST_ID" -> id==null?null:posts.selectPostById(id);
            default -> null;
        };
        DisplayReference resolved=display(semanticType,value);
        return resolved==null?null:new DisplayReference(raw,resolved.displayValue(),resolved.meta(),resolved.selectable(),
                resolved.restricted(),resolved.invalidReason());
    }

    private Long longValue(Object raw)
    {
        if(raw instanceof Number number)return number.longValue();
        try{return raw==null?null:Long.valueOf(String.valueOf(raw));}
        catch(NumberFormatException ignored){return null;}
    }

    private DisplayReference display(String semanticType,Object value)
    {
        if(value==null)return null;
        return switch(semanticType)
        {
            case "USER_ID" -> user((SysUser)value);
            case "DEPT_ID" -> dept((SysDept)value);
            case "POST_ID" -> post((SysPost)value);
            case "ROLE_KEY" -> role((SysRole)value);
            case "DICT" -> dictionary((SysDictData)value);
            case "BUSINESS_REF" -> business((TodoBusinessDirectoryAccess.DirectoryEntry)value);
            default -> null;
        };
    }

    private DisplayReference user(SysUser value)
    {
        Map<String,Object> meta=new LinkedHashMap<>();meta.put("userName",safe(value.getUserName()));
        if(value.getDept()!=null)meta.put("deptName",safe(value.getDept().getDeptName()));
        boolean enabled="0".equals(value.getStatus());
        return new DisplayReference(value.getUserId(),label(value.getNickName(),value.getUserName()),meta,enabled,false,
                enabled?null:"用户已停用");
    }
    private DisplayReference dept(SysDept value)
    {
        Map<String,Object> meta=new LinkedHashMap<>();meta.put("ancestors",safe(value.getAncestors()));
        boolean enabled="0".equals(value.getStatus());
        return new DisplayReference(value.getDeptId(),safe(value.getDeptName()),meta,enabled,false,enabled?null:"部门已停用");
    }
    private DisplayReference post(SysPost value)
    {
        boolean enabled="0".equals(value.getStatus());
        return new DisplayReference(value.getPostId(),label(value.getPostName(),value.getPostCode()),
                Map.of("postCode",safe(value.getPostCode())),enabled,false,enabled?null:"岗位已停用");
    }
    private DisplayReference role(SysRole value)
    {
        boolean enabled="0".equals(value.getStatus());
        return new DisplayReference(value.getRoleKey(),label(value.getRoleName(),value.getRoleKey()),
                Map.of("roleId",value.getRoleId()),enabled,false,enabled?null:"角色已停用");
    }
    private DisplayReference dictionary(SysDictData value)
    {
        boolean enabled="0".equals(value.getStatus());
        return new DisplayReference(value.getDictValue(),safe(value.getDictLabel()),Map.of("listClass",safe(value.getListClass())),
                enabled,false,enabled?null:"字典项已停用");
    }
    private DisplayReference business(TodoBusinessDirectoryAccess.DirectoryEntry value)
    {
        return new DisplayReference(value.businessId(),label(value.businessName(),value.businessNo()),
                Map.of("businessNo",safe(value.businessNo()),"businessType",safe(value.businessType())),true,false,null);
    }

    private String label(String primary,String fallback)
    {return primary==null||primary.isBlank()?safe(fallback):primary;}
    private String safe(Object value){return value==null?"":String.valueOf(value);}
}

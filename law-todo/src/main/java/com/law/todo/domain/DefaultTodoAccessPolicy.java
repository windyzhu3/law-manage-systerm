package com.law.todo.domain;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@Component
public class DefaultTodoAccessPolicy implements TodoAccessPolicy
{
    private final TodoMapper mapper;
    public DefaultTodoAccessPolicy(TodoMapper mapper){this.mapper=mapper;}
    public boolean canClaim(TodoInstance todo,Long userId){if(todo.getOwnerId()!=null)return todo.getOwnerId().equals(userId);for(Map<String,Object> c:mapper.selectCandidates(todo.getTodoId()))if("USER".equals(String.valueOf(c.get("candidate_type")))&&userId.equals(longValue(c.get("candidate_value"))))return true;return false;}
    public boolean canOperate(TodoInstance todo,Long userId){return todo.getOwnerId()!=null&&todo.getOwnerId().equals(userId);}
    public boolean canView(TodoInstance todo,Long userId,Long deptId){return canOperate(todo,userId)||(deptId!=null&&deptId.equals(todo.getOwnerDeptId()))||canClaim(todo,userId);}
    private Long longValue(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}
}

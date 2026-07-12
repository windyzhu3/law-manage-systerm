package com.law.todo.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoQueryService
{
    private final TodoMapper mapper;private final TodoAccessPolicy access;
    public TodoQueryService(TodoMapper mapper,TodoAccessPolicy access){this.mapper=mapper;this.access=access;}
    public Map<String,Object> dashboard(Long userId,Long deptId){return mapper.selectDashboard(userId,deptId);}
    public List<Map<String,Object>> list(Map<String,Object> query,Long userId,Long deptId){query.put("currentUserId",userId);query.put("currentDeptId",deptId);return mapper.selectTodoList(query);}
    public TodoInstance detail(Long id,Long userId,Long deptId){TodoInstance t=mapper.selectById(id);if(t==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");if(!access.canView(t,userId,deptId))throw new TodoException("TODO_ACCESS_DENIED","无权查看该待办");return t;}
}

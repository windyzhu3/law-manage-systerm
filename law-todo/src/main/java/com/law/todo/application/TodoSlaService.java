package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;

@Service
public class TodoSlaService
{
    private final TodoMapper mapper;private final TodoAccessPolicy access;
    public TodoSlaService(TodoMapper mapper,TodoAccessPolicy access){this.mapper=mapper;this.access=access;}
    @Transactional public int scanAndEscalate(LocalDateTime now){List<Map<String,Object>> items=mapper.selectSlaScanItems(now);int changed=0;if(items==null)return 0;for(Map<String,Object> item:items){Long id=Long.valueOf(String.valueOf(value(item,"todo_id","todoId")));int percent=Integer.parseInt(String.valueOf(item.get("percent")));if(percent>=80)changed+=mark(id,"REMINDED_80",now);if(percent>=100)changed+=mark(id,"OVERDUE_100",now);if(percent>=150)changed+=mark(id,"ESCALATED_150",now);}return changed;}
    @Transactional public boolean pause(Long todoId,Long userId,LocalDateTime now){requireOwner(todoId,userId);return mapper.pauseSla(todoId,now)>0;}
    @Transactional public boolean resume(Long todoId,Long userId,LocalDateTime now){requireOwner(todoId,userId);return mapper.resumeSla(todoId,now)>0;}
    private Object value(Map<String,Object> m,String a,String b){return m.containsKey(a)?m.get(a):m.get(b);}
    private int mark(Long id,String threshold,LocalDateTime now){int changed=mapper.markSlaThreshold(id,threshold,now);if(changed>0){mapper.insertSlaNotification(id,threshold,now);if("ESCALATED_150".equals(threshold))mapper.insertSupervisorEscalationNotification(id,now);}return changed;}
    private void requireOwner(Long todoId,Long userId){TodoInstance todo=mapper.selectById(todoId);if(todo==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");if(!access.canOperate(todo,userId))throw new TodoException("TODO_ACCESS_DENIED","无权控制该待办的SLA计时");}
}

package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoSlaService
{
    private final TodoMapper mapper;
    public TodoSlaService(TodoMapper mapper){this.mapper=mapper;}
    @Transactional public int scanAndEscalate(LocalDateTime now){List<Map<String,Object>> items=mapper.selectSlaScanItems(now);int changed=0;if(items==null)return 0;for(Map<String,Object> item:items){Long id=Long.valueOf(String.valueOf(value(item,"todo_id","todoId")));int percent=Integer.parseInt(String.valueOf(item.get("percent")));if(percent>=80)changed+=mapper.markSlaThreshold(id,"REMINDED_80",now);if(percent>=100)changed+=mapper.markSlaThreshold(id,"OVERDUE_100",now);if(percent>=150)changed+=mapper.markSlaThreshold(id,"ESCALATED_150",now);}return changed;}
    @Transactional public boolean pause(Long todoId,LocalDateTime now){return mapper.pauseSla(todoId,now)>0;}
    @Transactional public boolean resume(Long todoId,LocalDateTime now){return mapper.resumeSla(todoId,now)>0;}
    private Object value(Map<String,Object> m,String a,String b){return m.containsKey(a)?m.get(a):m.get(b);}
}

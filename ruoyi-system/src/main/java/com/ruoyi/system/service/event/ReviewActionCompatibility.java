package com.ruoyi.system.service.event;

import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;

final class ReviewActionCompatibility
{
    private ReviewActionCompatibility() { }

    static String resolve(TodoInstance todo,Map<String,Object> payload)
    {
        String stable=text(payload.get("reviewAction"));
        if(!stable.isBlank())return stable;
        String legacy=text(payload.get("action"));
        if(!legacy.isBlank()&&historicallyRequiredAction(todo))return legacy;
        throw new TodoException("TODO_DOD_FIELD_MISSING","缺少完成字段：reviewAction");
    }

    private static boolean historicallyRequiredAction(TodoInstance todo)
    {
        String snapshot=todo.getDodSnapshotJson();
        if(snapshot==null||snapshot.isBlank())return false;
        try
        {
            List<String> required=JSON.parseObject(snapshot).getList("requiredFields",String.class);
            return required!=null&&required.contains("action")&&!required.contains("reviewAction");
        }
        catch(RuntimeException invalidSnapshot){return false;}
    }

    private static String text(Object value){return value==null?"":String.valueOf(value);}
}

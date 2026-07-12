package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.matter.MatterArchiveService;

@Component
public class CaseCloseTodoHandler implements TodoCompletionHandler
{
    private final MatterArchiveService service;public CaseCloseTodoHandler(MatterArchiveService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "CASE_CLOSE_CONFIRM".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){service.closeFromTodo(todo.getBusinessId(),text(p.get("action")),text(p.get("opinion")),text(p.get("feeClearStatus")),new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));}private String text(Object v){return v==null?null:String.valueOf(v);}
}

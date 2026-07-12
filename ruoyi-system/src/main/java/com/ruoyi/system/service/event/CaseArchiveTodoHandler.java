package com.ruoyi.system.service.event;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.matter.MatterArchiveService;

@Component
public class CaseArchiveTodoHandler implements TodoCompletionHandler
{
    private final MatterArchiveService service;private final TodoMapper todos;public CaseArchiveTodoHandler(MatterArchiveService service,TodoMapper todos){this.service=service;this.todos=todos;}
    @Override public boolean supports(TodoInstance todo){return "CASE_ARCHIVE_CONFIRM".equals(todo.getTemplateCode());}
    @SuppressWarnings("unchecked") @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){service.archiveFromTodo(todo.getBusinessId(),text(p.get("action")),text(p.get("opinion")),text(p.get("archiveNo")),(List<Map<String,Object>>)p.getOrDefault("materials",List.of()),new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));if("pass".equals(text(p.get("action"))))todos.cancelActiveByBusiness("MATTER",todo.getBusinessId(),todo.getTodoId(),name);}private String text(Object v){return v==null?null:String.valueOf(v);}
}

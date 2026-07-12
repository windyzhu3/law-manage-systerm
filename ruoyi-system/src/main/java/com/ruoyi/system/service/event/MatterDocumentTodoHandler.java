package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.matter.MatterDocumentService;

@Component
public class MatterDocumentTodoHandler implements TodoCompletionHandler
{
    private final MatterDocumentService service;public MatterDocumentTodoHandler(MatterDocumentService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "MATTER_DOCUMENT_SUPPLY".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){service.supplyFromTodo(todo.getBusinessId(),text(p.get("documentType")),text(p.get("fileName")),text(p.get("fileUrl")),text(p.get("remark")),new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));}private String text(Object v){return v==null?null:String.valueOf(v);}
}

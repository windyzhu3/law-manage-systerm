package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoRoutingService
{
    private final TodoMapper mapper;
    public TodoRoutingService(TodoMapper mapper){this.mapper=mapper;}
    @Transactional public TodoInstance createNext(TodoInstance previous,Long templateVersionId,String title,String businessType,Long businessId)
    {
        String key=previous.getTodoId()+":"+templateVersionId;TodoInstance existing=mapper.selectByNextKey(key);if(existing!=null)return existing;
        TodoInstance next=new TodoInstance();next.setTodoNo("TD"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase());
        next.setTemplateId(previous.getTemplateId());next.setTemplateVersionId(templateVersionId);next.setTitle(title);next.setBusinessType(businessType);next.setBusinessId(businessId);next.setBusinessNo(previous.getBusinessNo());next.setStatus("CREATED");next.setPriority("NORMAL");next.setSlaStatus("NORMAL");next.setCreatedAt(LocalDateTime.now());next.setPreviousTodoId(previous.getTodoId());next.setRootTodoId(previous.getRootTodoId()==null?previous.getTodoId():previous.getRootTodoId());next.setNextIdempotencyKey(key);mapper.insertInstance(next);return next;
    }
}

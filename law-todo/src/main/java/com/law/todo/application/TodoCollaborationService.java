package com.law.todo.application;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoCollaborationService
{
    private final TodoMapper mapper;public TodoCollaborationService(TodoMapper mapper){this.mapper=mapper;}
    @Transactional public int addAttachment(Long todoId,String actionId,String type,String fileName,String fileUrl,Long uploaderId){if(fileUrl==null||fileUrl.isBlank())throw new TodoException("TODO_ATTACHMENT_URL_REQUIRED","附件地址不能为空");Map<String,Object> v=new HashMap<>();v.put("todoId",todoId);v.put("actionId",actionId);v.put("attachmentType",type);v.put("fileName",fileName);v.put("fileUrl",fileUrl);v.put("uploaderId",uploaderId);return mapper.insertAttachment(v);}
    @Transactional public int addCc(Long todoId,Long userId,String type){return mapper.insertCc(todoId,userId,type==null?"CC":type);}
    @Transactional public int addCandidate(Long todoId,String type,Long value){Map<String,Object> c=new HashMap<>();c.put("todoId",todoId);c.put("candidateType",type);c.put("candidateValue",value);return mapper.insertCandidate(c);}
}

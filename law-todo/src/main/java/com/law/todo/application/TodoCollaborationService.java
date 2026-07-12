package com.law.todo.application;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoCollaborationService
{
    private final TodoMapper mapper;private final TodoAccessPolicy access;public TodoCollaborationService(TodoMapper mapper,TodoAccessPolicy access){this.mapper=mapper;this.access=access;}
    @Transactional public int addAttachment(Long todoId,String actionId,String type,String fileName,String fileUrl,Long uploaderId){if(fileUrl==null||fileUrl.isBlank())throw new TodoException("TODO_ATTACHMENT_URL_REQUIRED","附件地址不能为空");requireOwner(todoId,uploaderId);Map<String,Object> v=new HashMap<>();v.put("todoId",todoId);v.put("actionId",actionId);v.put("attachmentType",type);v.put("fileName",fileName);v.put("fileUrl",fileUrl);v.put("uploaderId",uploaderId);return mapper.insertAttachment(v);}
    @Transactional public int addCc(Long todoId,Long userId,String type,Long operatorId){requireParticipant(userId);requireOwner(todoId,operatorId);return mapper.insertCc(todoId,userId,type==null?"CC":type);}
    @Transactional public int addCandidate(Long todoId,String type,Long value,Long operatorId){requireParticipant(value);requireOwner(todoId,operatorId);if(!java.util.Set.of("USER","ROLE","DEPT","POST").contains(type))throw new TodoException("TODO_CANDIDATE_TYPE_INVALID","候选人类型无效");Map<String,Object> c=new HashMap<>();c.put("todoId",todoId);c.put("candidateType",type);c.put("candidateValue",value);return mapper.insertCandidate(c);}
    private void requireOwner(Long todoId,Long userId){TodoInstance todo=mapper.selectById(todoId);if(todo==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");if(!access.canOperate(todo,userId))throw new TodoException("TODO_ACCESS_DENIED","无权修改该待办的协作信息");}
    private void requireParticipant(Long value){if(value==null||value<=0)throw new TodoException("TODO_PARTICIPANT_REQUIRED","协作参与者不能为空");}
}

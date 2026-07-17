package com.law.todo.application;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoAutoActionResultRecorder
{
    private final TodoMapper mapper;
    public TodoAutoActionResultRecorder(TodoMapper mapper){this.mapper=mapper;}

    @Transactional
    public void record(Map<String,Object> outcome,Map<String,Object> audit)
    {
        if(mapper.completeAutoActionExecution(outcome)<=0)throw new TodoException("TODO_AUTO_ACTION_RESULT_CONFLICT","Auto action result could not be recorded");
        if(mapper.insertAutoActionAudit(audit)<=0)throw new TodoException("TODO_AUTO_ACTION_AUDIT_FAILED","Immutable auto action audit could not be recorded");
    }

    @Transactional
    public boolean finalizeStaleDead(String executionKey,int expectedAttempt,int finalAttempt,java.time.LocalDateTime staleBefore,
            java.time.LocalDateTime now,String errorCode,String errorMessage,Map<String,Object> audit)
    {
        if(mapper.finalizeStaleAutoActionDead(executionKey,expectedAttempt,finalAttempt,staleBefore,now,errorCode,errorMessage)<=0)return false;
        if(mapper.insertAutoActionAudit(audit)<=0)throw new TodoException("TODO_AUTO_ACTION_AUDIT_FAILED","Immutable auto action audit could not be recorded");
        return true;
    }
}

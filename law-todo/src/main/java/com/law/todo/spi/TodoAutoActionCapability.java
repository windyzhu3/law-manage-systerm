package com.law.todo.spi;

import java.util.Set;
import java.util.Objects;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.model.TodoInstance;

/** A deliberately small, code-owned capability surface for scheduled actions. */
public interface TodoAutoActionCapability
{
    Set<String> ALLOWED_ACTION_TYPES = Set.of(
            "COMPLETE_DEFAULT", "RETURN_DEFAULT", "ESCALATE", "TRANSFER", "RETURN_POOL");

    String actionType();

    AutoActionResult execute(TodoInstance todo, AutoActionRule rule, Actor serviceActor);

    enum AutoActionStatus { SUCCESS, RETRY, DEAD }
    record AutoActionResult(AutoActionStatus status,String errorCode,String errorMessage)
    {
        public AutoActionResult { Objects.requireNonNull(status,"status"); }
        public static AutoActionResult success(){return new AutoActionResult(AutoActionStatus.SUCCESS,null,null);}
        public static AutoActionResult retry(String code,String message){return new AutoActionResult(AutoActionStatus.RETRY,code,message);}
        public static AutoActionResult dead(String code,String message){return new AutoActionResult(AutoActionStatus.DEAD,code,message);}
    }
}

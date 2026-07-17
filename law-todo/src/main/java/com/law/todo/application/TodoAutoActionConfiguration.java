package com.law.todo.application;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.law.todo.spi.TodoAutoActionCapability.AutoActionResult;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoAutoActionCapability;

@Configuration
public class TodoAutoActionConfiguration
{
    @Bean TodoAutoActionCapability completeDefaultCapability(TodoCommandService commands){return capability("COMPLETE_DEFAULT",(todo,rule,actor)->{commands.autoComplete(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    @Bean TodoAutoActionCapability returnDefaultCapability(TodoCommandService commands){return capability("RETURN_DEFAULT",(todo,rule,actor)->{commands.autoReturn(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    @Bean TodoAutoActionCapability escalationCapability(TodoCommandService commands){return capability("ESCALATE",(todo,rule,actor)->{commands.autoEscalate(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    @Bean TodoAutoActionCapability transferCapability(TodoCommandService commands){return capability("TRANSFER",(todo,rule,actor)->{commands.autoTransfer(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    @Bean TodoAutoActionCapability returnPoolCapability(TodoCommandService commands){return capability("RETURN_POOL",(todo,rule,actor)->{commands.autoReturnPool(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    private TodoAutoActionCapability capability(String type,Executor executor){return new TodoAutoActionCapability(){public String actionType(){return type;}public AutoActionResult execute(TodoInstance todo,AutoActionRule rule,Actor actor){return executor.execute(todo,rule,actor);}};}
    @SuppressWarnings("unchecked") private static ActionCommand command(Long todoId,AutoActionRule rule){Map<String,Object> config=rule.config();Map<String,Object> fields=config.get("fields") instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();java.util.ArrayList<Long> files=new java.util.ArrayList<>();if(config.get("fileObjectIds") instanceof List<?> values)for(Object value:values)files.add(Long.valueOf(String.valueOf(value)));Map<String,Object> payload=new java.util.LinkedHashMap<>(fields);if(config.get("targetOwnerId")!=null)payload.put("targetOwnerId",config.get("targetOwnerId"));return new ActionCommand("AUTO:"+todoId+":"+config.get("ruleKey"),String.valueOf(config.getOrDefault("reason","Scheduled controlled auto action")),payload,files);}
    @FunctionalInterface private interface Executor{AutoActionResult execute(TodoInstance todo,AutoActionRule rule,Actor actor);}
}

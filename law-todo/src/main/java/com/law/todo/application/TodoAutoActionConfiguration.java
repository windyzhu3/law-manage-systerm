package com.law.todo.application;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.law.todo.spi.TodoAutoActionCapability.AutoActionResult;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.notification.TodoNotificationPort;
import com.law.todo.notification.TodoNotificationPort.NotificationCommand;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoSupervisorPort;

@Configuration
public class TodoAutoActionConfiguration
{
    @Bean TodoAutoActionCapability completeDefaultCapability(TodoCommandService commands){return capability("COMPLETE_DEFAULT",(todo,rule,actor)->{commands.autoComplete(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    @Bean TodoAutoActionCapability returnDefaultCapability(TodoCommandService commands){return capability("RETURN_DEFAULT",(todo,rule,actor)->{commands.autoReturn(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    @Bean TodoAutoActionCapability escalationCapability(TodoCommandService commands,TodoNotificationPort notifications,TodoSupervisorPort supervisors){return capability("ESCALATE",(todo,rule,actor)->{ActionCommand action=command(todo.getTodoId(),rule);commands.autoEscalate(todo.getTodoId(),action,actor);notifyEscalation(todo,rule,action,notifications,supervisors);return AutoActionResult.success();});}
    @Bean TodoAutoActionCapability transferCapability(TodoCommandService commands){return capability("TRANSFER",(todo,rule,actor)->{commands.autoTransfer(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    @Bean TodoAutoActionCapability returnPoolCapability(TodoCommandService commands){return capability("RETURN_POOL",(todo,rule,actor)->{commands.autoReturnPool(todo.getTodoId(),command(todo.getTodoId(),rule),actor);return AutoActionResult.success();});}
    private TodoAutoActionCapability capability(String type,Executor executor){return new TodoAutoActionCapability(){public String actionType(){return type;}public AutoActionResult execute(TodoInstance todo,AutoActionRule rule,Actor actor){return executor.execute(todo,rule,actor);}};}
    @SuppressWarnings("unchecked") private static ActionCommand command(Long todoId,AutoActionRule rule){Map<String,Object> config=rule.config();Map<String,Object> fields=config.get("fields") instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();java.util.ArrayList<Long> files=new java.util.ArrayList<>();if(config.get("fileObjectIds") instanceof List<?> values)for(Object value:values)files.add(Long.valueOf(String.valueOf(value)));Map<String,Object> payload=new java.util.LinkedHashMap<>(fields);if(config.get("targetOwnerId")!=null)payload.put("targetOwnerId",config.get("targetOwnerId"));return new ActionCommand("AUTO:"+todoId+":"+config.get("ruleKey"),String.valueOf(config.getOrDefault("reason","Scheduled controlled auto action")),payload,files);}
    private static void notifyEscalation(TodoInstance todo,AutoActionRule rule,ActionCommand action,TodoNotificationPort notifications,TodoSupervisorPort supervisors)
    {
        String trigger=String.valueOf(rule.config().get("triggerAt"));
        String title="Todo escalation: "+String.valueOf(todo.getTitle());
        String source=sourceKey(action.actionId(),trigger);
        List<Long> recipients=supervisors.supervisors(todo.getOwnerId(),todo.getOwnerDeptId());
        if(recipients==null)return;
        recipients.stream().filter(value->value!=null&&value>0).distinct().sorted().forEach(recipient->notifications.send(
                new NotificationCommand(source,todo.getTodoId(),recipient,"AUTO_ESCALATE_"+trigger,limit(title,200),limit(action.opinion(),1000))));
    }
    private static String sourceKey(String actionId,String trigger)
    {
        String canonical=actionId+":"+trigger;
        try{return "AUTO_ESCALATE:"+java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA-256 is required",unavailable);}
    }
    private static String limit(String value,int max){return value==null?null:value.length()<=max?value:value.substring(0,max);}
    @FunctionalInterface private interface Executor{AutoActionResult execute(TodoInstance todo,AutoActionRule rule,Actor actor);}
}

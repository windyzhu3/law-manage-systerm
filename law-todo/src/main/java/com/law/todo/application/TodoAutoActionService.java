package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoAutoActionCapability.AutoActionResult;
import com.law.todo.spi.TodoAutoActionCapability.AutoActionStatus;
import com.law.todo.expression.ConditionEvaluator;
import com.law.todo.expression.ConditionValidator;

@Service
public class TodoAutoActionService
{
    public static final Actor SERVICE_ACTOR = new Actor(-1L, "TODO_AUTO_ACTION", null);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_RETRY_MINUTES = 5;

    private final TodoMapper mapper;
    private final Map<String, TodoAutoActionCapability> capabilities;
    private final TodoAutoActionResultRecorder recorder;
    private final TodoDefinitionCodec codec = new TodoDefinitionCodec();

    public TodoAutoActionService(TodoMapper mapper, List<TodoAutoActionCapability> capabilities)
    {
        this(mapper,capabilities,new TodoAutoActionResultRecorder(mapper));
    }

    @Autowired
    public TodoAutoActionService(TodoMapper mapper, List<TodoAutoActionCapability> capabilities,TodoAutoActionResultRecorder recorder)
    {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.recorder=Objects.requireNonNull(recorder,"recorder");
        List<TodoAutoActionCapability> supplied = capabilities == null ? List.of() : capabilities;
        Map<String, TodoAutoActionCapability> registry;
        try
        {
            registry = supplied.stream().collect(Collectors.toMap(TodoAutoActionCapability::actionType,
                    Function.identity(), (left, right) -> { throw new IllegalStateException("Duplicate auto-action capability: " + left.actionType()); },
                    LinkedHashMap::new));
        }
        catch (NullPointerException invalid)
        {
            throw new IllegalStateException("Auto-action capabilities require an action type", invalid);
        }
        for (String type : registry.keySet())
            if (!TodoAutoActionCapability.ALLOWED_ACTION_TYPES.contains(type))
                throw new IllegalStateException("Capability is not allow-listed: " + type);
        this.capabilities = Collections.unmodifiableMap(registry);
    }

    public List<String> capabilityTypes()
    {
        return capabilities.keySet().stream().sorted().toList();
    }

    /**
     * Executes one immutable definition rule. The database claim is the concurrency gate;
     * business mutation remains inside TodoCommandService through a registered capability.
     */
    public AutoActionResult execute(AutoActionRule rule, TodoInstance todo, LocalDateTime now)
    {
        Objects.requireNonNull(rule, "rule");Objects.requireNonNull(todo, "todo");Objects.requireNonNull(now, "now");
        Map<String,Object> config = rule.config();RuntimeRule runtime=validateRuntimeRule(config);
        String actionType=runtime.actionType();String ruleKey=runtime.ruleKey();TodoAutoActionCapability capability=runtime.capability();
        String executionKey = executionKey(todo.getTodoId(), ruleKey);
        int maxAttempts = positiveInt(config.get("maxAttempts"), DEFAULT_MAX_ATTEMPTS, "maxAttempts");
        int retryMinutes = positiveInt(config.get("retryDelayMinutes"), DEFAULT_RETRY_MINUTES, "retryDelayMinutes");
        int claimTimeoutMinutes=positiveInt(config.get("claimTimeoutMinutes"),15,"claimTimeoutMinutes");
        Map<String,Object> claim = new HashMap<>();claim.put("executionKey", executionKey);claim.put("todoId", todo.getTodoId());
        claim.put("ruleKey", ruleKey);claim.put("actionType", actionType);claim.put("now", now);
        int inserted=mapper.insertAutoActionExecutionIfAbsent(claim);
        Map<String,Object> execution = mapper.selectAutoActionExecution(executionKey);
        requireIdentity(execution, todo.getTodoId(), ruleKey, actionType);
        String status = text(value(execution, "status", "status"));
        if ("SUCCESS".equals(status)) return AutoActionResult.success();
        if ("DEAD".equals(status)) return AutoActionResult.dead(text(value(execution,"last_error_code","lastErrorCode")), text(value(execution,"last_error_message","lastErrorMessage")));
        if ("RETRY".equals(status))
        {
            LocalDateTime nextRetry = date(value(execution, "next_retry_at", "nextRetryAt"));
            if (nextRetry != null && nextRetry.isAfter(now)) return AutoActionResult.retry("TODO_AUTO_ACTION_RETRY_PENDING", "Retry is not due");
            if (mapper.claimAutoActionRetry(executionKey, number(value(execution,"attempt_count","attemptCount")), now) <= 0)
                return AutoActionResult.retry("TODO_AUTO_ACTION_ALREADY_CLAIMED", "Execution is already claimed");
            execution = mapper.selectAutoActionExecution(executionKey);
        }
        else if ("CLAIMED".equals(status)&&inserted==0)
        {
            int currentAttempt=number(value(execution,"attempt_count","attemptCount"));
            if(currentAttempt+1>=maxAttempts)
            {
                AutoActionResult reconciled=reconcileCommittedAction(executionKey,todo,rule,ruleKey,actionType,capability,currentAttempt,now);
                if(reconciled!=null)return reconciled;
                int finalAttempt=Math.max(currentAttempt,maxAttempts);String code="TODO_AUTO_ACTION_STALE_MAX_ATTEMPTS",message="Stale execution reached maximum attempts";
                Map<String,Object> audit=audit(executionKey,todo,ruleKey,actionType,finalAttempt,AutoActionResult.dead(code,message),now);
                if(recorder.finalizeStaleDead(executionKey,currentAttempt,finalAttempt,now.minusMinutes(claimTimeoutMinutes),now,code,message,audit))
                    return AutoActionResult.dead(code,message);
                reconciled=reconcileCommittedAction(executionKey,todo,rule,ruleKey,actionType,capability,currentAttempt,now);
                return reconciled==null?replayWinner(mapper.selectAutoActionExecution(executionKey)):reconciled;
            }
            if(mapper.claimStaleAutoActionExecution(executionKey,currentAttempt,now.minusMinutes(claimTimeoutMinutes),now)<=0)
                return AutoActionResult.retry("TODO_AUTO_ACTION_ALREADY_CLAIMED","Execution is already claimed");
            execution=mapper.selectAutoActionExecution(executionKey);
        }
        else if (!"CLAIMED".equals(status))
            throw new TodoException("TODO_AUTO_ACTION_CLAIM_INVALID", "Invalid auto action claim state: " + status);

        int attempt = number(value(execution,"attempt_count","attemptCount"));
        try
        {
            if(!precondition(rule,todo,now))return record(executionKey,todo,ruleKey,actionType,attempt,AutoActionResult.dead("TODO_AUTO_ACTION_PRECONDITION_FALSE","Configured precondition did not match"),now,retryMinutes);
            AutoActionResult result = capability.execute(todo, rule, SERVICE_ACTOR);
            if (result == null) result = AutoActionResult.success();
            return record(executionKey, todo, ruleKey, actionType, attempt,
                    normalize(result, attempt, maxAttempts), now, retryMinutes);
        }
        catch (TodoException business)
        {
            AutoActionResult failed = attempt >= maxAttempts
                    ? AutoActionResult.dead(business.getBusinessCode(), business.getMessage())
                    : AutoActionResult.retry(business.getBusinessCode(), business.getMessage());
            return record(executionKey, todo, ruleKey, actionType, attempt, failed, now, retryMinutes);
        }
        catch (RuntimeException failure)
        {
            AutoActionResult failed = attempt >= maxAttempts
                    ? AutoActionResult.dead("TODO_AUTO_ACTION_EXECUTION_FAILED", failure.getMessage())
                    : AutoActionResult.retry("TODO_AUTO_ACTION_EXECUTION_FAILED", failure.getMessage());
            return record(executionKey, todo, ruleKey, actionType, attempt, failed, now, retryMinutes);
        }
    }

    public int scanDue(LocalDateTime now)
    {
        List<Map<String,Object>> rows = mapper.selectAutoActionScanItems(now);if (rows == null) return 0;
        int executed = 0;
        for (Map<String,Object> row : rows)
        {
            TodoInstance todo = row.get("todo") instanceof TodoInstance supplied ? supplied : mapper.selectById(longValue(value(row,"todo_id","todoId")));
            if (todo == null) continue;
            String json = text(value(row,"compiled_json","compiledJson"));if (json == null || json.isBlank()) continue;
            TodoDefinitionDocument definition;
            try { definition = codec.read(json); }
            catch (RuntimeException invalid) { continue; }
            for (int index=0;index<definition.autoActions().size();index++)
            {
                AutoActionRule rule=definition.autoActions().get(index);
                try { validateRuntimeRule(rule.config()); }
                catch(RuntimeException invalid)
                {
                    try{recordInvalidRule(todo,rule,index,invalid,now);executed++;}catch(RuntimeException ignored){/* isolate malformed published snapshots */}
                    continue;
                }
                try
                {
                    if(due(rule,row,todo,now)){execute(rule,todo,now);executed++;}
                }
                catch(RuntimeException ignored){/* isolate one execution without misclassifying a valid published rule */}
            }
        }
        return executed;
    }

    private AutoActionResult record(String key, TodoInstance todo, String ruleKey, String actionType, int attempt,
            AutoActionResult result, LocalDateTime now, int retryMinutes)
    {
        Map<String,Object> outcome = new HashMap<>();outcome.put("executionKey",key);outcome.put("attemptNo",attempt);
        outcome.put("status",result.status().name());outcome.put("errorCode",result.errorCode());outcome.put("errorMessage",result.errorMessage());
        outcome.put("nextRetryAt",result.status()==AutoActionStatus.RETRY?now.plusMinutes(retryMinutes):null);outcome.put("now",now);
        Map<String,Object> audit=audit(key,todo,ruleKey,actionType,attempt,result,now);
        recorder.record(outcome,audit);
        return result;
    }

    private Map<String,Object> audit(String key,TodoInstance todo,String ruleKey,String actionType,int attempt,AutoActionResult result,LocalDateTime now)
    {
        Map<String,Object> audit=new HashMap<>();audit.put("executionKey",key);audit.put("attemptNo",attempt);audit.put("todoId",todo.getTodoId());audit.put("ruleKey",ruleKey);audit.put("actionType",actionType);audit.put("status",result.status().name());audit.put("errorCode",result.errorCode());audit.put("errorMessage",result.errorMessage());audit.put("serviceActorId",SERVICE_ACTOR.userId());audit.put("serviceActorName",SERVICE_ACTOR.userName());audit.put("now",now);return audit;
    }

    private AutoActionResult reconcileCommittedAction(String executionKey,TodoInstance todo,AutoActionRule rule,String ruleKey,String actionType,TodoAutoActionCapability capability,int attempt,LocalDateTime now)
    {
        Map<String,Object> action=mapper.selectActionById(executionKey);if(action==null||action.isEmpty())return null;
        AutoActionResult result;
        if(!committedActionMatches(action,todo.getTodoId(),actionType))result=AutoActionResult.dead("TODO_AUTO_ACTION_RECONCILIATION_CONFLICT","Reserved action does not match the controlled execution identity");
        else
        {
            try
            {
                result=capability.execute(todo,rule,SERVICE_ACTOR);if(result==null)result=AutoActionResult.success();
                if(result.status()==AutoActionStatus.RETRY)result=AutoActionResult.dead(result.errorCode(),result.errorMessage());
            }
            catch(TodoException failure){result=AutoActionResult.dead(failure.getBusinessCode(),failure.getMessage());}
            catch(RuntimeException failure){result=AutoActionResult.dead("TODO_AUTO_ACTION_REPAIR_FAILED",failure.getMessage());}
        }
        Map<String,Object> outcome=new HashMap<>();outcome.put("executionKey",executionKey);outcome.put("attemptNo",attempt);outcome.put("status",result.status().name());outcome.put("errorCode",result.errorCode());outcome.put("errorMessage",result.errorMessage());outcome.put("nextRetryAt",null);outcome.put("now",now);
        if(recorder.tryRecord(outcome,audit(executionKey,todo,ruleKey,actionType,attempt,result,now)))return result;
        return replayWinner(mapper.selectAutoActionExecution(executionKey));
    }

    private boolean committedActionMatches(Map<String,Object> action,Long todoId,String actionType)
    {
        Object recorded=value(action,"todo_id","todoId");String source=text(value(action,"action_source","actionSource"));String operator=text(value(action,"operator_id","operatorId"));String name=text(value(action,"operator_name","operatorName"));
        return recorded!=null&&todoId.equals(Long.valueOf(String.valueOf(recorded)))&&actionType.equals(text(value(action,"action_type","actionType")))&&"SYSTEM".equals(source)&&String.valueOf(SERVICE_ACTOR.userId()).equals(operator)&&SERVICE_ACTOR.userName().equals(name);
    }

    private AutoActionResult replayWinner(Map<String,Object> winner)
    {
        if(winner!=null&&"SUCCESS".equals(text(value(winner,"status","status"))))return AutoActionResult.success();
        if(winner!=null&&"DEAD".equals(text(value(winner,"status","status"))))return AutoActionResult.dead(text(value(winner,"last_error_code","lastErrorCode")),text(value(winner,"last_error_message","lastErrorMessage")));
        return AutoActionResult.retry("TODO_AUTO_ACTION_ALREADY_CLAIMED","Execution is already claimed");
    }

    private void recordInvalidRule(TodoInstance todo,AutoActionRule rule,int index,RuntimeException invalid,LocalDateTime now)
    {
        String raw=text(rule.config().get("ruleKey"));String suffix=raw!=null&&!raw.isBlank()&&raw.length()<=70?raw:"INDEX-"+index;
        String ruleKey="INVALID:"+suffix,executionKey=executionKey(todo.getTodoId(),ruleKey),actionType="INVALID_RULE";
        Map<String,Object> claim=new HashMap<>();claim.put("executionKey",executionKey);claim.put("todoId",todo.getTodoId());claim.put("ruleKey",ruleKey);claim.put("actionType",actionType);claim.put("now",now);
        int inserted=mapper.insertAutoActionExecutionIfAbsent(claim);Map<String,Object> execution=mapper.selectAutoActionExecution(executionKey);requireIdentity(execution,todo.getTodoId(),ruleKey,actionType);
        if(inserted==0)return;String code=invalid instanceof TodoException business?business.getBusinessCode():"TODO_AUTO_ACTION_RULE_INVALID";
        record(executionKey,todo,ruleKey,actionType,number(value(execution,"attempt_count","attemptCount")),AutoActionResult.dead(code,invalid.getMessage()),now,DEFAULT_RETRY_MINUTES);
    }

    private RuntimeRule validateRuntimeRule(Map<String,Object> config)
    {
        String actionType=text(first(config,"actionType","action"));if(!TodoAutoActionCapability.ALLOWED_ACTION_TYPES.contains(actionType))throw new TodoException("TODO_AUTO_ACTION_NOT_ALLOWED","Auto action is not allow-listed: "+actionType);
        TodoAutoActionCapability capability=capabilities.get(actionType);if(capability==null)throw new TodoException("TODO_AUTO_ACTION_CAPABILITY_MISSING","No registered capability for: "+actionType);
        String declared=text(config.get("capability"));if(!actionType.equals(declared))throw new TodoException("TODO_AUTO_ACTION_CAPABILITY_MISMATCH","Declared capability does not match action type");
        String ruleKey=requiredRuleKey(config);String trigger=text(config.get("triggerAt"));if(!java.util.Set.of("DUE","SLA_80","SLA_100","SLA_150").contains(trigger))throw new TodoException("TODO_AUTO_ACTION_TRIGGER_INVALID","triggerAt must use a governed due time");
        if("TRANSFER".equals(actionType))positiveLong(config.get("targetOwnerId"),"TODO_AUTO_ACTION_TRANSFER_OWNER_INVALID","targetOwnerId must be a positive integer");
        positiveInt(config.get("maxAttempts"),DEFAULT_MAX_ATTEMPTS,"maxAttempts");positiveInt(config.get("retryDelayMinutes"),DEFAULT_RETRY_MINUTES,"retryDelayMinutes");positiveInt(config.get("claimTimeoutMinutes"),15,"claimTimeoutMinutes");
        return new RuntimeRule(actionType,ruleKey,capability);
    }

    @SuppressWarnings("unchecked")
    private boolean precondition(AutoActionRule rule,TodoInstance todo,LocalDateTime now)
    {
        Object raw=rule.config().get("precondition");if(raw==null)return true;if(!(raw instanceof Map<?,?> map))throw new TodoException("TODO_AUTO_ACTION_PRECONDITION_INVALID","precondition must be a canonical condition object");
        try
        {
            Map<String,Object> todoContext=new LinkedHashMap<>();todoContext.put("status",todo.getStatus());todoContext.put("ownerId",todo.getOwnerId());todoContext.put("businessType",todo.getBusinessType());todoContext.put("businessId",todo.getBusinessId());todoContext.put("slaStatus",todo.getSlaStatus());todoContext.put("dueAt",todo.getDueAt()==null?null:todo.getDueAt().toString());
            Map<String,Object> context=Map.of("todo",todoContext,"runtime",Map.of("now",now.toString()));
            return new ConditionEvaluator().evaluate(new ConditionValidator().decodeCanonical((Map<String,?>)map),context);
        }
        catch(IllegalArgumentException invalid){throw new TodoException("TODO_AUTO_ACTION_PRECONDITION_INVALID",invalid.getMessage());}
    }

    private AutoActionResult normalize(AutoActionResult result, int attempt, int maxAttempts)
    {
        if (result.status() == AutoActionStatus.RETRY && attempt >= maxAttempts)
            return AutoActionResult.dead(result.errorCode(), result.errorMessage());
        return result;
    }

    private boolean due(AutoActionRule rule, Map<String,Object> row, TodoInstance todo, LocalDateTime now)
    {
        String trigger = text(rule.config().get("triggerAt"));if (trigger == null) return false;
        Object raw = switch (trigger) {
            case "DUE" -> first(row,"due_at","dueAt") == null ? todo.getDueAt() : first(row,"due_at","dueAt");
            case "SLA_80" -> first(row,"remind80_due_at","remind80DueAt");
            case "SLA_100" -> first(row,"overdue100_due_at","overdue100DueAt");
            case "SLA_150" -> first(row,"escalate150_due_at","escalate150DueAt");
            default -> null;
        };
        LocalDateTime at = date(raw);return at != null && !at.isAfter(now);
    }

    private void requireIdentity(Map<String,Object> row, Long todoId, String ruleKey, String actionType)
    {
        if (row == null || row.isEmpty()) throw new TodoException("TODO_AUTO_ACTION_CLAIM_FAILED", "Auto action could not be claimed");
        if (!todoId.equals(longValue(value(row,"todo_id","todoId"))) || !ruleKey.equals(text(value(row,"rule_key","ruleKey")))
                || !actionType.equals(text(value(row,"action_type","actionType"))))
            throw new TodoException("TODO_AUTO_ACTION_EXECUTION_CONFLICT", "Execution key belongs to another action");
    }

    private String requiredRuleKey(Map<String,Object> config)
    {
        String key = text(config.get("ruleKey"));
        if (key == null || key.isBlank() || key.length() > 96) throw new TodoException("TODO_AUTO_ACTION_RULE_KEY_INVALID", "ruleKey is required and limited to 96 characters");
        return key;
    }
    private Long positiveLong(Object raw,String code,String message){try{Long value=raw==null?null:Long.valueOf(String.valueOf(raw));if(value==null||value<=0)throw new NumberFormatException();return value;}catch(NumberFormatException invalid){throw new TodoException(code,message);}}
    private String executionKey(Long todoId,String ruleKey){return "AUTO:"+todoId+":"+ruleKey;}
    private int positiveInt(Object raw,int fallback,String field){if(raw==null)return fallback;int value=number(raw);if(value<=0)throw new TodoException("TODO_AUTO_ACTION_RULE_INVALID",field+" must be positive");return value;}
    private int number(Object raw){return Integer.parseInt(String.valueOf(raw));}
    private Long longValue(Object raw){return raw==null?null:Long.valueOf(String.valueOf(raw));}
    private LocalDateTime date(Object raw){if(raw==null)return null;return raw instanceof LocalDateTime value?value:LocalDateTime.parse(String.valueOf(raw).replace(' ','T'));}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private Object first(Map<String,Object> row,String first,String second){return row.containsKey(first)?row.get(first):row.get(second);}
    private String text(Object raw){return raw==null?null:String.valueOf(raw);}
    private record RuntimeRule(String actionType,String ruleKey,TodoAutoActionCapability capability) { }

}

package com.law.todo.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.DodRuleCommand;
import com.law.todo.application.view.TodoConfigurationViews.DodRuleDetail;
import com.law.todo.application.view.TodoConfigurationViews.DodRuleListItem;
import com.law.todo.definition.validation.TodoFormValidator.ValidationIssue;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessValidator;
import com.law.todo.spi.TodoDictionaryValidationPort;

/** Manages reusable completion-condition rules; published definition snapshots are never mutated here. */
@Service
public class TodoDodRuleManagementService
{
    private final TodoConfigurationMapper mapper;
    private final TodoMapper todoMapper;
    private final TodoDictionaryValidationPort dictionaries;
    private final List<TodoBusinessValidator> validators;

    public TodoDodRuleManagementService(TodoConfigurationMapper mapper,TodoMapper todoMapper,
            TodoDictionaryValidationPort dictionaries,List<TodoBusinessValidator> validators)
    {this.mapper=mapper;this.todoMapper=todoMapper;this.dictionaries=dictionaries;this.validators=validators==null?List.of():List.copyOf(validators);}

    @Transactional(readOnly=true)
    public List<DodRuleListItem> list(Map<String,Object> query)
    {return mapper.selectDodRules(query==null?Map.of():query).stream().map(this::listItem).toList();}

    @Transactional(readOnly=true)
    public DodRuleDetail detail(long dodRuleId)
    {return detail(require(dodRuleId));}

    @Transactional
    public long save(DodRuleCommand command,Actor actor)
    {
        String type=command.dodRuleId()==null?"CREATE_DOD_RULE":"UPDATE_DOD_RULE";
        String fingerprint=fingerprint(type,command.dodRuleId(),command.expectedVersion(),command,actor);
        Long replay=claim(command.actionId(),type,command.dodRuleId(),fingerprint,actor,command);if(replay!=null)return replay;
        validate(command);
        Map<String,Object> row=row(command,actor);
        int changed=command.dodRuleId()==null?mapper.insertDodRule(row):mapper.updateDodRuleConditionally(row);
        if(changed<=0)throw new TodoException("TODO_DOD_RULE_VERSION_CONFLICT","DoD rule changed; refresh before retrying");
        Long id=command.dodRuleId()==null?number(row.get("dodRuleId")):command.dodRuleId();
        if(id==null)throw new TodoException("TODO_DOD_RULE_VERSION_CONFLICT","DoD rule identifier was not generated");
        complete(command.actionId(),fingerprint,id);return id;
    }

    @Transactional
    public long copy(long sourceDodRuleId,String newRuleCode,String actionId,Actor actor)
    {
        if(newRuleCode==null||newRuleCode.isBlank())throw new TodoException("TODO_DOD_RULE_CODE_REQUIRED","Copied DoD rule code is required");
        Map<String,Object> request=new TreeMap<>();request.put("sourceDodRuleId",sourceDodRuleId);request.put("newRuleCode",newRuleCode);
        String fingerprint=fingerprint("COPY_DOD_RULE",sourceDodRuleId,null,request,actor);
        Long replay=claim(actionId,"COPY_DOD_RULE",sourceDodRuleId,fingerprint,actor,request);if(replay!=null)return replay;
        Map<String,Object> source=require(sourceDodRuleId);
        DodRuleCommand copy=new DodRuleCommand(null,newRuleCode,text(source,"rule_name","ruleName"),text(source,"rule_type","ruleType"),
                text(source,"required_fields_json","requiredFieldsJson"),text(source,"required_attachments_json","requiredAttachmentsJson"),
                text(source,"conditional_rules_json","conditionalRulesJson"),text(source,"validator_refs_json","validatorRefsJson"),
                text(source,"error_messages_json","errorMessagesJson"),text(source,"status","status"),actionId,0);
        validate(copy);Map<String,Object> row=row(copy,actor);
        if(mapper.insertDodRule(row)<=0)throw new TodoException("TODO_DOD_RULE_VERSION_CONFLICT","DoD rule copy could not be saved");
        Long id=number(row.get("dodRuleId"));if(id==null)throw new TodoException("TODO_DOD_RULE_VERSION_CONFLICT","DoD rule identifier was not generated");
        complete(actionId,fingerprint,id);return id;
    }

    @Transactional
    public void toggle(long dodRuleId,String status,String actionId,int expectedVersion,Actor actor)
    {
        if(!"0".equals(status)&&!"1".equals(status))throw new TodoException("TODO_DOD_RULE_STATUS_INVALID","DoD rule status is invalid");
        Map<String,Object> request=new TreeMap<>();request.put("status",status);request.put("expectedVersion",expectedVersion);
        String fingerprint=fingerprint("TOGGLE_DOD_RULE",dodRuleId,expectedVersion,request,actor);
        if(claim(actionId,"TOGGLE_DOD_RULE",dodRuleId,fingerprint,actor,request)!=null)return;
        Map<String,Object> row=new HashMap<>();row.put("dodRuleId",dodRuleId);row.put("status",status);row.put("actionId",actionId);
        row.put("expectedVersion",expectedVersion);row.put("updateBy",actor.userName());
        if(mapper.updateDodRuleConditionally(row)<=0)throw new TodoException("TODO_DOD_RULE_VERSION_CONFLICT","DoD rule changed; refresh before retrying");
        complete(actionId,fingerprint,dodRuleId);
    }

    /** Sample validation deliberately aggregates missing fields and attachments instead of failing at the first violation. */
    @Transactional(readOnly=true)
    public DodTestResult test(long dodRuleId,Map<String,Object> payload,List<String> attachments,Actor actor)
    {
        Map<String,Object> rule=require(dodRuleId);Map<String,Object> values=payload==null?Map.of():payload;
        List<String> supplied=attachments==null?List.of():attachments;
        List<String> missingFields=required(rule,"required_fields_json","requiredFieldsJson").stream()
                .filter(field->!present(values.get(field))).toList();
        List<String> missingAttachments=required(rule,"required_attachments_json","requiredAttachmentsJson").stream()
                .filter(type->!supplied.contains(type)).toList();
        return new DodTestResult(missingFields.isEmpty()&&missingAttachments.isEmpty(),missingFields,missingAttachments,List.of());
    }

    private void validate(DodRuleCommand command)
    {
        if(command==null||!dictionaries.isEnabled("law_todo_dod_rule_type",command.ruleType())
                ||!dictionaries.isEnabled("law_todo_rule_status",command.status()))
            throw new TodoException("TODO_DOD_RULE_DICTIONARY_INVALID","DoD rule dictionary value is unknown or disabled");
        Set<String> available=new LinkedHashSet<>();for(TodoBusinessValidator validator:validators)
            if(validator.catalogCode()!=null&&!validator.catalogCode().isBlank())available.add(validator.catalogCode());
        for(String ref:strings(command.validatorRefsJson()))if(!available.contains(ref))
            throw new TodoException("TODO_DOD_VALIDATOR_NOT_FOUND","DoD validator is unavailable: "+ref);
    }

    private Map<String,Object> require(long id)
    {Map<String,Object> rule=mapper.selectDodRule(id);if(rule==null||rule.isEmpty())throw new TodoException("TODO_DOD_RULE_NOT_FOUND","DoD rule not found");return rule;}
    private static String fingerprint(String type,Long source,Integer version,Object request,Actor actor)
    {
        Map<String,Object> values=new TreeMap<>();values.put("actionType",type);values.put("sourceEntityId",source);values.put("expectedVersion",version);
        values.put("actorId",actor.userId());values.put("actorName",actor.userName());values.put("actorDeptId",actor.deptId());
        values.put("request",JSON.parse(JSON.toJSONString(request)));return TodoDefinitionSimulationService.sha256(JSON.toJSONString(values));
    }
    private Long claim(String actionId,String type,Long source,String fingerprint,Actor actor,Object request)
    {
        if(actionId==null||actionId.isBlank())throw new TodoException("TODO_DOD_RULE_ACTION_REQUIRED","DoD rule actionId is required");
        Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);action.put("actionType",type);action.put("entityType","DOD_RULE");
        action.put("sourceEntityId",source);action.put("operatorId",actor.userId());action.put("operatorName",actor.userName());action.put("operatorDeptId",actor.deptId());
        action.put("requestFingerprint",fingerprint);action.put("payloadJson",JSON.toJSONString(request));
        int inserted=todoMapper.insertDefinitionActionClaim(action);Map<String,Object> locked=todoMapper.selectDefinitionActionForUpdate(actionId);
        if(locked==null||!type.equals(text(locked,"action_type","actionType"))||!"DOD_RULE".equals(text(locked,"entity_type","entityType"))
                ||!fingerprint.equals(text(locked,"request_fingerprint","requestFingerprint"))||!Objects.equals(source,number(value(locked,"source_entity_id","sourceEntityId")))
                ||!Objects.equals(actor.userId(),number(value(locked,"operator_id","operatorId")))||!Objects.equals(actor.userName(),text(locked,"operator_name","operatorName"))
                ||!Objects.equals(actor.deptId(),number(value(locked,"operator_dept_id","operatorDeptId"))))
            throw new TodoException("TODO_DOD_RULE_ACTION_CONFLICT","DoD rule action conflicts with a different request");
        Long entity=number(value(locked,"entity_id","entityId"));String status=text(locked,"action_status","actionStatus");
        if(entity!=null){if(!"APPLIED".equals(status))throw new TodoException("TODO_DOD_RULE_ACTION_CONFLICT","Recorded DoD rule action is incomplete");return entity;}
        if(inserted<=0||!"CLAIMED".equals(status))throw new TodoException("TODO_DOD_RULE_ACTION_CONFLICT","Recorded DoD rule action is incomplete");
        return null;
    }
    private void complete(String actionId,String fingerprint,Long entityId)
    {if(entityId==null||todoMapper.completeDefinitionAction(actionId,fingerprint,entityId)<=0)throw new TodoException("TODO_DOD_RULE_ACTION_CONFLICT","DoD rule action could not be completed");}
    private Map<String,Object> row(DodRuleCommand command,Actor actor)
    {
        Map<String,Object> row=new HashMap<>();row.put("dodRuleId",command.dodRuleId());row.put("ruleCode",command.ruleCode());row.put("ruleName",command.ruleName());
        row.put("ruleType",command.ruleType());row.put("requiredFieldsJson",command.requiredFieldsJson());row.put("requiredAttachmentsJson",command.requiredAttachmentsJson());
        row.put("conditionalRulesJson",command.conditionalRulesJson());row.put("validatorRefsJson",command.validatorRefsJson());row.put("errorMessagesJson",command.errorMessagesJson());
        row.put("status",command.status());row.put("actionId",command.actionId());row.put("expectedVersion",command.expectedVersion());row.put("updateAllFields",true);
        row.put("createBy",actor.userName());row.put("updateBy",actor.userName());return row;
    }
    private DodRuleListItem listItem(Map<String,Object> row)
    {return new DodRuleListItem(number(value(row,"dod_rule_id","dodRuleId")),text(row,"rule_code","ruleCode"),text(row,"rule_name","ruleName"),
            text(row,"rule_type","ruleType"),text(row,"status","status"),integer(row,"version","version"),longNumber(value(row,"reference_count","referenceCount")),date(value(row,"update_time","updateTime")));}
    private DodRuleDetail detail(Map<String,Object> row)
    {return new DodRuleDetail(number(value(row,"dod_rule_id","dodRuleId")),text(row,"rule_code","ruleCode"),text(row,"rule_name","ruleName"),
            text(row,"rule_type","ruleType"),text(row,"required_fields_json","requiredFieldsJson"),text(row,"required_attachments_json","requiredAttachmentsJson"),
            text(row,"conditional_rules_json","conditionalRulesJson"),text(row,"validator_refs_json","validatorRefsJson"),text(row,"error_messages_json","errorMessagesJson"),
            text(row,"status","status"),integer(row,"version","version"),longNumber(value(row,"reference_count","referenceCount")),text(row,"create_by","createBy"),
            date(value(row,"create_time","createTime")),text(row,"update_by","updateBy"),date(value(row,"update_time","updateTime")));}
    private List<String> required(Map<String,Object> row,String snake,String camel){return strings(value(row,snake,camel));}
    private List<String> strings(Object raw)
    {
        if(raw==null)return List.of();List<?> values;
        if(raw instanceof List<?> list)values=list;
        else {String json=String.valueOf(raw);if(json.isBlank())return List.of();values=JSON.parseArray(json);}
        List<String> result=new ArrayList<>();for(Object item:values)if(item!=null)result.add(String.valueOf(item));return List.copyOf(result);
    }
    private boolean present(Object value){return value!=null&&(!(value instanceof String text)||!text.isBlank());}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private long longNumber(Object value){Long number=number(value);return number==null?0:number;}
    private Integer integer(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value==null?null:Integer.valueOf(String.valueOf(value));}
    private LocalDateTime date(Object value){if(value instanceof LocalDateTime time)return time;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();return null;}

    public record DodTestResult(boolean passed,List<String> missingFields,List<String> missingAttachments,List<ValidationIssue> validatorIssues)
    {public DodTestResult{missingFields=List.copyOf(missingFields);missingAttachments=List.copyOf(missingAttachments);validatorIssues=List.copyOf(validatorIssues);}}
}

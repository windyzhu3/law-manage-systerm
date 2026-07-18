package com.law.todo.application;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.application.command.TodoManagementCommands.TemplateCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.expression.ConditionValidator;
import com.alibaba.fastjson2.JSON;

@Service
public class TodoTemplateService
{
    private final TodoMapper mapper;
    private final TodoEventCatalogService eventCatalog;
    private final ConditionValidator conditionValidator;
    public TodoTemplateService(TodoMapper mapper){this(mapper,new TodoEventCatalogService(mapper),new ConditionValidator());}
    @Autowired public TodoTemplateService(TodoMapper mapper,TodoEventCatalogService eventCatalog,ConditionValidator conditionValidator){this.mapper=mapper;this.eventCatalog=eventCatalog;this.conditionValidator=conditionValidator;}
    public List<Map<String,Object>> listTemplates(){return mapper.selectTemplates();}
    @Transactional public int saveTemplate(TemplateCommand command,String operator){Map<String,Object> value=new HashMap<>();value.put("templateId",command.templateId());value.put("templateCode",command.templateCode());value.put("templateName",command.templateName());value.put("businessType",command.businessType());value.put("status",command.status()==null?"0":command.status());value.put("createBy",operator);value.put("updateBy",operator);return saveTemplate(value);}
    @Transactional public int saveTemplate(Map<String,Object> value){required(value,"templateCode");required(value,"templateName");required(value,"businessType");return value.get("templateId")==null?mapper.insertTemplate(value):mapper.updateTemplate(value);}
    public List<Map<String,Object>> listTriggers(){return mapper.selectAllTriggerRules();}
    @Transactional public int saveTrigger(TriggerCommand command){return saveTrigger(command,new Actor(0L,"system",0L));}
    @Transactional public int saveTrigger(TriggerCommand command,Actor actor){requireExpectedVersion(command.triggerRuleId(),command.expectedVersion());String type=command.triggerRuleId()==null?"CREATE_TRIGGER":"UPDATE_TRIGGER";String fingerprint=fingerprint(type,command.triggerRuleId(),command.expectedVersion(),command,actor);Long replay=claim(command.actionId(),type,command.triggerRuleId(),fingerprint,actor,command);if(replay!=null)return 1;Map<String,Object> value=new HashMap<>();value.put("triggerRuleId",command.triggerRuleId());value.put("eventType",command.eventType());value.put("templateId",command.templateId());value.put("templateVersionId",command.templateVersionId());value.put("businessType",command.businessType());value.put("enabled",command.enabled()==null?"Y":command.enabled());value.put("conditionJson",command.conditionJson());value.put("payloadVersion",command.payloadVersion()==null?1:command.payloadVersion());value.put("expectedVersion",command.expectedVersion()==null?0:command.expectedVersion());int saved=saveTrigger(value);Long id=command.triggerRuleId()==null?Long.valueOf(String.valueOf(value.get("triggerRuleId"))):command.triggerRuleId();complete(command.actionId(),fingerprint,id,"TODO_TRIGGER_ACTION_CONFLICT");return saved;}
    @Transactional int saveTrigger(Map<String,Object> value){required(value,"eventType");required(value,"templateId");required(value,"templateVersionId");required(value,"businessType");value.putIfAbsent("expectedVersion",0);validateTriggerBinding(value);validateTriggerCondition(value);int saved=value.get("triggerRuleId")==null?mapper.insertTriggerRule(value):mapper.updateTriggerRule(value);if(saved<=0&&value.get("triggerRuleId")!=null)throw new TodoException("TODO_TRIGGER_VERSION_CONFLICT","Trigger changed; refresh before retrying");return saved;}
    private void requireExpectedVersion(Long id,Integer version){if(id!=null&&version==null)throw new TodoException("TODO_TRIGGER_VERSION_REQUIRED","expectedVersion is required for trigger updates");}
    private void validateTriggerBinding(Map<String,Object> value){Map<String,Object> version=mapper.selectTemplateVersionById(Long.valueOf(String.valueOf(value.get("templateVersionId"))));if(version==null||version.isEmpty()||!String.valueOf(value.get("templateId")).equals(String.valueOf(version.get("template_id")))||!"PUBLISHED".equals(String.valueOf(version.get("status"))))throw new TodoException("TODO_TRIGGER_VERSION_INVALID","Trigger template version must belong to the template and be published");}
    private void validateTriggerCondition(Map<String,Object> value)
    {
        String eventType=String.valueOf(value.get("eventType"));
        int payloadVersion=value.get("payloadVersion")==null?1:Integer.parseInt(String.valueOf(value.get("payloadVersion")));
        String schema=eventCatalog.payloadSchema(eventType,payloadVersion);
        if(schema==null)throw new TodoException("TODO_EVENT_CATALOG_REQUIRED","Trigger event type and payload version must be active");
        String json=text(value.get("conditionJson"));
        if(json==null||json.isBlank())return;
        ConditionValidator.ValidationResult result=conditionValidator.validate(json,schema,false);
        if(!result.valid())
        {
            ValidationIssue issue=result.issues().get(0);
            throw new TodoException(issue.code(),issue.message());
        }
    }
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private String fingerprint(String type,Long id,Integer version,Object command,Actor actor){Map<String,Object> values=new TreeMap<>();values.put("type",type);values.put("id",id);values.put("version",version);values.put("actor",actor.userId());values.put("request",JSON.parse(JSON.toJSONString(command)));return TodoDefinitionSimulationService.sha256(JSON.toJSONString(values));}
    private Long claim(String actionId,String type,Long source,String fingerprint,Actor actor,Object command)
    {
        if(actionId==null||actionId.isBlank())throw new TodoException("TODO_TRIGGER_ACTION_REQUIRED","actionId is required");
        Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);action.put("actionType",type);action.put("entityType","TRIGGER");
        action.put("sourceEntityId",source);action.put("operatorId",actor.userId());action.put("operatorName",actor.userName());
        action.put("operatorDeptId",actor.deptId());action.put("requestFingerprint",fingerprint);action.put("payloadJson",JSON.toJSONString(command));
        int inserted=mapper.insertDefinitionActionClaim(action);
        Map<String,Object> locked=mapper.selectDefinitionActionForUpdate(actionId);
        if(locked==null||!type.equals(text(value(locked,"action_type","actionType")))
                ||!"TRIGGER".equals(text(value(locked,"entity_type","entityType")))
                ||!fingerprint.equals(text(value(locked,"request_fingerprint","requestFingerprint")))
                ||!Objects.equals(source,number(value(locked,"source_entity_id","sourceEntityId")))
                ||!Objects.equals(actor.userId(),number(value(locked,"operator_id","operatorId")))
                ||!Objects.equals(actor.userName(),text(value(locked,"operator_name","operatorName")))
                ||!Objects.equals(actor.deptId(),number(value(locked,"operator_dept_id","operatorDeptId"))))
            throw new TodoException("TODO_TRIGGER_ACTION_CONFLICT","action id conflicts");
        Long entity=number(value(locked,"entity_id","entityId"));
        String status=text(value(locked,"action_status","actionStatus"));
        if(entity!=null)
        {
            if(!"APPLIED".equals(status))throw new TodoException("TODO_TRIGGER_ACTION_CONFLICT","action incomplete");
            return entity;
        }
        if(inserted<=0||!"CLAIMED".equals(status))
            throw new TodoException("TODO_TRIGGER_ACTION_CONFLICT","recorded action is incomplete");
        return null;
    }
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private void complete(String actionId,String fingerprint,Long id,String code){if(id==null||mapper.completeDefinitionAction(actionId,fingerprint,id)<=0)throw new TodoException(code,"action completion failed");}
    @Transactional public Long publish(Long templateId,int versionNo,String ownerJson,String dodJson,String slaJson,String nextJson,String uiSchemaJson,String user)
    {
        validJson(ownerJson,"ownerRuleJson");validJson(dodJson,"dodRuleJson");validJson(slaJson,"slaRuleJson");validJson(nextJson,"nextRuleJson");validJson(uiSchemaJson,"uiSchemaJson");
        if(mapper.selectTemplateVersion(templateId,versionNo)!=null)throw new TodoException("TODO_TEMPLATE_VERSION_EXISTS","模板版本已存在");
        Map<String,Object> version=new HashMap<>();version.put("templateId",templateId);version.put("versionNo",versionNo);version.put("ownerRuleJson",ownerJson);version.put("dodRuleJson",dodJson);version.put("slaRuleJson",slaJson);version.put("nextRuleJson",nextJson);version.put("uiSchemaJson",uiSchemaJson);version.put("publishedBy",user);
        if(mapper.insertTemplateVersion(version)<=0)throw new TodoException("TODO_TEMPLATE_PUBLISH_FAILED","模板发布失败");mapper.updateTemplateCurrentVersion(templateId,versionNo,user);return Long.valueOf(String.valueOf(version.get("versionId")));
    }
    private void required(Map<String,Object> value,String key){if(value.get(key)==null||String.valueOf(value.get(key)).isBlank())throw new TodoException("TODO_TEMPLATE_FIELD_REQUIRED","缺少模板字段："+key);}
    private void validJson(String value,String field){if(value!=null&&!value.isBlank()&&!JSON.isValid(value))throw new TodoException("TODO_TEMPLATE_JSON_INVALID","模板JSON字段格式错误："+field);}
}

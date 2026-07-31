package com.law.todo.application;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.application.command.TodoManagementCommands.TemplateCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateMetadataCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateToggleCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerSortCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerToggleCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TriggerTemplateVersionCatalogView;
import com.law.todo.application.view.TodoConfigurationViews.EventCatalogEntry;
import com.law.todo.application.view.TodoConfigurationViews.RoutingTargetCatalogEntry;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.spi.TodoDictionaryValidationPort;
import com.alibaba.fastjson2.JSON;

@Service
public class TodoTemplateService
{
    private static final String LEAD_ENTRY_SLOT="LEAD_FIRST_CONTACT_ENTRY";
    private static final String LEAD_ENTRY_TEMPLATE="TD-001";
    public record EntrySlotBinding(String entrySlotCode,long triggerRuleId,
            long templateVersionId,String templateCode) { }

    private final TodoMapper mapper;
    private final TodoEventCatalogService eventCatalog;
    private final ConditionValidator conditionValidator;
    private final TodoDictionaryValidationPort dictionaries;
    public TodoTemplateService(TodoMapper mapper){this(mapper,new TodoEventCatalogService(mapper),new ConditionValidator(),(type,value)->true);}
    @Autowired public TodoTemplateService(TodoMapper mapper,TodoEventCatalogService eventCatalog,ConditionValidator conditionValidator,TodoDictionaryValidationPort dictionaries){this.mapper=mapper;this.eventCatalog=eventCatalog;this.conditionValidator=conditionValidator;this.dictionaries=dictionaries;}
    public List<Map<String,Object>> listTemplates(){return mapper.selectTemplates();}
    public List<Map<String,Object>> listTemplateCalendarCatalog()
    {
        List<Map<String,Object>> rows=mapper.selectCalendars();if(rows==null)return List.of();
        return rows.stream().filter(row->"0".equals(text(value(row,"status","status")))).map(row->{
            Map<String,Object> item=new java.util.LinkedHashMap<>();item.put("calendarCode",text(value(row,"calendar_code","calendarCode")));
            item.put("calendarName",text(value(row,"calendar_name","calendarName")));item.put("timezone",text(value(row,"timezone","timezone")));
            return item;
        }).toList();
    }
    public List<Map<String,Object>> listEventCatalogs(){return eventCatalog.entries();}
    public List<EventCatalogEntry> listTemplateEventCatalog()
    {return eventCatalog.entries().stream().filter(row->"ACTIVE".equals(text(value(row,"status","status")))).map(row->new EventCatalogEntry(text(value(row,"event_type","eventType")),
            integer(value(row,"payload_version","payloadVersion")),text(value(row,"business_object_type","businessObjectType")),
            text(value(row,"payload_schema_json","payloadSchemaJson")),text(value(row,"status","status")))).toList();}
    public List<TriggerTemplateVersionCatalogView> listPublishedVersionCatalog(Long templateId)
    {List<Map<String,Object>> rows=mapper.selectPublishedTemplateVersionCatalog(templateId);if(rows==null)return List.of();return rows.stream().map(row->new TriggerTemplateVersionCatalogView(number(value(row,"version_id","versionId")),integer(value(row,"version_no","versionNo")),text(value(row,"status","status")))).toList();}
    public List<RoutingTargetCatalogEntry> listRoutingTargetCatalog()
    {List<Map<String,Object>> rows=mapper.selectRoutingTargetCatalog();if(rows==null)return List.of();return rows.stream().map(row->new RoutingTargetCatalogEntry(
            number(value(row,"template_id","templateId")),text(value(row,"template_code","templateCode")),
            text(value(row,"template_name","templateName")),text(value(row,"business_type","businessType")),
            number(value(row,"version_id","versionId")),integer(value(row,"version_no","versionNo")),
            text(value(row,"status","status")))).toList();}
    @Transactional public int saveTemplate(TemplateCommand command,String operator)
    {
        Map<String,Object> value=new HashMap<>();value.put("templateId",command.templateId());value.put("templateName",command.templateName());
        if(command.templateId()==null)
        {
            validateBusinessType(command.businessType());value.put("templateCode",command.templateCode());
            value.put("businessType",command.businessType());value.put("status",command.status()==null?"0":command.status());
            value.put("createBy",operator);
        }
        value.put("updateBy",operator);return saveTemplate(value);
    }
    @Transactional public int updateTemplateMetadata(TemplateMetadataCommand command,Actor actor)
    {
        String fingerprint=fingerprint("UPDATE_TEMPLATE",command.templateId(),command.expectedVersion(),command,actor);
        if(claimTemplateAction(command.actionId(),"UPDATE_TEMPLATE",command.templateId(),fingerprint,actor,command)!=null)return 1;
        Map<String,Object> current=mapper.selectTemplateForUpdate(command.templateId());
        if(current==null||current.isEmpty())throw new TodoException("TODO_TEMPLATE_NOT_FOUND","Todo template not found");
        validateBusinessType(text(value(current,"business_type","businessType")));
        if(!Objects.equals(command.expectedVersion(),integer(value(current,"version","version"))))
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","Template changed; refresh before retrying");
        Map<String,Object> value=new HashMap<>();value.put("templateId",command.templateId());value.put("templateName",command.templateName());
        value.put("expectedVersion",command.expectedVersion());value.put("updateBy",actor.userName());
        if(mapper.updateTemplateMetadataConditionally(value)<=0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","Template changed; refresh before retrying");
        complete(command.actionId(),fingerprint,command.templateId(),"TODO_TEMPLATE_ACTION_CONFLICT");return 1;
    }
    @Transactional public int saveTemplate(Map<String,Object> value)
    {
        required(value,"templateName");
        if(value.get("templateId")==null)
        {required(value,"templateCode");required(value,"businessType");return mapper.insertTemplate(value);}
        Map<String,Object> metadata=new HashMap<>();metadata.put("templateId",value.get("templateId"));
        metadata.put("templateName",value.get("templateName"));metadata.put("updateBy",value.get("updateBy"));
        return mapper.updateTemplate(metadata);
    }
    @Transactional public void toggleTemplate(long templateId,TemplateToggleCommand command,Actor actor)
    {
        String fingerprint=fingerprint("TOGGLE_TEMPLATE",templateId,command.expectedVersion(),command,actor);
        if(claimTemplateAction(command.actionId(),"TOGGLE_TEMPLATE",templateId,fingerprint,actor,command)!=null)return;
        Map<String,Object> locked=mapper.selectTemplateForUpdate(templateId);
        if(locked==null||locked.isEmpty())throw new TodoException("TODO_TEMPLATE_NOT_FOUND","Todo template not found");
        if(!Objects.equals(command.expectedVersion(),integer(value(locked,"version","version"))))
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","Template changed; refresh before retrying");
        if("0".equals(command.status())&&text(value(locked,"replacement_template_code","replacementTemplateCode"))!=null)
            throw new TodoException("TODO_TEMPLATE_REPLACED","Replaced templates cannot be reactivated");
        validateBusinessType(text(value(locked,"business_type","businessType")));
        Map<String,Object> row=new HashMap<>();row.put("templateId",templateId);row.put("status",command.status());
        row.put("expectedVersion",command.expectedVersion());row.put("updateBy",actor.userName());
        if(mapper.updateTemplateStatusConditionally(row)<=0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","Template changed; refresh before retrying");
        complete(command.actionId(),fingerprint,templateId,"TODO_TEMPLATE_ACTION_CONFLICT");
    }
    public List<Map<String,Object>> listTriggers(){return listTriggers(null);}
    public List<Map<String,Object>> listTriggers(String keyword){return mapper.selectAllTriggerRules(keyword);}
    @Transactional public int saveTrigger(TriggerCommand command){return saveTrigger(command,new Actor(0L,"system",0L));}
    @Transactional public int saveTrigger(TriggerCommand command,Actor actor){validateBusinessType(command.businessType());requireExpectedVersion(command.triggerRuleId(),command.expectedVersion());validateLeadIngressCommand(command);String type=command.triggerRuleId()==null?"CREATE_TRIGGER":"UPDATE_TRIGGER";String fingerprint=fingerprint(type,command.triggerRuleId(),command.expectedVersion(),command,actor);Long replay=claim(command.actionId(),type,command.triggerRuleId(),fingerprint,actor,command);if(replay!=null)return 1;Map<String,Object> value=new HashMap<>();value.put("triggerRuleId",command.triggerRuleId());value.put("ruleCode",command.ruleCode());value.put("ruleName",command.ruleName());value.put("eventType",command.eventType());value.put("templateId",command.templateId());value.put("templateVersionId",command.templateVersionId());value.put("businessType",command.businessType());value.put("entrySlotCode",command.entrySlotCode());value.put("enabled",command.enabled()==null?"Y":command.enabled());value.put("conditionJson",command.conditionJson());value.put("payloadVersion",command.payloadVersion()==null?1:command.payloadVersion());value.put("expectedVersion",command.expectedVersion()==null?0:command.expectedVersion());value.put("createBy",actor.userName());value.put("updateBy",actor.userName());int saved=saveTrigger(value);Long id=command.triggerRuleId()==null?Long.valueOf(String.valueOf(value.get("triggerRuleId"))):command.triggerRuleId();complete(command.actionId(),fingerprint,id,"TODO_TRIGGER_ACTION_CONFLICT");return saved;}
    @Transactional public void sortTriggers(TriggerSortCommand command,Actor actor)
    {
        validateSort(command);
        String fingerprint=fingerprint("SORT_TRIGGER",null,null,command,actor);Long replay=claim(command.actionId(),"SORT_TRIGGER",null,fingerprint,actor,command);if(replay!=null)return;
        for(var item:command.items())
        {
            Map<String,Object> row=new HashMap<>();row.put("triggerRuleId",item.triggerRuleId());row.put("sortOrder",item.sortOrder());row.put("expectedVersion",item.expectedVersion());row.put("updateBy",actor.userName());
            if(mapper.updateTriggerRuleSortConditionally(row)<=0)throw new TodoException("TODO_TRIGGER_VERSION_CONFLICT","Trigger changed; refresh before retrying");
        }
        complete(command.actionId(),fingerprint,command.items().get(0).triggerRuleId(),"TODO_TRIGGER_ACTION_CONFLICT");
    }
    @Transactional public void toggleTrigger(long triggerRuleId,TriggerToggleCommand command,Actor actor)
    {String fingerprint=fingerprint("TOGGLE_TRIGGER",triggerRuleId,command.expectedVersion(),command,actor);if(claim(command.actionId(),"TOGGLE_TRIGGER",triggerRuleId,fingerprint,actor,command)!=null)return;if("Y".equals(command.enabled()))validateTriggerEnable(triggerRuleId,command.expectedVersion());Map<String,Object> row=new HashMap<>();row.put("triggerRuleId",triggerRuleId);row.put("enabled",command.enabled());row.put("expectedVersion",command.expectedVersion());row.put("updateBy",actor.userName());if(mapper.updateTriggerRuleEnabledConditionally(row)<=0)throw new TodoException("TODO_TRIGGER_VERSION_CONFLICT","Trigger changed; refresh before retrying");complete(command.actionId(),fingerprint,triggerRuleId,"TODO_TRIGGER_ACTION_CONFLICT");}
    @Transactional public EntrySlotBinding switchEntrySlot(String entrySlotCode,long triggerRuleId,int expectedVersion,Actor actor)
    {
        List<Map<String,Object>> bindings=mapper.selectEntrySlotBindingsForUpdate(entrySlotCode);
        if(bindings==null||bindings.isEmpty())throw new TodoException("TODO_TRIGGER_ENTRY_SLOT_NOT_FOUND","Entry slot was not found");
        Map<String,Object> target=bindings.stream().filter(row->Objects.equals(triggerRuleId,number(value(row,"trigger_rule_id","triggerRuleId")))).findFirst().orElse(null);
        if(target==null)throw new TodoException("TODO_TRIGGER_ENTRY_SLOT_TARGET_INVALID","Trigger does not belong to the entry slot");
        if(!Objects.equals(expectedVersion,integer(value(target,"trigger_version","triggerVersion"))))
            throw new TodoException("TODO_TRIGGER_VERSION_CONFLICT","Trigger changed; refresh before retrying");
        if(LEAD_ENTRY_SLOT.equals(entrySlotCode)&&!LEAD_ENTRY_TEMPLATE.equals(text(value(target,"template_code","templateCode"))))
            throw new TodoException("TODO_TRIGGER_ENTRY_SLOT_TARGET_INVALID","Lead entry slot must target TD-001");
        if(!"PUBLISHED".equals(text(value(target,"version_status","versionStatus"))))
            throw new TodoException("TODO_TRIGGER_VERSION_INVALID","Trigger template version must be published");
        if(!"0".equals(text(value(target,"template_status","templateStatus"))))
            throw new TodoException("TODO_TRIGGER_TEMPLATE_INACTIVE","Trigger template must be active before enabling");
        mapper.disableEntrySlotBindings(entrySlotCode,triggerRuleId,actor.userName());
        if(mapper.enableEntrySlotBinding(triggerRuleId,expectedVersion,actor.userName())<=0)
            throw new TodoException("TODO_TRIGGER_VERSION_CONFLICT","Trigger changed; refresh before retrying");
        return new EntrySlotBinding(text(value(target,"entry_slot_code","entrySlotCode")),triggerRuleId,
                number(value(target,"template_version_id","templateVersionId")),text(value(target,"template_code","templateCode")));
    }
    private void validateSort(TriggerSortCommand command)
    {java.util.Set<Long> ids=new java.util.HashSet<>();java.util.Set<Integer> orders=new java.util.HashSet<>();for(var item:command.items())if(!ids.add(item.triggerRuleId())||!orders.add(item.sortOrder()))throw new TodoException("TODO_TRIGGER_SORT_INVALID","Trigger sort items must be unique");}
    @Transactional int saveTrigger(Map<String,Object> value){required(value,"eventType");required(value,"templateId");required(value,"templateVersionId");required(value,"businessType");populateCompatibilityIdentity(value);validateTriggerIdentity(value);if(mapper.countTriggerRulesByCode(text(value.get("ruleCode")),number(value.get("triggerRuleId")))>0)throw new TodoException("TODO_TRIGGER_CODE_DUPLICATE","Trigger rule code already exists");value.putIfAbsent("expectedVersion",0);validateTriggerBinding(value);validateTriggerCondition(value);validateLeadIngress(value);int saved;try{saved=value.get("triggerRuleId")==null?mapper.insertTriggerRule(value):mapper.updateTriggerRule(value);}catch(DuplicateKeyException duplicate){if(isRuleCodeDuplicate(duplicate))throw new TodoException("TODO_TRIGGER_CODE_DUPLICATE","Trigger rule code already exists");throw duplicate;}if(saved<=0&&value.get("triggerRuleId")!=null)throw new TodoException("TODO_TRIGGER_VERSION_CONFLICT","Trigger changed; refresh before retrying");return saved;}
    private boolean isRuleCodeDuplicate(DuplicateKeyException duplicate)
    {for(Throwable cause=duplicate;cause!=null;cause=cause.getCause())if(String.valueOf(cause.getMessage()).contains("uk_todo_trigger_rule_code"))return true;return false;}
    private void populateCompatibilityIdentity(Map<String,Object> value)
    {String event=text(value.get("eventType"));Long version=number(value.get("templateVersionId"));String code=text(value.get("ruleCode"));String name=text(value.get("ruleName"));if(code==null||code.isBlank()){String normalized=event==null?"RULE":event.toUpperCase().replaceAll("[^A-Z0-9]+","_").replaceAll("^_+|_+$","");String generated="TRIGGER_"+(normalized.isBlank()?"RULE":normalized)+"_"+(version==null?"NEW":version);value.put("ruleCode",generated.substring(0,Math.min(64,generated.length())));}if(name==null||name.isBlank())value.put("ruleName",event==null?"Trigger rule":event+" trigger rule");}
    private void validateTriggerIdentity(Map<String,Object> value)
    {String code=text(value.get("ruleCode"));String name=text(value.get("ruleName"));if(code==null||code.length()>64||!code.matches("^[A-Z][A-Z0-9_]*$"))throw new TodoException("TODO_TRIGGER_CODE_INVALID","Trigger rule code must use uppercase letters, digits, and underscores");if(name==null||name.length()>128)throw new TodoException("TODO_TRIGGER_NAME_INVALID","Trigger rule name is required and must be at most 128 characters");}
    private void requireExpectedVersion(Long id,Integer version){if(id!=null&&version==null)throw new TodoException("TODO_TRIGGER_VERSION_REQUIRED","expectedVersion is required for trigger updates");}
    private void validateBusinessType(String businessType){if(!dictionaries.isEnabled("law_todo_business_type",businessType))throw new TodoException("TODO_TEMPLATE_BUSINESS_TYPE_INVALID","Business type is unknown or disabled");}
    private void validateTriggerBinding(Map<String,Object> value)
    {
        Map<String,Object> binding=mapper.selectTriggerTemplateBinding(Long.valueOf(String.valueOf(value.get("templateVersionId"))));
        Long templateId=number(value.get("templateId"));
        if(binding==null||binding.isEmpty()||!Objects.equals(templateId,number(value(binding,"version_template_id","versionTemplateId")))
                ||!"PUBLISHED".equals(text(value(binding,"version_status","versionStatus"))))
            throw new TodoException("TODO_TRIGGER_VERSION_INVALID","Trigger template version must belong to the template and be published");
        String enabled=text(value.get("enabled"));
        if((enabled==null||enabled.isBlank()||"Y".equals(enabled))&&!"0".equals(text(value(binding,"template_status","templateStatus"))))
            throw new TodoException("TODO_TRIGGER_TEMPLATE_INACTIVE","Enabled trigger must bind an active template");
    }
    private void validateTriggerCondition(Map<String,Object> value)
    {
        String eventType=String.valueOf(value.get("eventType"));
        int payloadVersion=value.get("payloadVersion")==null?1:Integer.parseInt(String.valueOf(value.get("payloadVersion")));
        var activeEvent=eventCatalog.activeEntry(eventType,payloadVersion);
        if(activeEvent==null)throw new TodoException("TODO_EVENT_CATALOG_REQUIRED","Trigger event type and payload version must be active and define a payload schema");
        String businessType=text(value.get("businessType"));
        if(businessType==null||!businessType.equals(activeEvent.businessObjectType()))
            throw new TodoException("TODO_TRIGGER_BUSINESS_TYPE_MISMATCH","Trigger business type must match the active event catalog");
        String schema=activeEvent.payloadSchemaJson();
        String json=text(value.get("conditionJson"));
        if(json==null||json.isBlank())return;
        ConditionValidator.ValidationResult result=conditionValidator.validate(json,schema,false);
        if(!result.valid())
        {
            ValidationIssue issue=result.issues().get(0);
            throw new TodoException(issue.code(),issue.message());
        }
    }
    private void validateTriggerEnable(long triggerRuleId,Integer expectedVersion)
    {
        Map<String,Object> binding=mapper.selectTriggerBindingForUpdate(triggerRuleId);
        if(binding==null||binding.isEmpty()||!Objects.equals(expectedVersion,integer(value(binding,"trigger_version","triggerVersion"))))
            throw new TodoException("TODO_TRIGGER_VERSION_CONFLICT","Trigger changed; refresh before retrying");
        String businessType=text(value(binding,"business_type","businessType"));validateBusinessType(businessType);
        if(!"0".equals(text(value(binding,"template_status","templateStatus"))))
            throw new TodoException("TODO_TRIGGER_TEMPLATE_INACTIVE","Trigger template must be active before enabling");
        Long templateId=number(value(binding,"template_id","templateId"));
        Long versionTemplateId=number(value(binding,"version_template_id","versionTemplateId"));
        if(templateId==null||!Objects.equals(templateId,versionTemplateId)||!"PUBLISHED".equals(text(value(binding,"version_status","versionStatus"))))
            throw new TodoException("TODO_TRIGGER_VERSION_INVALID","Trigger template version must belong to the template and be published");
        Map<String,Object> condition=new HashMap<>();condition.put("eventType",value(binding,"event_type","eventType"));condition.put("businessType",businessType);
        condition.put("payloadVersion",value(binding,"payload_version","payloadVersion"));condition.put("conditionJson",value(binding,"condition_json","conditionJson"));
        validateTriggerCondition(condition);
        if(isLeadIngress(text(value(binding,"event_type","eventType")),businessType))
            throw new TodoException("TODO_TRIGGER_ENTRY_SLOT_SWITCH_REQUIRED","Lead entry activation must use the entry-slot switch");
    }
    private void validateLeadIngress(Map<String,Object> value)
    {
        if(isLeadIngress(text(value.get("eventType")),text(value.get("businessType")))
                &&(!LEAD_ENTRY_SLOT.equals(text(value.get("entrySlotCode")))||!"N".equals(text(value.get("enabled")))))
            throw new TodoException("TODO_TRIGGER_ENTRY_SLOT_SWITCH_REQUIRED","Lead entry activation must use the entry-slot switch");
    }
    private void validateLeadIngressCommand(TriggerCommand command)
    {
        if(isLeadIngress(command.eventType(),command.businessType())
                &&(!LEAD_ENTRY_SLOT.equals(command.entrySlotCode())||!"N".equals(command.enabled())))
            throw new TodoException("TODO_TRIGGER_ENTRY_SLOT_SWITCH_REQUIRED","Lead entry activation must use the entry-slot switch");
    }
    private boolean isLeadIngress(String eventType,String businessType)
    {return "LEAD_ASSIGNED".equals(eventType)&&"LEAD".equals(businessType);}
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
    private Long claimTemplateAction(String actionId,String actionType,Long source,String fingerprint,Actor actor,Object command)
    {
        if(actionId==null||actionId.isBlank())throw new TodoException("TODO_TEMPLATE_ACTION_REQUIRED","actionId is required");
        Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);action.put("actionType",actionType);
        action.put("entityType","TEMPLATE");action.put("sourceEntityId",source);action.put("operatorId",actor.userId());
        action.put("operatorName",actor.userName());action.put("operatorDeptId",actor.deptId());action.put("requestFingerprint",fingerprint);
        action.put("payloadJson",JSON.toJSONString(command));int inserted=mapper.insertDefinitionActionClaim(action);
        Map<String,Object> locked=mapper.selectDefinitionActionForUpdate(actionId);
        if(locked==null||!actionType.equals(text(value(locked,"action_type","actionType")))
                ||!"TEMPLATE".equals(text(value(locked,"entity_type","entityType")))
                ||!fingerprint.equals(text(value(locked,"request_fingerprint","requestFingerprint")))
                ||!Objects.equals(source,number(value(locked,"source_entity_id","sourceEntityId")))
                ||!Objects.equals(actor.userId(),number(value(locked,"operator_id","operatorId")))
                ||!Objects.equals(actor.userName(),text(value(locked,"operator_name","operatorName")))
                ||!Objects.equals(actor.deptId(),number(value(locked,"operator_dept_id","operatorDeptId"))))
            throw new TodoException("TODO_TEMPLATE_ACTION_CONFLICT","action id conflicts");
        Long entity=number(value(locked,"entity_id","entityId"));String status=text(value(locked,"action_status","actionStatus"));
        if(entity!=null){if(!"APPLIED".equals(status))throw new TodoException("TODO_TEMPLATE_ACTION_CONFLICT","action incomplete");return entity;}
        if(inserted<=0||!"CLAIMED".equals(status))throw new TodoException("TODO_TEMPLATE_ACTION_CONFLICT","recorded action is incomplete");
        return null;
    }
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private Integer integer(Object value){return value==null?null:Integer.valueOf(String.valueOf(value));}
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

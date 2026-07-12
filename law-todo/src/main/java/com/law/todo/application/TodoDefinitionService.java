package com.law.todo.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoDefinitionService
{
    private static final String DRAFT="DRAFT";
    private static final String PUBLISHED="PUBLISHED";
    private final TodoMapper mapper;

    public TodoDefinitionService(TodoMapper mapper){this.mapper=mapper;}

    public List<Map<String,Object>> versions(Long templateId)
    {
        requireTemplate(templateId);return mapper.selectTemplateVersions(templateId);
    }

    @Transactional
    public Long copyTemplate(Long sourceTemplateId,CopyTemplateCommand command,Actor actor)
    {
        Long repeated=repeatedEntity(command.actionId());if(repeated!=null)return repeated;
        Map<String,Object> source=requireTemplate(sourceTemplateId);claim(command.actionId(),"COPY_TEMPLATE","TEMPLATE",sourceTemplateId,actor,Map.of("newTemplateCode",command.newTemplateCode()));
        Map<String,Object> target=new HashMap<>();target.put("templateCode",command.newTemplateCode());target.put("templateName",command.newTemplateName());target.put("businessType",value(source,"business_type","businessType"));target.put("status","0");target.put("createBy",actor.userName());
        if(mapper.insertTemplate(target)<=0)throw new TodoException("TODO_TEMPLATE_COPY_FAILED","模板复制失败");Long id=longValue(target.get("templateId"));mapper.updateDefinitionActionEntity(command.actionId(),id);return id;
    }

    @Transactional
    public Long copyVersion(Long templateId,int sourceVersionNo,CopyVersionCommand command,Actor actor)
    {
        Long repeated=repeatedEntity(command.actionId());if(repeated!=null)return repeated;
        Map<String,Object> source=mapper.selectTemplateVersion(templateId,sourceVersionNo);if(source==null||source.isEmpty())throw new TodoException("TODO_TEMPLATE_VERSION_NOT_FOUND","模板版本不存在");
        claim(command.actionId(),"COPY_VERSION","VERSION",longValue(value(source,"version_id","versionId")),actor,Map.of("newVersionNo",command.newVersionNo()));
        Map<String,Object> target=new HashMap<>();target.put("templateId",templateId);target.put("versionNo",command.newVersionNo());target.put("status",DRAFT);target.put("sourceVersionId",longValue(value(source,"version_id","versionId")));copyRule(source,target,"owner_rule_json","ownerRuleJson");copyRule(source,target,"dod_rule_json","dodRuleJson");copyRule(source,target,"sla_rule_json","slaRuleJson");copyRule(source,target,"next_rule_json","nextRuleJson");copyRule(source,target,"ui_schema_json","uiSchemaJson");
        if(mapper.insertTemplateVersion(target)<=0)throw new TodoException("TODO_TEMPLATE_VERSION_COPY_FAILED","模板版本复制失败");Long id=longValue(target.get("versionId"));mapper.updateDefinitionActionEntity(command.actionId(),id);return id;
    }

    @Transactional
    public Long updateDraft(UpdateDraftCommand command,Actor actor)
    {
        Long repeated=repeatedEntity(command.actionId());if(repeated!=null)return repeated;Map<String,Object> current=requireVersion(command.versionId());requireDraft(current);validate(command.ownerRuleJson(),command.dodRuleJson(),command.slaRuleJson(),command.nextRuleJson(),command.uiSchemaJson());
        claim(command.actionId(),"UPDATE_DRAFT","VERSION",command.versionId(),actor,Map.of());Map<String,Object> update=new HashMap<>();update.put("versionId",command.versionId());update.put("ownerRuleJson",command.ownerRuleJson());update.put("dodRuleJson",command.dodRuleJson());update.put("slaRuleJson",command.slaRuleJson());update.put("nextRuleJson",command.nextRuleJson());update.put("uiSchemaJson",command.uiSchemaJson());
        if(mapper.updateTemplateVersionDraft(update)<=0)throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","草稿版本已变化");return command.versionId();
    }

    @Transactional
    public Long publish(PublishDraftCommand command,Actor actor)
    {
        Long repeated=repeatedEntity(command.actionId());if(repeated!=null)return repeated;Map<String,Object> current=requireVersion(command.versionId());requireDraft(current);validate(text(value(current,"owner_rule_json","ownerRuleJson")),text(value(current,"dod_rule_json","dodRuleJson")),text(value(current,"sla_rule_json","slaRuleJson")),text(value(current,"next_rule_json","nextRuleJson")),text(value(current,"ui_schema_json","uiSchemaJson")));
        claim(command.actionId(),"PUBLISH_VERSION","VERSION",command.versionId(),actor,Map.of());if(mapper.publishTemplateVersionConditionally(command.versionId(),actor.userName())<=0)throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","草稿版本已发布或状态已变化");return command.versionId();
    }

    private void validate(String owner,String dod,String sla,String next,String ui)
    {
        validJson(owner,"ownerRuleJson");validObject(dod,"dodRuleJson");validObject(sla,"slaRuleJson");validObject(next,"nextRuleJson");validObject(ui,"uiSchemaJson");
        if(sla!=null&&!sla.isBlank()){JSONObject rule=JSON.parseObject(sla);String code=rule.getString("calendarCode");if(code==null||mapper.selectCalendarByCode(code)==null)throw new TodoException("TODO_SLA_CALENDAR_NOT_FOUND","SLA工作日历不存在");if(rule.getLongValue("minutes")<=0)throw new TodoException("TODO_SLA_MINUTES_INVALID","SLA分钟数必须大于0");}
        if(next!=null&&!next.isBlank()){Long id=JSON.parseObject(next).getLong("templateVersionId");if(id!=null){Map<String,Object> target=requireVersion(id);if(!PUBLISHED.equals(text(value(target,"status","status"))))throw new TodoException("TODO_NEXT_TEMPLATE_NOT_PUBLISHED","下一待办模板版本尚未发布");}}
    }

    private void validJson(String json,String field){if(json==null||json.isBlank()||!JSON.isValid(json))throw new TodoException("TODO_TEMPLATE_JSON_INVALID","模板JSON字段格式错误："+field);}
    private void validObject(String json,String field){if(json!=null&&!json.isBlank()&&!JSON.isValidObject(json))throw new TodoException("TODO_TEMPLATE_JSON_INVALID","模板JSON字段格式错误："+field);}
    private Map<String,Object> requireTemplate(Long id){Map<String,Object> value=mapper.selectTemplateById(id);if(value==null||value.isEmpty())throw new TodoException("TODO_TEMPLATE_NOT_FOUND","待办模板不存在");return value;}
    private Map<String,Object> requireVersion(Long id){Map<String,Object> value=mapper.selectTemplateVersionById(id);if(value==null||value.isEmpty())throw new TodoException("TODO_TEMPLATE_VERSION_NOT_FOUND","模板版本不存在");return value;}
    private void requireDraft(Map<String,Object> value){if(!DRAFT.equals(text(value(value,"status","status"))))throw new TodoException("TODO_TEMPLATE_VERSION_IMMUTABLE","已发布模板版本不可修改");}
    private Long repeatedEntity(String actionId){Map<String,Object> action=mapper.selectDefinitionActionById(actionId);return action==null||action.isEmpty()?null:longValue(value(action,"entity_id","entityId"));}
    private void claim(String actionId,String actionType,String entityType,Long source,Actor actor,Map<String,Object> payload){Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);action.put("actionType",actionType);action.put("entityType",entityType);action.put("sourceEntityId",source);action.put("operatorId",actor.userId());action.put("operatorName",actor.userName());action.put("payloadJson",JSON.toJSONString(payload));if(mapper.insertDefinitionActionIfAbsent(action)<=0)throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","定义操作幂等键冲突");}
    private void copyRule(Map<String,Object> source,Map<String,Object> target,String snake,String camel){target.put(camel,value(source,snake,camel));}
    private Object value(Map<String,Object> map,String snake,String camel){return map.containsKey(snake)?map.get(snake):map.get(camel);}
    private Long longValue(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}

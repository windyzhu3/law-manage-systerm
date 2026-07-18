package com.law.todo.application;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.application.command.TodoManagementCommands.TemplateCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
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
    @Transactional public int saveTrigger(TriggerCommand command){Map<String,Object> value=new HashMap<>();value.put("triggerRuleId",command.triggerRuleId());value.put("eventType",command.eventType());value.put("templateId",command.templateId());value.put("templateVersionId",command.templateVersionId());value.put("businessType",command.businessType());value.put("enabled",command.enabled()==null?"Y":command.enabled());value.put("conditionJson",command.conditionJson());value.put("payloadVersion",command.payloadVersion()==null?1:command.payloadVersion());return saveTrigger(value);}
    @Transactional public int saveTrigger(Map<String,Object> value){required(value,"eventType");required(value,"templateId");required(value,"templateVersionId");required(value,"businessType");validateTriggerCondition(value);return value.get("triggerRuleId")==null?mapper.insertTriggerRule(value):mapper.updateTriggerRule(value);}
    private void validateTriggerCondition(Map<String,Object> value)
    {
        String json=text(value.get("conditionJson"));
        if(json==null||json.isBlank())return;
        String eventType=String.valueOf(value.get("eventType"));
        int payloadVersion=value.get("payloadVersion")==null?1:Integer.parseInt(String.valueOf(value.get("payloadVersion")));
        String schema=eventCatalog.payloadSchema(eventType,payloadVersion);
        ConditionValidator.ValidationResult result=conditionValidator.validate(json,schema,false);
        if(!result.valid())
        {
            ValidationIssue issue=result.issues().get(0);
            throw new TodoException(issue.code(),issue.message());
        }
    }
    private String text(Object value){return value==null?null:String.valueOf(value);}
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

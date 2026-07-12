package com.law.todo.application;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.application.command.TodoManagementCommands.TemplateCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;

@Service
public class TodoTemplateService
{
    private final TodoMapper mapper;
    public TodoTemplateService(TodoMapper mapper){this.mapper=mapper;}
    public List<Map<String,Object>> listTemplates(){return mapper.selectTemplates();}
    @Transactional public int saveTemplate(TemplateCommand command,String operator){Map<String,Object> value=new HashMap<>();value.put("templateId",command.templateId());value.put("templateCode",command.templateCode());value.put("templateName",command.templateName());value.put("businessType",command.businessType());value.put("status",command.status()==null?"0":command.status());value.put("createBy",operator);value.put("updateBy",operator);return saveTemplate(value);}
    @Transactional public int saveTemplate(Map<String,Object> value){required(value,"templateCode");required(value,"templateName");required(value,"businessType");return value.get("templateId")==null?mapper.insertTemplate(value):mapper.updateTemplate(value);}
    public List<Map<String,Object>> listTriggers(){return mapper.selectAllTriggerRules();}
    @Transactional public int saveTrigger(TriggerCommand command){Map<String,Object> value=new HashMap<>();value.put("triggerRuleId",command.triggerRuleId());value.put("eventType",command.eventType());value.put("templateId",command.templateId());value.put("templateVersionId",command.templateVersionId());value.put("businessType",command.businessType());value.put("enabled",command.enabled()==null?"Y":command.enabled());value.put("conditionJson",command.conditionJson());return saveTrigger(value);}
    @Transactional public int saveTrigger(Map<String,Object> value){required(value,"eventType");required(value,"templateId");required(value,"templateVersionId");required(value,"businessType");return value.get("triggerRuleId")==null?mapper.insertTriggerRule(value):mapper.updateTriggerRule(value);}
    @Transactional public Long publish(Long templateId,int versionNo,String ownerJson,String dodJson,String slaJson,String nextJson,String user)
    {
        if(mapper.selectTemplateVersion(templateId,versionNo)!=null)throw new TodoException("TODO_TEMPLATE_VERSION_EXISTS","模板版本已存在");
        Map<String,Object> version=new HashMap<>();version.put("templateId",templateId);version.put("versionNo",versionNo);version.put("ownerRuleJson",ownerJson);version.put("dodRuleJson",dodJson);version.put("slaRuleJson",slaJson);version.put("nextRuleJson",nextJson);version.put("publishedBy",user);
        if(mapper.insertTemplateVersion(version)<=0)throw new TodoException("TODO_TEMPLATE_PUBLISH_FAILED","模板发布失败");mapper.updateTemplateCurrentVersion(templateId,versionNo,user);return Long.valueOf(String.valueOf(version.get("versionId")));
    }
    private void required(Map<String,Object> value,String key){if(value.get(key)==null||String.valueOf(value.get(key)).isBlank())throw new TodoException("TODO_TEMPLATE_FIELD_REQUIRED","缺少模板字段："+key);}
}

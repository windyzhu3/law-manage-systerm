package com.law.todo.application;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoTemplateService
{
    private final TodoMapper mapper;
    public TodoTemplateService(TodoMapper mapper){this.mapper=mapper;}
    @Transactional public Long publish(Long templateId,int versionNo,String ownerJson,String dodJson,String slaJson,String nextJson,String user)
    {
        if(mapper.selectTemplateVersion(templateId,versionNo)!=null)throw new TodoException("TODO_TEMPLATE_VERSION_EXISTS","模板版本已存在");
        Map<String,Object> version=new HashMap<>();version.put("templateId",templateId);version.put("versionNo",versionNo);version.put("ownerRuleJson",ownerJson);version.put("dodRuleJson",dodJson);version.put("slaRuleJson",slaJson);version.put("nextRuleJson",nextJson);version.put("publishedBy",user);
        if(mapper.insertTemplateVersion(version)<=0)throw new TodoException("TODO_TEMPLATE_PUBLISH_FAILED","模板发布失败");mapper.updateTemplateCurrentVersion(templateId,versionNo,user);return Long.valueOf(String.valueOf(version.get("versionId")));
    }
}

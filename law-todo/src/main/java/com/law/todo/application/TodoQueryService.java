package com.law.todo.application;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoFormView;
import com.law.todo.application.view.TodoFormView.MaterialState;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.alibaba.fastjson2.JSON;

@Service
public class TodoQueryService
{
    private final TodoMapper mapper;private final TodoAccessPolicy access;
    public TodoQueryService(TodoMapper mapper,TodoAccessPolicy access){this.mapper=mapper;this.access=access;}
    public Map<String,Object> dashboard(Long userId,Long deptId){return mapper.selectDashboard(userId,deptId);}
    public List<Map<String,Object>> list(Map<String,Object> query,Long userId,Long deptId){query.put("currentUserId",userId);query.put("currentDeptId",deptId);return mapper.selectTodoList(query);}
    public TodoInstance detail(Long id,Long userId,Long deptId){TodoInstance t=mapper.selectById(id);if(t==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");if(!access.canView(t,userId,deptId))throw new TodoException("TODO_ACCESS_DENIED","无权查看该待办");return t;}
    public Map<String,Object> detailView(Long id,Long userId,Long deptId){TodoInstance t=detail(id,userId,deptId);Map<String,Object> view=new HashMap<>();view.put("todo",t);view.put("actions",mapper.selectActionTimeline(id));view.put("attachments",mapper.selectAttachments(id));view.put("candidates",mapper.selectCandidates(id));view.put("cc",mapper.selectCc(id));view.put("relations",mapper.selectRelations(id));return view;}
    public TodoFormView form(long todoId,Actor actor)
    {
        TodoInstance todo=detail(todoId,actor.userId(),actor.deptId());
        Map<String,Object> version=mapper.selectTemplateVersionById(todo.getTemplateVersionId());
        if(version==null||version.isEmpty())throw new TodoException("TODO_DEFINITION_SNAPSHOT_NOT_FOUND","Todo definition snapshot is unavailable");
        TodoDefinitionDocument definition=definition(version);
        DodRule dod=definition.dod()==null?new DodRule(Map.of()):definition.dod();
        if(todo.getDodSnapshotJson()!=null&&!todo.getDodSnapshotJson().isBlank())dod=new DodRule(JSON.parseObject(todo.getDodSnapshotJson()));
        UiSchema ui=definition.ui()==null?new UiSchema(Map.of()):definition.ui();
        Map<String,Object> defaults=map(ui.config().get("defaults"));
        List<MaterialState> materials=new java.util.ArrayList<>();
        List<Map<String,Object>> attachments=mapper.selectAttachments(todoId);
        if(attachments!=null)for(Map<String,Object> attachment:attachments)materials.add(new MaterialState(longValue(value(attachment,"file_object_id","fileObjectId")),text(value(attachment,"attachment_type","attachmentType")),text(value(attachment,"file_name","fileName"))));
        return new TodoFormView(todoId,action(todo.getStatus()),ui,dod,defaults,materials,text(value(version,"definition_hash","definitionHash")));
    }
    private TodoDefinitionDocument definition(Map<String,Object> version){String json=text(value(version,"compiled_json","compiledJson"));if(json==null||json.isBlank())json=text(value(version,"definition_json","definitionJson"));if(json!=null&&!json.isBlank())return new TodoDefinitionCodec().read(json);return new TodoDefinitionDocument(1,text(value(version,"template_code","templateCode")),null,null,new DodRule(jsonMap(value(version,"dod_rule_json","dodRuleJson"))),null,new UiSchema(jsonMap(value(version,"ui_schema_json","uiSchemaJson"))),null,List.of(),List.of(),List.of());}
    private String action(String status){return switch(status==null?"":status){case "CREATED"->"CLAIM";case "CLAIMED","RETURNED"->"START";case "IN_PROGRESS"->"SUBMIT";case "SUBMITTED"->"COMPLETE";default->"VIEW";};}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value){return value instanceof Map<?,?>?(Map<String,Object>)value:Map.of();}
    private Map<String,Object> jsonMap(Object value){if(value==null)return Map.of();if(value instanceof Map<?,?>)return map(value);String text=String.valueOf(value);return text.isBlank()?Map.of():JSON.parseObject(text);}
    private Object value(Map<String,Object> map,String snake,String camel){return map.containsKey(snake)?map.get(snake):map.get(camel);}private String text(Object value){return value==null?null:String.valueOf(value);}private Long longValue(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
}

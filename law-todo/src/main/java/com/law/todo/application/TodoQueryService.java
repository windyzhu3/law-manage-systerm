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
import com.law.todo.application.view.TodoFormView.ExtensionPolicyView;
import com.law.file.application.FileMaterialQueryService;
import com.law.file.domain.FileObject.FileActor;
import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.alibaba.fastjson2.JSON;

@Service
public class TodoQueryService
{
    private final TodoMapper mapper;private final TodoAccessPolicy access;private final FileMaterialQueryService fileMaterials;
    public TodoQueryService(TodoMapper mapper,TodoAccessPolicy access){this(mapper,access,null);}
    @Autowired public TodoQueryService(TodoMapper mapper,TodoAccessPolicy access,FileMaterialQueryService fileMaterials)
    {this.mapper=mapper;this.access=access;this.fileMaterials=fileMaterials;}
    public Map<String,Object> dashboard(Long userId,Long deptId){return mapper.selectDashboard(userId,deptId);}
    public List<Map<String,Object>> list(Map<String,Object> query,Long userId,Long deptId){query.put("currentUserId",userId);query.put("currentDeptId",deptId);return mapper.selectTodoList(query);}
    public TodoInstance detail(Long id,Long userId,Long deptId){TodoInstance t=mapper.selectById(id);if(t==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");if(!access.canView(t,userId,deptId))throw new TodoException("TODO_ACCESS_DENIED","无权查看该待办");return t;}
    public Map<String,Object> detailView(Long id,Long userId,Long deptId){return detailView(id,new Actor(userId,"user-"+userId,deptId));}
    public Map<String,Object> detailView(Long id,Actor actor){TodoInstance t=detail(id,actor.userId(),actor.deptId());Map<String,Object> view=new HashMap<>();view.put("todo",t);view.put("actions",mapper.selectActionTimeline(id));view.put("attachments",mapper.selectAttachments(id));view.put("materials",materials(t,actor));view.put("candidates",mapper.selectCandidates(id));view.put("cc",mapper.selectCc(id));view.put("relations",mapper.selectRelations(id));return view;}
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
        List<MaterialState> materials=materials(todo,actor);
        return new TodoFormView(todoId,action(todo.getStatus()),todo.getBusinessType(),todo.getBusinessId(),ui,dod,
            defaults,materials,extensionPolicy(todoId),text(value(version,"definition_hash","definitionHash")));
    }
    private List<MaterialState> materials(TodoInstance todo,Actor actor)
    {
        if(fileMaterials==null)return List.of();
        return fileMaterials.list(todo.getBusinessType(),todo.getBusinessId(),new FileActor(actor.userId(),actor.userName(),actor.deptId()))
            .stream().map(value->new MaterialState(value.fileObjectId(),value.materialType(),value.fileName())).toList();
    }
    private ExtensionPolicyView extensionPolicy(Long todoId)
    {
        Map<String,Object> context=mapper.selectExtensionContext(todoId);if(context==null||context.isEmpty())return null;
        Long version=longValue(value(context,"policy_version_id","policyVersionId"));if(version==null)return null;
        int maximum=integer(value(context,"max_extension_count","maxExtensionCount"));
        int approved=mapper.countApprovedExtensions(todoId,version);
        return new ExtensionPolicyView(version,maximum,approved,Math.max(0,maximum-approved),
            longValue(value(context,"max_extension_value","maxExtensionValue")),text(value(context,"max_extension_unit","maxExtensionUnit")),
            Boolean.parseBoolean(String.valueOf(value(context,"proof_required","proofRequired"))),
            text(value(context,"pending_sla_mode","pendingSlaMode")),date(value(context,"due_at","dueAt")));
    }
    private TodoDefinitionDocument definition(Map<String,Object> version){String json=text(value(version,"compiled_json","compiledJson"));if(json==null||json.isBlank())json=text(value(version,"definition_json","definitionJson"));if(json!=null&&!json.isBlank())return new TodoDefinitionCodec().read(json);return new TodoDefinitionDocument(1,text(value(version,"template_code","templateCode")),null,null,new DodRule(jsonMap(value(version,"dod_rule_json","dodRuleJson"))),null,new UiSchema(jsonMap(value(version,"ui_schema_json","uiSchemaJson"))),null,List.of(),List.of(),List.of());}
    private String action(String status){return switch(status==null?"":status){case "CREATED"->"CLAIM";case "CLAIMED","RETURNED"->"START";case "IN_PROGRESS"->"SUBMIT";case "SUBMITTED"->"COMPLETE";default->"VIEW";};}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value){return value instanceof Map<?,?>?(Map<String,Object>)value:Map.of();}
    private Map<String,Object> jsonMap(Object value){if(value==null)return Map.of();if(value instanceof Map<?,?>)return map(value);String text=String.valueOf(value);return text.isBlank()?Map.of():JSON.parseObject(text);}
    private Object value(Map<String,Object> map,String snake,String camel){return map.containsKey(snake)?map.get(snake):map.get(camel);}private String text(Object value){return value==null?null:String.valueOf(value);}private Long longValue(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}private java.time.LocalDateTime date(Object value){return value instanceof java.time.LocalDateTime date?date:value==null?null:java.time.LocalDateTime.parse(String.valueOf(value).replace(' ','T'));}
}

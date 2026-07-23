package com.law.todo.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoResourceCommands.EventResourceCommand;
import com.law.todo.application.command.TodoResourceCommands.EventResourceStatusCommand;
import com.law.todo.application.view.TodoResourceViews.EventResourceDetail;
import com.law.todo.application.view.TodoResourceViews.EventResourceListItem;
import com.law.todo.application.view.TodoResourceViews.EventResourcePage;
import com.law.todo.application.view.TodoResourceViews.EventResourceReference;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;

/** Versioned event metadata used by business-admin configuration surfaces. */
@Service
public class TodoEventResourceService
{
    private static final Set<String> PROPERTY_TYPES=Set.of("string","integer","number","boolean","object","array");
    private final TodoConfigurationMapper mapper;
    private final TodoMapper todoMapper;

    public TodoEventResourceService(TodoConfigurationMapper mapper,TodoMapper todoMapper)
    {this.mapper=mapper;this.todoMapper=todoMapper;}

    @Transactional(readOnly=true)
    public EventResourcePage list(Map<String,Object> query)
    {
        Map<String,Object> values=query==null?Map.of():query;
        return new EventResourcePage(mapper.selectEventResources(values).stream().map(this::listItem).toList(),
                mapper.countEventResources(values));
    }

    @Transactional(readOnly=true)
    public EventResourceDetail detail(long id)
    {
        Map<String,Object> row=require(id);String eventType=text(row,"event_type","eventType");
        Integer payloadVersion=integer(row,"payload_version","payloadVersion");
        List<EventResourceReference> references=mapper.selectEventResourceReferences(eventType,payloadVersion).stream()
                .map(this::reference).toList();
        return detail(row,references);
    }

    @Transactional
    public long save(EventResourceCommand command,Actor actor)
    {
        if(command==null)throw invalidSchema("Event resource command is required");
        Actor user=requireActor(actor);Map<String,Object> current=null;
        if(command.eventCatalogId()!=null)
        {
            current=require(command.eventCatalogId());
            if("ACTIVE".equals(text(current,"status","status")))
                throw new TodoException("TODO_EVENT_RESOURCE_ACTIVE_IMMUTABLE","Active event versions cannot be edited; create a new version");
            requireSameIdentity(command,current);
        }
        else if(command.payloadVersion()!=1)
            throw new TodoException("TODO_EVENT_RESOURCE_VERSION_INVALID","New event resources must start at payload version 1");
        if(!"DRAFT".equals(command.status()))
            throw new TodoException("TODO_EVENT_RESOURCE_STATUS_INVALID","Event resources must be saved as drafts and published explicitly");
        validate(command.payloadSchemaJson(),command.samplePayloadJson());
        String fingerprint=fingerprint("SAVE_EVENT_RESOURCE",command.eventCatalogId(),command.expectedVersion(),command,user);
        Long replay=claim(command.actionId(),"SAVE_EVENT_RESOURCE",command.eventCatalogId(),fingerprint,user,command);
        if(replay!=null)return replay;
        if(command.eventCatalogId()==null&&mapper.selectEventResourceByTypeVersion(command.eventType(),command.payloadVersion())!=null)
            throw new TodoException("TODO_EVENT_RESOURCE_VERSION_EXISTS","Event payload version already exists");
        Map<String,Object> row=row(command,user);int changed;
        try{changed=command.eventCatalogId()==null?mapper.insertEventResource(row):mapper.updateEventResourceConditionally(row);}
        catch(DuplicateKeyException duplicate){throw new TodoException("TODO_EVENT_RESOURCE_VERSION_EXISTS","Event payload version already exists");}
        if(changed<=0)throw new TodoException("TODO_EVENT_RESOURCE_VERSION_CONFLICT","Event resource changed; refresh before retrying");
        Long id=command.eventCatalogId()==null?number(row.get("eventCatalogId")):command.eventCatalogId();
        if(id==null)throw new TodoException("TODO_EVENT_RESOURCE_VERSION_CONFLICT","Event resource identifier was not generated");
        complete(command.actionId(),fingerprint,id);return id;
    }

    @Transactional
    public long createNextVersion(long sourceId,String actionId,Actor actor)
    {
        Actor user=requireActor(actor);Map<String,Object> source=require(sourceId);
        Map<String,Object> request=new TreeMap<>();request.put("sourceId",sourceId);request.put("eventType",text(source,"event_type","eventType"));
        String fingerprint=fingerprint("CREATE_EVENT_RESOURCE_VERSION",sourceId,integer(source,"version","version"),request,user);
        Long replay=claim(actionId,"CREATE_EVENT_RESOURCE_VERSION",sourceId,fingerprint,user,request);if(replay!=null)return replay;
        Map<String,Object> row=new HashMap<>();row.put("eventType",text(source,"event_type","eventType"));
        row.put("eventName",text(source,"event_name","eventName"));row.put("description",text(source,"description","description"));
        row.put("payloadVersion",mapper.selectNextEventPayloadVersion(text(source,"event_type","eventType")));
        row.put("businessObjectType",text(source,"business_object_type","businessObjectType"));
        row.put("payloadSchemaJson",text(source,"payload_schema_json","payloadSchemaJson"));
        row.put("samplePayloadJson",text(source,"sample_payload_json","samplePayloadJson"));
        row.put("producer",text(source,"producer","producer"));row.put("sourceModule",text(source,"source_module","sourceModule"));
        row.put("schemaStatus",text(source,"schema_status","schemaStatus"));row.put("status","DRAFT");row.put("createBy",user.userName());
        try{if(mapper.insertEventResource(row)<=0)throw new TodoException("TODO_EVENT_RESOURCE_VERSION_CONFLICT","Event version could not be created");}
        catch(DuplicateKeyException duplicate){throw new TodoException("TODO_EVENT_RESOURCE_VERSION_EXISTS","Event payload version already exists");}
        Long id=number(row.get("eventCatalogId"));if(id==null)throw new TodoException("TODO_EVENT_RESOURCE_VERSION_CONFLICT","Event resource identifier was not generated");
        complete(actionId,fingerprint,id);return id;
    }

    @Transactional
    public void changeStatus(long id,EventResourceStatusCommand command,Actor actor)
    {
        if(command==null)throw new TodoException("TODO_EVENT_RESOURCE_STATUS_INVALID","Event resource status command is required");
        Actor user=requireActor(actor);Map<String,Object> current=require(id);
        if("ACTIVE".equals(command.status())&&!"READY".equals(text(current,"schema_status","schemaStatus")))
            throw new TodoException("TODO_EVENT_RESOURCE_SCHEMA_INCOMPLETE","Only READY event schemas can be activated");
        if("ACTIVE".equals(command.status()))
            validate(text(current,"payload_schema_json","payloadSchemaJson"),
                    text(current,"sample_payload_json","samplePayloadJson"));
        Map<String,Object> request=new TreeMap<>();request.put("status",command.status());request.put("expectedVersion",command.expectedVersion());
        String fingerprint=fingerprint("CHANGE_EVENT_RESOURCE_STATUS",id,command.expectedVersion(),request,user);
        if(claim(command.actionId(),"CHANGE_EVENT_RESOURCE_STATUS",id,fingerprint,user,request)!=null)return;
        Map<String,Object> row=new HashMap<>();row.put("eventCatalogId",id);row.put("status",command.status());
        row.put("expectedVersion",command.expectedVersion());row.put("updateBy",user.userName());
        if(mapper.updateEventResourceStatusConditionally(row)<=0)
            throw new TodoException("TODO_EVENT_RESOURCE_VERSION_CONFLICT","Event resource changed; refresh before retrying");
        complete(command.actionId(),fingerprint,id);
    }

    private void validate(String schemaJson,String sampleJson)
    {
        JSONObject schema;JSONObject sample;
        try{schema=JSON.parseObject(schemaJson);sample=JSON.parseObject(sampleJson);}catch(RuntimeException invalid){throw invalidSchema("Payload Schema must be a JSON object");}
        if(schema==null||!"object".equals(schema.getString("type")))throw invalidSchema("Payload Schema root type must be object");
        JSONObject properties=schema.getJSONObject("properties");
        if(properties==null||properties.isEmpty())throw invalidSchema("Payload Schema must define at least one selectable property");
        for(String name:properties.keySet())
        {
            JSONObject property=properties.getJSONObject(name);String type=property==null?null:property.getString("type");
            if(name.isBlank()||property==null||!PROPERTY_TYPES.contains(type)||blank(property.getString("title")))
                throw invalidSchema("Every payload property requires a supported type and a business label");
            if(sample.containsKey(name)&&!matches(type,sample.get(name)))throw invalidSample("Sample field type does not match Schema: "+name);
        }
        JSONArray required=schema.getJSONArray("required");
        if(required!=null)for(Object value:required)
        {
            String field=value==null?null:String.valueOf(value);
            if(blank(field)||!properties.containsKey(field))throw invalidSchema("Required field is not defined in properties: "+field);
            if(!sample.containsKey(field)||sample.get(field)==null)throw invalidSample("Sample payload is missing required field: "+field);
        }
    }

    private boolean matches(String type,Object value)
    {return switch(type){case "string"->value instanceof String;case "integer"->value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long;case "number"->value instanceof Number;case "boolean"->value instanceof Boolean;case "object"->value instanceof Map<?,?>;case "array"->value instanceof List<?>;default->false;};}
    private TodoException invalidSchema(String message){return new TodoException("TODO_EVENT_RESOURCE_SCHEMA_INVALID",message);}
    private TodoException invalidSample(String message){return new TodoException("TODO_EVENT_RESOURCE_SAMPLE_INVALID",message);}
    private void requireSameIdentity(EventResourceCommand command,Map<String,Object> current)
    {
        if(!Objects.equals(command.eventType(),text(current,"event_type","eventType"))
                ||!Objects.equals(command.payloadVersion(),integer(current,"payload_version","payloadVersion")))
            throw new TodoException("TODO_EVENT_RESOURCE_IDENTITY_IMMUTABLE","Event type and payload version cannot be changed");
    }
    private Map<String,Object> require(long id)
    {Map<String,Object> row=mapper.selectEventResource(id);if(row==null||row.isEmpty())throw new TodoException("TODO_EVENT_RESOURCE_NOT_FOUND","Event resource not found");return row;}
    private Actor requireActor(Actor actor)
    {if(actor==null||actor.userId()==null)throw new TodoException("TODO_ACCESS_DENIED","Event resource requires an authenticated actor");return actor;}

    private Map<String,Object> row(EventResourceCommand command,Actor actor)
    {
        Map<String,Object> row=new HashMap<>();row.put("eventCatalogId",command.eventCatalogId());row.put("eventType",command.eventType());
        row.put("eventName",command.eventName());row.put("description",command.description());row.put("payloadVersion",command.payloadVersion());
        row.put("businessObjectType",command.businessObjectType());row.put("sourceModule",command.sourceModule());
        row.put("payloadSchemaJson",command.payloadSchemaJson());row.put("samplePayloadJson",command.samplePayloadJson());
        row.put("schemaStatus","READY");row.put("status",command.status());row.put("producer",command.sourceModule());
        row.put("expectedVersion",command.expectedVersion());row.put("createBy",actor.userName());row.put("updateBy",actor.userName());return row;
    }

    private EventResourceListItem listItem(Map<String,Object> row)
    {return new EventResourceListItem(number(value(row,"event_catalog_id","eventCatalogId")),text(row,"event_type","eventType"),
            text(row,"event_name","eventName"),text(row,"description","description"),
            integer(row,"payload_version","payloadVersion"),text(row,"business_object_type","businessObjectType"),
            text(row,"source_module","sourceModule"),text(row,"schema_status","schemaStatus"),text(row,"status","status"),
            integer(row,"version","version"),longNumber(value(row,"reference_count","referenceCount")),date(value(row,"update_time","updateTime")));}
    private EventResourceDetail detail(Map<String,Object> row,List<EventResourceReference> references)
    {return new EventResourceDetail(number(value(row,"event_catalog_id","eventCatalogId")),text(row,"event_type","eventType"),
            text(row,"event_name","eventName"),text(row,"description","description"),integer(row,"payload_version","payloadVersion"),
            text(row,"business_object_type","businessObjectType"),text(row,"source_module","sourceModule"),text(row,"producer","producer"),
            text(row,"payload_schema_json","payloadSchemaJson"),text(row,"sample_payload_json","samplePayloadJson"),text(row,"schema_status","schemaStatus"),
            text(row,"status","status"),integer(row,"version","version"),text(row,"create_by","createBy"),date(value(row,"create_time","createTime")),
            text(row,"update_by","updateBy"),date(value(row,"update_time","updateTime")),references);}
    private EventResourceReference reference(Map<String,Object> row)
    {return new EventResourceReference(text(row,"reference_type","referenceType"),number(value(row,"reference_id","referenceId")),
            text(row,"reference_code","referenceCode"),text(row,"reference_name","referenceName"),text(row,"reference_status","referenceStatus"));}

    private static String fingerprint(String type,Long source,Integer version,Object request,Actor actor)
    {Map<String,Object> values=new TreeMap<>();values.put("actionType",type);values.put("sourceEntityId",source);values.put("expectedVersion",version);
        values.put("actorId",actor.userId());values.put("actorName",actor.userName());values.put("actorDeptId",actor.deptId());
        values.put("request",JSON.parse(JSON.toJSONString(request)));return TodoDefinitionSimulationService.sha256(JSON.toJSONString(values));}
    private Long claim(String actionId,String type,Long source,String fingerprint,Actor actor,Object request)
    {
        if(blank(actionId))throw new TodoException("TODO_EVENT_RESOURCE_ACTION_REQUIRED","Event resource actionId is required");
        Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);action.put("actionType",type);action.put("entityType","EVENT_RESOURCE");
        action.put("sourceEntityId",source);action.put("operatorId",actor.userId());action.put("operatorName",actor.userName());action.put("operatorDeptId",actor.deptId());
        action.put("requestFingerprint",fingerprint);action.put("payloadJson",JSON.toJSONString(request));
        int inserted=todoMapper.insertDefinitionActionClaim(action);Map<String,Object> locked=todoMapper.selectDefinitionActionForUpdate(actionId);
        if(locked==null||!type.equals(text(locked,"action_type","actionType"))||!"EVENT_RESOURCE".equals(text(locked,"entity_type","entityType"))
                ||!fingerprint.equals(text(locked,"request_fingerprint","requestFingerprint"))||!Objects.equals(source,number(value(locked,"source_entity_id","sourceEntityId")))
                ||!Objects.equals(actor.userId(),number(value(locked,"operator_id","operatorId")))||!Objects.equals(actor.userName(),text(locked,"operator_name","operatorName"))
                ||!Objects.equals(actor.deptId(),number(value(locked,"operator_dept_id","operatorDeptId"))) )
            throw new TodoException("TODO_EVENT_RESOURCE_ACTION_CONFLICT","Event resource action conflicts with a different request");
        Long entity=number(value(locked,"entity_id","entityId"));String status=text(locked,"action_status","actionStatus");
        if(entity!=null){if(!"APPLIED".equals(status))throw new TodoException("TODO_EVENT_RESOURCE_ACTION_CONFLICT","Recorded event action is incomplete");return entity;}
        if(inserted<=0||!"CLAIMED".equals(status))throw new TodoException("TODO_EVENT_RESOURCE_ACTION_CONFLICT","Recorded event action is incomplete");return null;
    }
    private void complete(String actionId,String fingerprint,Long entityId)
    {if(todoMapper.completeDefinitionAction(actionId,fingerprint,entityId)<=0)throw new TodoException("TODO_EVENT_RESOURCE_ACTION_CONFLICT","Event resource action could not be completed");}

    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private long longNumber(Object value){Long number=number(value);return number==null?0:number;}
    private Integer integer(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value==null?null:Integer.valueOf(String.valueOf(value));}
    private LocalDateTime date(Object value){if(value instanceof LocalDateTime time)return time;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();return null;}
    private boolean blank(String value){return value==null||value.isBlank();}
}

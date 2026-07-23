package com.law.todo.application;

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
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationResourceCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;

/** Governed editor for fields, materials, and completion recipes. */
@Service
public class TodoConfigurationResourceManagementService
{
    private static final Set<String> FIELD_TYPES=Set.of("string","integer","number","boolean","object","array");
    private static final Set<String> RECIPE_KEYS=Set.of("businessActions","templateStages","recommendationPriority",
            "requiredFields","requiredAttachments","validatorRefs","conditionalRules","employeeInstructions");
    private final TodoConfigurationMapper mapper;
    private final TodoMapper todoMapper;
    private final TodoConfigurationResourceCatalogService catalog;

    public TodoConfigurationResourceManagementService(TodoConfigurationMapper mapper,TodoMapper todoMapper,
            TodoConfigurationResourceCatalogService catalog)
    {this.mapper=mapper;this.todoMapper=todoMapper;this.catalog=catalog;}

    @Transactional
    public long save(ConfigurationResourceCommand command,Actor actor)
    {
        if(command==null)throw invalid("Configuration resource command is required");
        Actor user=requireActor(actor);JSONObject value=parse(command.valueJson());validate(command,value);
        Map<String,Object> current=null;
        if(command.resourceItemId()!=null)
        {
            current=mapper.selectConfigurationResourceItem(command.resourceItemId());
            if(current==null||current.isEmpty())
                throw new TodoException("TODO_CONFIGURATION_RESOURCE_NOT_FOUND","TODO_CONFIGURATION_RESOURCE_NOT_FOUND: Configuration resource not found");
            requireSameIdentity(command,current);
        }
        String fingerprint=fingerprint(command,user);
        Long replay=claim(command.actionId(),command.resourceItemId(),fingerprint,user,command);
        if(replay!=null)return replay;
        Map<String,Object> row=row(command,user);int changed;
        try
        {
            changed=command.resourceItemId()==null?mapper.insertConfigurationResourceItem(row)
                    :mapper.updateConfigurationResourceItemConditionally(row);
        }
        catch(DuplicateKeyException duplicate)
        {
            throw new TodoException("TODO_CONFIGURATION_RESOURCE_CODE_DUPLICATE",
                    "TODO_CONFIGURATION_RESOURCE_CODE_DUPLICATE: Resource code already exists for this business type");
        }
        if(changed<=0)
            throw new TodoException("TODO_CONFIGURATION_RESOURCE_VERSION_CONFLICT",
                    "TODO_CONFIGURATION_RESOURCE_VERSION_CONFLICT: Resource changed; refresh before retrying");
        Long id=command.resourceItemId()==null?number(row.get("resourceItemId")):command.resourceItemId();
        if(id==null)
            throw new TodoException("TODO_CONFIGURATION_RESOURCE_VERSION_CONFLICT",
                    "TODO_CONFIGURATION_RESOURCE_VERSION_CONFLICT: Resource identifier was not generated");
        complete(command.actionId(),fingerprint,id);return id;
    }

    private void validate(ConfigurationResourceCommand command,JSONObject value)
    {
        switch(command.resourceType())
        {
            case "FIELD"->validateField(value);
            case "MATERIAL"->{ }
            case "DOD_RECIPE"->validateRecipe(command.businessType(),value);
            default->throw invalid("Unsupported configuration resource type");
        }
    }

    private void validateField(JSONObject value)
    {
        String type=value.getString("type");
        if(type==null||!FIELD_TYPES.contains(type))throw invalid("Field resource requires a supported type");
    }

    private void validateRecipe(String businessType,JSONObject value)
    {
        if(!value.keySet().containsAll(RECIPE_KEYS)
                ||!stringArray(value,"businessActions")||!stringArray(value,"templateStages")
                ||value.getInteger("recommendationPriority")==null||value.getInteger("recommendationPriority")<0
                ||!stringArray(value,"requiredFields")||!stringArray(value,"requiredAttachments")
                ||!stringArray(value,"validatorRefs")||!objectArray(value,"conditionalRules")
                ||!stringArray(value,"employeeInstructions"))
            throw invalid("DoD recipe metadata is incomplete or invalid");
        for(String field:strings(value.getJSONArray("requiredFields")))
            if(!catalog.isKnownField(field,businessType))throw unknown("field",field);
        for(String material:strings(value.getJSONArray("requiredAttachments")))
            if(!catalog.isKnownMaterial(material,businessType))throw unknown("material",material);
        for(String validator:strings(value.getJSONArray("validatorRefs")))
            if(!catalog.isSelectableValidator(validator,businessType))throw unknown("validator",validator);
    }

    private boolean stringArray(JSONObject value,String key)
    {
        Object raw=value.get(key);if(!(raw instanceof JSONArray values))return false;
        return values.stream().allMatch(item->item instanceof String text&&!text.isBlank());
    }

    private boolean objectArray(JSONObject value,String key)
    {
        Object raw=value.get(key);if(!(raw instanceof JSONArray values))return false;
        return values.stream().allMatch(item->item instanceof JSONObject||item instanceof Map<?,?>);
    }

    private List<String> strings(JSONArray array)
    {return array==null?List.of():array.toJavaList(String.class);}

    private TodoException invalid(String detail)
    {return new TodoException("TODO_CONFIGURATION_RESOURCE_VALUE_INVALID","TODO_CONFIGURATION_RESOURCE_VALUE_INVALID: "+detail);}

    private TodoException unknown(String type,String code)
    {return new TodoException("TODO_CONFIGURATION_RESOURCE_REFERENCE_UNKNOWN",
            "TODO_CONFIGURATION_RESOURCE_REFERENCE_UNKNOWN: Unknown "+type+" reference "+code);}

    private JSONObject parse(String value)
    {
        try
        {
            JSONObject result=JSON.parseObject(value);
            if(result==null)throw invalid("Configuration resource value must be a JSON object");
            return result;
        }
        catch(TodoException error){throw error;}
        catch(RuntimeException error){throw invalid("Configuration resource value must be a JSON object");}
    }

    private Actor requireActor(Actor actor)
    {
        if(actor==null||actor.userId()==null||actor.userName()==null||actor.userName().isBlank())
            throw new TodoException("TODO_ACCESS_DENIED","TODO_ACCESS_DENIED: Configuration resource requires an authenticated actor");
        return actor;
    }

    private void requireSameIdentity(ConfigurationResourceCommand command,Map<String,Object> current)
    {
        if(!Objects.equals(command.resourceType(),text(current,"resource_type","resourceType"))
                ||!Objects.equals(command.resourceCode(),text(current,"resource_code","resourceCode"))
                ||!Objects.equals(command.businessType(),text(current,"business_type","businessType")))
            throw new TodoException("TODO_CONFIGURATION_RESOURCE_IDENTITY_IMMUTABLE",
                    "TODO_CONFIGURATION_RESOURCE_IDENTITY_IMMUTABLE: Resource type, business type, and code cannot be changed");
    }

    private Map<String,Object> row(ConfigurationResourceCommand command,Actor actor)
    {
        Map<String,Object> row=new HashMap<>();row.put("resourceItemId",command.resourceItemId());
        row.put("resourceType",command.resourceType());row.put("resourceCode",command.resourceCode());
        row.put("resourceName",command.resourceName());row.put("description",command.description());
        row.put("businessType",command.businessType());row.put("valueJson",command.valueJson());
        row.put("status",command.status());row.put("sortOrder",command.sortOrder());
        row.put("expectedVersion",command.expectedVersion());row.put("createBy",actor.userName());
        row.put("updateBy",actor.userName());return row;
    }

    private static String fingerprint(ConfigurationResourceCommand command,Actor actor)
    {
        Map<String,Object> values=new TreeMap<>();values.put("actionType","SAVE_CONFIGURATION_RESOURCE");
        values.put("sourceEntityId",command.resourceItemId());values.put("expectedVersion",command.expectedVersion());
        values.put("actorId",actor.userId());values.put("actorName",actor.userName());values.put("actorDeptId",actor.deptId());
        values.put("request",JSON.parse(JSON.toJSONString(command)));
        return TodoDefinitionSimulationService.sha256(JSON.toJSONString(values));
    }

    private Long claim(String actionId,Long source,String fingerprint,Actor actor,Object request)
    {
        if(actionId==null||actionId.isBlank())
            throw new TodoException("TODO_CONFIGURATION_RESOURCE_ACTION_REQUIRED",
                    "TODO_CONFIGURATION_RESOURCE_ACTION_REQUIRED: Resource actionId is required");
        Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);
        action.put("actionType","SAVE_CONFIGURATION_RESOURCE");action.put("entityType","CONFIGURATION_RESOURCE");
        action.put("sourceEntityId",source);action.put("operatorId",actor.userId());
        action.put("operatorName",actor.userName());action.put("operatorDeptId",actor.deptId());
        action.put("requestFingerprint",fingerprint);action.put("payloadJson",JSON.toJSONString(request));
        int inserted=todoMapper.insertDefinitionActionClaim(action);
        Map<String,Object> locked=todoMapper.selectDefinitionActionForUpdate(actionId);
        if(locked==null||!"SAVE_CONFIGURATION_RESOURCE".equals(text(locked,"action_type","actionType"))
                ||!"CONFIGURATION_RESOURCE".equals(text(locked,"entity_type","entityType"))
                ||!fingerprint.equals(text(locked,"request_fingerprint","requestFingerprint"))
                ||!Objects.equals(source,number(value(locked,"source_entity_id","sourceEntityId")))
                ||!Objects.equals(actor.userId(),number(value(locked,"operator_id","operatorId")))
                ||!Objects.equals(actor.userName(),text(locked,"operator_name","operatorName"))
                ||!Objects.equals(actor.deptId(),number(value(locked,"operator_dept_id","operatorDeptId"))))
            throw actionConflict();
        Long entity=number(value(locked,"entity_id","entityId"));
        String status=text(locked,"action_status","actionStatus");
        if(entity!=null)
        {
            if(!"APPLIED".equals(status))throw actionConflict();
            return entity;
        }
        if(inserted<=0||!"CLAIMED".equals(status))throw actionConflict();
        return null;
    }

    private void complete(String actionId,String fingerprint,long entityId)
    {
        if(todoMapper.completeDefinitionAction(actionId,fingerprint,entityId)<=0)throw actionConflict();
    }

    private TodoException actionConflict()
    {return new TodoException("TODO_CONFIGURATION_RESOURCE_ACTION_CONFLICT",
            "TODO_CONFIGURATION_RESOURCE_ACTION_CONFLICT: Resource action conflicts with another request");}
    private Object value(Map<String,Object> row,String snake,String camel)
    {return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
}

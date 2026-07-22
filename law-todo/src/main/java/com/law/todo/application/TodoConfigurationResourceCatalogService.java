package com.law.todo.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoBusinessValidator;

/** Safe read model for business-friendly configuration editors. */
@Service
public class TodoConfigurationResourceCatalogService
{
    private static final List<String> BUSINESS_TYPES=List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER");
    private final TodoConfigurationMapper mapper;
    private final Map<String,TodoBusinessValidator> validators;

    public TodoConfigurationResourceCatalogService(TodoConfigurationMapper mapper,List<TodoBusinessValidator> validators)
    {
        this.mapper=mapper;Map<String,TodoBusinessValidator> registered=new LinkedHashMap<>();
        for(TodoBusinessValidator validator:validators==null?List.<TodoBusinessValidator>of():validators)
            if(validator.catalogCode()!=null&&!validator.catalogCode().isBlank())registered.putIfAbsent(validator.catalogCode(),validator);
        this.validators=Map.copyOf(registered);
    }

    @Transactional(readOnly=true)
    public List<ValidatorResource> validators(String businessType)
    {
        List<Map<String,Object>> metadata=mapper.selectValidatorMetadata();
        Map<String,Map<String,Object>> governed=new LinkedHashMap<>();
        for(Map<String,Object> row:metadata==null?List.<Map<String,Object>>of():metadata)governed.put(text(row,"validator_code"),row);
        Set<String> codes=new LinkedHashSet<>(governed.keySet());codes.addAll(validators.keySet());List<ValidatorResource> result=new ArrayList<>();
        for(String code:codes)
        {
            Map<String,Object> row=governed.get(code);TodoBusinessValidator implementation=validators.get(code);
            List<String> types=row==null?supportedTypes(implementation):strings(row.get("business_types_json"));
            if(businessType!=null&&!businessType.isBlank()&&!types.contains(businessType))continue;
            String configured=row==null?"UNMANAGED":text(row,"status");
            String effective=implementation==null?"UNAVAILABLE":row==null?"UNMANAGED":"ACTIVE".equals(configured)?"ACTIVE":"DISABLED";
            result.add(new ValidatorResource(code,row==null?implementation.catalogName():text(row,"validator_name"),
                    row==null?implementation.catalogDescription():text(row,"description"),types,
                    row==null?implementation.parameterSchemaJson():json(row.get("parameter_schema_json"),"{}"),
                    row==null?implementation.exampleParametersJson():json(row.get("example_parameters_json"),"{}"),
                    implementation==null?null:implementation.getClass().getName(),configured,effective,
                    implementation!=null&&"ACTIVE".equals(effective),longNumber(row==null?null:row.get("reference_count"))));
        }
        return result.stream().sorted(Comparator.comparing(ValidatorResource::name).thenComparing(ValidatorResource::code)).toList();
    }

    @Transactional(readOnly=true)
    public List<FieldResource> fields(String businessType)
    {
        Map<String,MutableField> fields=new LinkedHashMap<>();
        List<Map<String,Object>> configured=mapper.selectConfigurationResourceItems("FIELD",businessType);
        for(Map<String,Object> row:configured==null?List.<Map<String,Object>>of():configured)
        {
            JSONObject value=parseObject(row.get("value_json"));String code=text(row,"resource_code");
            fields.putIfAbsent(code,new MutableField(code,text(row,"resource_name"),value.getString("type")));
        }
        List<Map<String,Object>> schemas=mapper.selectActiveEventResourceSchemas(businessType);
        for(Map<String,Object> event:schemas==null?List.<Map<String,Object>>of():schemas)
        {
            JSONObject schema=parseObject(event.get("payload_schema_json"));JSONArray required=schema.getJSONArray("required");
            Set<String> requiredCodes=required==null?Set.of():Set.copyOf(required.toJavaList(String.class));
            flatten(fields,"",schema.getJSONObject("properties"),requiredCodes,text(event,"event_type"),0);
        }
        return fields.values().stream().map(MutableField::view).sorted(Comparator.comparing(FieldResource::name).thenComparing(FieldResource::code)).toList();
    }

    @Transactional(readOnly=true)
    public List<MaterialResource> materials(String businessType)
    {return mapper.selectConfigurationResourceItems("MATERIAL",businessType).stream().map(row->new MaterialResource(
            text(row,"resource_code"),text(row,"resource_name"),text(row,"description"),text(row,"business_type"),
            text(row,"status"),integer(row.get("sort_order")))).toList();}

    @Transactional(readOnly=true)
    public List<DodRecipeResource> recipes(String businessType)
    {return mapper.selectConfigurationResourceItems("DOD_RECIPE",businessType).stream().map(row->{JSONObject value=parseObject(row.get("value_json"));return new DodRecipeResource(
            text(row,"resource_code"),text(row,"resource_name"),text(row,"description"),text(row,"business_type"),
            strings(value.get("requiredFields")),strings(value.get("requiredAttachments")),strings(value.get("validatorRefs")),
            listOfMaps(value.get("conditionalRules")));}).toList();}

    public boolean isSelectableValidator(String code,String businessType)
    {return validators(businessType).stream().anyMatch(row->row.code().equals(code)&&row.selectable());}
    public boolean isKnownField(String code,String businessType)
    {return fields(businessType).stream().anyMatch(row->row.code().equals(code));}
    public boolean isKnownMaterial(String code,String businessType)
    {return materials(businessType).stream().anyMatch(row->row.code().equals(code));}

    private void flatten(Map<String,MutableField> target,String prefix,JSONObject properties,Set<String> required,String eventType,int depth)
    {
        if(properties==null||depth>1)return;
        for(String name:properties.keySet())
        {
            JSONObject property=properties.getJSONObject(name);if(property==null)continue;String code=prefix.isEmpty()?name:prefix+"."+name;
            String type=property.getString("type");if("object".equals(type)){flatten(target,code,property.getJSONObject("properties"),Set.of(),eventType,depth+1);continue;}
            MutableField field=target.computeIfAbsent(code,key->new MutableField(code,property.getString("title"),type));
            field.required|=required.contains(name);field.events.add(eventType);
        }
    }

    private List<String> supportedTypes(TodoBusinessValidator validator)
    {return validator==null?List.of():BUSINESS_TYPES.stream().filter(validator::supports).toList();}
    private List<String> operators(String type)
    {return switch(type==null?"":type){case "integer","number"->List.of("EQ","NE","GT","GTE","LT","LTE","IN","NOT_IN","PRESENT");case "boolean"->List.of("EQ","NE","PRESENT");default->List.of("EQ","NE","IN","NOT_IN","PRESENT");};}
    private JSONObject parseObject(Object value){try{JSONObject object=value instanceof JSONObject json?json:JSON.parseObject(String.valueOf(value));return object==null?new JSONObject():object;}catch(RuntimeException invalid){return new JSONObject();}}
    private List<String> strings(Object value){try{if(value==null)return List.of();JSONArray array=value instanceof JSONArray json?json:JSON.parseArray(String.valueOf(value));return array==null?List.of():List.copyOf(array.toJavaList(String.class));}catch(RuntimeException invalid){return List.of();}}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> listOfMaps(Object value){try{if(value==null)return List.of();return JSON.parseArray(JSON.toJSONString(value),Map.class).stream().map(row->(Map<String,Object>)row).toList();}catch(RuntimeException invalid){return List.of();}}
    private String json(Object value,String fallback){return value==null?fallback:value instanceof String text?text:JSON.toJSONString(value);}
    private String text(Map<String,Object> row,String key){Object value=row==null?null:row.get(key);return value==null?null:String.valueOf(value);}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
    private long longNumber(Object value){return value==null?0:Long.parseLong(String.valueOf(value));}

    private final class MutableField
    {
        private final String code;private final String name;private final String type;private boolean required;private final Set<String> events=new LinkedHashSet<>();
        private MutableField(String code,String name,String type){this.code=code;this.name=name==null||name.isBlank()?code:name;this.type=type;}
        private FieldResource view(){return new FieldResource(code,name,type,required,operators(type),List.copyOf(events));}
    }
    public record ValidatorResource(String code,String name,String description,List<String> businessTypes,String parameterSchemaJson,
            String exampleParametersJson,String implementation,String configuredStatus,String effectiveStatus,boolean selectable,long referenceCount) { }
    public record FieldResource(String code,String name,String type,boolean required,List<String> operators,List<String> sourceEvents) { }
    public record MaterialResource(String code,String name,String description,String businessType,String status,int sortOrder) { }
    public record DodRecipeResource(String code,String name,String description,String businessType,List<String> requiredFields,
            List<String> requiredAttachments,List<String> validatorRefs,List<Map<String,Object>> conditionalRules) { }
}

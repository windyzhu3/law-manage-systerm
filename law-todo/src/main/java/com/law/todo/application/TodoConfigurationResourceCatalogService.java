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
import com.law.todo.application.TodoPayloadSchemaDescriptor.PayloadFieldDescriptor;

/** Safe read model for business-friendly configuration editors. */
@Service
public class TodoConfigurationResourceCatalogService
{
    private static final List<String> BUSINESS_TYPES=List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER");
    private final TodoConfigurationMapper mapper;
    private final Map<String,TodoBusinessValidator> validators;
    private final TodoPayloadSchemaDescriptor payloadSchemas;

    public TodoConfigurationResourceCatalogService(TodoConfigurationMapper mapper,List<TodoBusinessValidator> validators)
    {this(mapper,validators,new TodoPayloadSchemaDescriptor());}

    TodoConfigurationResourceCatalogService(TodoConfigurationMapper mapper,List<TodoBusinessValidator> validators,
            TodoPayloadSchemaDescriptor payloadSchemas)
    {
        this.mapper=mapper;Map<String,TodoBusinessValidator> registered=new LinkedHashMap<>();
        for(TodoBusinessValidator validator:validators==null?List.<TodoBusinessValidator>of():validators)
            if(validator.catalogCode()!=null&&!validator.catalogCode().isBlank())registered.putIfAbsent(validator.catalogCode(),validator);
        this.validators=Map.copyOf(registered);this.payloadSchemas=payloadSchemas==null?new TodoPayloadSchemaDescriptor():payloadSchemas;
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
                    configured,effective,
                    implementation!=null&&"ACTIVE".equals(effective),longNumber(row==null?null:row.get("reference_count"))));
        }
        return result.stream().sorted(Comparator.comparing(ValidatorResource::name).thenComparing(ValidatorResource::code)).toList();
    }

    @Transactional(readOnly=true)
    public List<FieldResource> fields(String businessType)
    {return fields(businessType,null);}

    @Transactional(readOnly=true)
    public List<FieldResource> fields(String businessType,String eventType)
    {
        Map<String,MutableField> fields=new LinkedHashMap<>();
        List<Map<String,Object>> configured=mapper.selectConfigurationResourceItems("FIELD",businessType);
        for(Map<String,Object> row:configured==null?List.<Map<String,Object>>of():configured)
        {
            JSONObject value=parseObject(row.get("value_json"));String code=text(row,"resource_code");
            if(code!=null&&!code.isBlank())fields.computeIfAbsent(code,MutableField::new).mergeGoverned(row,text(row,"resource_name"),value);
        }
        List<Map<String,Object>> schemas=mapper.selectActiveEventResourceSchemas(businessType);
        for(Map<String,Object> event:schemas==null?List.<Map<String,Object>>of():schemas)
        {
            String sourceEvent=text(event,"event_type");
            if(eventType!=null&&!eventType.isBlank()&&!eventType.equals(sourceEvent))continue;
            for(PayloadFieldDescriptor descriptor:payloadSchemas.describe(text(event,"payload_schema_json"),businessType))
                fields.computeIfAbsent(descriptor.path(),MutableField::new)
                        .merge(descriptor,sourceEvent,integer(event.get("payload_version")));
        }
        return fields.values().stream().map(MutableField::view).sorted(Comparator.comparing(FieldResource::name).thenComparing(FieldResource::code)).toList();
    }

    @Transactional(readOnly=true)
    public List<MaterialResource> materials(String businessType)
    {return mapper.selectConfigurationResourceItems("MATERIAL",businessType).stream().map(row->new MaterialResource(
            text(row,"resource_code"),text(row,"resource_name"),text(row,"description"),text(row,"business_type"),
            text(row,"status"),integer(row.get("sort_order")),longNullable(row.get("resource_item_id")),
            integer(row.get("version")),"GOVERNED")).toList();}

    @Transactional(readOnly=true)
    public List<DodRecipeResource> recipes(String businessType)
    {return mapper.selectConfigurationResourceItems("DOD_RECIPE",businessType).stream().map(row->{JSONObject value=parseObject(row.get("value_json"));return new DodRecipeResource(
            text(row,"resource_code"),text(row,"resource_name"),text(row,"description"),text(row,"business_type"),
            strings(value.get("businessActions")),strings(value.get("templateStages")),integer(value.get("recommendationPriority")),
            strings(value.get("requiredFields")),strings(value.get("requiredAttachments")),strings(value.get("validatorRefs")),
            listOfMaps(value.get("conditionalRules")),strings(value.get("employeeInstructions")),
            longNullable(row.get("resource_item_id")),integer(row.get("version")),"GOVERNED",
            text(row,"status"),integer(row.get("sort_order")));}).toList();}

    public boolean isSelectableValidator(String code,String businessType)
    {return validators(businessType).stream().anyMatch(row->row.code().equals(code)&&row.selectable());}
    public boolean isKnownField(String code,String businessType)
    {return fields(businessType).stream().anyMatch(row->row.code().equals(code));}
    public boolean isKnownMaterial(String code,String businessType)
    {return materials(businessType).stream().anyMatch(row->row.code().equals(code));}

    private List<String> supportedTypes(TodoBusinessValidator validator)
    {return validator==null?List.of():BUSINESS_TYPES.stream().filter(validator::supports).toList();}
    private static boolean businessLabel(String value)
    {
        if(value==null||value.isBlank())return false;String label=value.trim();
        return !(label.startsWith("{")||label.startsWith("[")||label.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*"));
    }
    private static boolean chineseLabel(String value)
    {return businessLabel(value)&&value.codePoints().anyMatch(character->Character.UnicodeScript.of(character)==Character.UnicodeScript.HAN);}
    private List<String> operators(String type)
    {return switch(type==null?"":type){case "integer","number"->List.of("EQ","NE","GT","GTE","LT","LTE","IN","NOT_IN","PRESENT");case "boolean"->List.of("EQ","NE","PRESENT");default->List.of("EQ","NE","IN","NOT_IN","CONTAINS","PRESENT");};}
    private JSONObject parseObject(Object value){try{JSONObject object=value instanceof JSONObject json?json:JSON.parseObject(String.valueOf(value));return object==null?new JSONObject():object;}catch(RuntimeException invalid){return new JSONObject();}}
    private List<String> strings(Object value){try{if(value==null)return List.of();JSONArray array=value instanceof JSONArray json?json:JSON.parseArray(String.valueOf(value));return array==null?List.of():List.copyOf(array.toJavaList(String.class));}catch(RuntimeException invalid){return List.of();}}
    private List<Object> objects(Object value){try{if(value==null)return List.of();JSONArray array=value instanceof JSONArray json?json:JSON.parseArray(String.valueOf(value));return array==null?List.of():List.copyOf(array.toList(Object.class));}catch(RuntimeException invalid){return List.of();}}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> listOfMaps(Object value){try{if(value==null)return List.of();return JSON.parseArray(JSON.toJSONString(value),Map.class).stream().map(row->(Map<String,Object>)row).toList();}catch(RuntimeException invalid){return List.of();}}
    private String text(Map<String,Object> row,String key){Object value=row==null?null:row.get(key);return value==null?null:String.valueOf(value);}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
    private long longNumber(Object value){return value==null?0:Long.parseLong(String.valueOf(value));}
    private Long longNullable(Object value){return value==null?null:Long.parseLong(String.valueOf(value));}

    private final class MutableField
    {
        private final String code;private String name="业务字段";private boolean governedName;private String type="string";private boolean required;
        private Object example;private boolean sensitive;private final Set<String> operators=new LinkedHashSet<>();
        private final Set<String> events=new LinkedHashSet<>();private final Set<Object> options=new LinkedHashSet<>();
        private final Set<String> eventVersions=new LinkedHashSet<>();
        private Long resourceItemId;private int version;private String source="EVENT_SCHEMA";
        private String description;private String businessType;private String status;private int sortOrder;
        private MutableField(String code){this.code=code;}
        private void mergeGoverned(Map<String,Object> row,String governedName,JSONObject value)
        {
            resourceItemId=longNullable(row.get("resource_item_id"));version=integer(row.get("version"));source="GOVERNED";
            description=text(row,"description");businessType=text(row,"business_type");status=text(row,"status");
            sortOrder=integer(row.get("sort_order"));
            if(chineseLabel(governedName)){name=governedName.trim();this.governedName=true;}
            String type=value.getString("type");if(type!=null&&!type.isBlank())this.type=type;
            required|=Boolean.TRUE.equals(value.getBoolean("required"));if(example==null)example=value.get("example");
            sensitive|=Boolean.TRUE.equals(value.getBoolean("sensitive"))||Boolean.TRUE.equals(value.getBoolean("x-sensitive"));
            operators.addAll(strings(value.get("operators")));options.addAll(objects(value.get("options")));
        }
        private void merge(PayloadFieldDescriptor descriptor,String event,int payloadVersion)
        {
            if(!governedName&&businessLabel(descriptor.label()))name=descriptor.label().trim();
            if(descriptor.type()!=null&&!descriptor.type().isBlank())type=descriptor.type();required|=descriptor.required();
            if(example==null)example=descriptor.example();sensitive|=descriptor.sensitive();operators.addAll(descriptor.operators());
            options.addAll(descriptor.options());if(event!=null&&!event.isBlank())
            {
                events.add(event);if(payloadVersion>0)eventVersions.add(event+"@"+payloadVersion);
            }
        }
        private FieldResource view()
        {return new FieldResource(code,name,type,required,sensitive?null:example,sensitive,
                operators.isEmpty()?operators(type):List.copyOf(operators),List.copyOf(events),List.copyOf(options),
                resourceItemId,version,source,description,businessType,status,sortOrder,List.copyOf(eventVersions));}
    }
    public record ValidatorResource(String code,String name,String description,List<String> businessTypes,
            String configuredStatus,String effectiveStatus,boolean selectable,long referenceCount) { }
    public record FieldResource(String code,String name,String type,boolean required,Object example,boolean sensitive,
            List<String> operators,List<String> sourceEvents,List<Object> options,
            Long resourceItemId,int version,String source,String description,String businessType,String status,int sortOrder,
            List<String> sourceEventVersions)
    {
        public FieldResource
        {
            name=businessLabel(name)?name.trim():"业务字段";operators=operators==null?List.of():List.copyOf(operators);
            sourceEvents=sourceEvents==null?List.of():List.copyOf(sourceEvents);options=options==null?List.of():List.copyOf(options);
            sourceEventVersions=sourceEventVersions==null?List.of():List.copyOf(sourceEventVersions);
            if(sensitive)example=null;
        }
        public FieldResource(String code,String name,String type,boolean required,List<String> operators,List<String> sourceEvents)
        {this(code,name,type,required,null,false,operators,sourceEvents,List.of(),null,0,"EVENT_SCHEMA",null,null,null,0,List.of());}
        public FieldResource(String code,String name,String type,boolean required,Object example,boolean sensitive,
                List<String> operators,List<String> sourceEvents,List<Object> options)
        {this(code,name,type,required,example,sensitive,operators,sourceEvents,options,null,0,"EVENT_SCHEMA",null,null,null,0,List.of());}
    }
    public record MaterialResource(String code,String name,String description,String businessType,String status,int sortOrder,
            Long resourceItemId,int version,String source)
    {
        public MaterialResource(String code,String name,String description,String businessType,String status,int sortOrder)
        {this(code,name,description,businessType,status,sortOrder,null,0,"GOVERNED");}
    }
    public record DodRecipeResource(String code,String name,String description,String businessType,
            List<String> businessActions,List<String> templateStages,int recommendationPriority,
            List<String> requiredFields,List<String> requiredAttachments,List<String> validatorRefs,
            List<Map<String,Object>> conditionalRules,List<String> employeeInstructions,
            Long resourceItemId,int version,String source,String status,int sortOrder)
    {
        public DodRecipeResource
        {
            businessActions=businessActions==null?List.of():List.copyOf(businessActions);
            templateStages=templateStages==null?List.of():List.copyOf(templateStages);
            requiredFields=requiredFields==null?List.of():List.copyOf(requiredFields);
            requiredAttachments=requiredAttachments==null?List.of():List.copyOf(requiredAttachments);
            validatorRefs=validatorRefs==null?List.of():List.copyOf(validatorRefs);
            conditionalRules=conditionalRules==null?List.of():List.copyOf(conditionalRules);
            employeeInstructions=employeeInstructions==null?List.of():List.copyOf(employeeInstructions);
        }
        public DodRecipeResource(String code,String name,String description,String businessType,
                List<String> requiredFields,List<String> requiredAttachments,List<String> validatorRefs,
                List<Map<String,Object>> conditionalRules)
        {
            this(code,name,description,businessType,List.of(),List.of(),0,requiredFields,requiredAttachments,
                    validatorRefs,conditionalRules,List.of(),null,0,"GOVERNED","ACTIVE",0);
        }
        public DodRecipeResource(String code,String name,String description,String businessType,
                List<String> businessActions,List<String> templateStages,int recommendationPriority,
                List<String> requiredFields,List<String> requiredAttachments,List<String> validatorRefs,
                List<Map<String,Object>> conditionalRules,List<String> employeeInstructions)
        {this(code,name,description,businessType,businessActions,templateStages,recommendationPriority,
                requiredFields,requiredAttachments,validatorRefs,conditionalRules,employeeInstructions,null,0,"GOVERNED","ACTIVE",0);}
    }
}

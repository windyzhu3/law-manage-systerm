package com.law.todo.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.view.TodoConfigurationViews.RoutingTargetCatalogEntry;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Business-facing completion outcomes and safe recommended routing targets. */
@Service
public class TodoBusinessOutcomeCatalogService
{
    private static final Map<String,String> TD001_TARGETS=Map.of(
            "VALID","TD-004",
            "SUSPECT_INVALID","TD-002",
            "UNREACHABLE","TD-003");
    private final TodoConfigurationResourceCatalogService resources;
    private final TodoTemplateService templates;
    private final TodoConfigurationMapper mapper;

    public TodoBusinessOutcomeCatalogService(TodoConfigurationResourceCatalogService resources,
            TodoTemplateService templates,TodoConfigurationMapper mapper)
    {
        this.resources=resources;this.templates=templates;this.mapper=mapper;
    }

    public BusinessOutcomeSet resolve(String templateCode,String businessType,TodoDefinitionDocument definition)
    {
        List<FieldResource> fields=resources.fields(businessType);
        FieldResource resultField=resultField(fields,definition);
        if(resultField==null)return BusinessOutcomeSet.empty();
        Map<String,RoutingTargetCatalogEntry> targetsByCode=new LinkedHashMap<>();
        for(RoutingTargetCatalogEntry target:templates.listRoutingTargetCatalog())
            if(target.templateCode()!=null)targetsByCode.put(target.templateCode(),target);
        List<BusinessOutcomeOption> options=new ArrayList<>();
        for(Map<String,Object> option:options(resultField))
        {
            String value=text(value(option,"value","dict_value"));
            if(value==null||value.isBlank())continue;
            String targetCode="TD-001".equals(templateCode)?TD001_TARGETS.get(value):null;
            RoutingTargetCatalogEntry target=targetCode==null?null:targetsByCode.get(targetCode);
            options.add(new BusinessOutcomeOption(value,text(value(option,"label","dict_label")),
                    targetCode,target==null?null:target.templateName(),target==null?null:target.versionId()));
        }
        return new BusinessOutcomeSet(resultField.code(),resultField.name(),resultField.description(),
                List.copyOf(options),"TD-001".equals(templateCode)?"TD001_STANDARD_ROUTE":null);
    }

    public List<OutcomeIssue> validate(String templateCode,String businessType,TodoDefinitionDocument definition)
    {
        BusinessOutcomeSet outcomeSet=resolve(templateCode,businessType,definition);
        if(!outcomeSet.available())return List.of();
        Map<String,BusinessOutcomeOption> expected=new LinkedHashMap<>();
        outcomeSet.options().forEach(option->expected.put(option.value(),option));
        List<Map<String,Object>> configured=outcomes(definition);
        List<OutcomeIssue> issues=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        Set<String> covered=new LinkedHashSet<>();
        for(int index=0;index<configured.size();index++)
        {
            Map<String,Object> outcome=configured.get(index);
            String path="routing.businessOutcomes["+index+"]";
            String field=text(outcome.get("resultField"));
            String result=text(outcome.get("resultValue"));
            if(!outcomeSet.resultField().equals(field))
            {
                issues.add(new OutcomeIssue("TODO_ROUTING_RESULT_FIELD_INVALID",path+".resultField",
                        "业务结果字段必须选择“"+outcomeSet.resultFieldName()+"”"));
                continue;
            }
            BusinessOutcomeOption option=expected.get(result);
            if(option==null)
            {
                issues.add(new OutcomeIssue("TODO_ROUTING_RESULT_VALUE_INVALID",path+".resultValue",
                        "请选择“"+outcomeSet.resultFieldName()+"”中的有效结果"));
                continue;
            }
            if(!seen.add(result))
            {
                issues.add(new OutcomeIssue("TODO_ROUTING_OUTCOME_DUPLICATE",path+".resultValue",
                        "业务结果“"+option.label()+"”只能配置一次"));
                continue;
            }
            covered.add(result);
            String resultType=text(outcome.get("resultType"));
            Long targetVersionId=longValue(outcome.get("targetVersionId"));
            if(!"NEXT".equals(resultType)||option.targetVersionId()==null
                    ||!option.targetVersionId().equals(targetVersionId))
                issues.add(new OutcomeIssue("TODO_ROUTING_TARGET_VERSION_INVALID",path+".targetVersionId",
                        "请将“"+option.label()+"”路由到已发布的“"+option.targetTemplateName()+"”"));
        }
        if(!covered.containsAll(expected.keySet()))
        {
            List<String> missing=expected.values().stream().filter(option->!covered.contains(option.value()))
                    .map(BusinessOutcomeOption::label).toList();
            issues.add(new OutcomeIssue("TODO_ROUTING_OUTCOME_INCOMPLETE","routing.businessOutcomes",
                    "尚未配置这些业务结果："+String.join("、",missing)));
        }
        return List.copyOf(issues);
    }

    private FieldResource resultField(List<FieldResource> fields,TodoDefinitionDocument definition)
    {
        List<String> required=definition==null||definition.dod()==null?List.of():strings(
                definition.dod().config().get("requiredFields"));
        return fields.stream().filter(field->required.contains(field.code()))
                .filter(this::selectableOutcomeField)
                .sorted((left,right)->{
                    if("contactResult".equals(left.code()))return -1;
                    if("contactResult".equals(right.code()))return 1;
                    return left.name().compareTo(right.name());
                }).findFirst().orElse(null);
    }

    private boolean selectableOutcomeField(FieldResource field)
    {
        return !field.options().isEmpty()||field.dictType()!=null&&!field.dictType().isBlank();
    }

    private List<Map<String,Object>> options(FieldResource field)
    {
        if(field.dictType()!=null&&!field.dictType().isBlank())
        {
            List<Map<String,Object>> rows=mapper.selectEnabledDictionaryData(field.dictType());
            return rows==null?List.of():rows;
        }
        List<Map<String,Object>> values=new ArrayList<>();
        for(Object raw:field.options())
        {
            if(raw instanceof Map<?,?> map)
            {
                Map<String,Object> copy=new LinkedHashMap<>();
                map.forEach((key,value)->copy.put(String.valueOf(key),value));
                values.add(copy);
            }
            else values.add(Map.of("label",String.valueOf(raw),"value",raw));
        }
        return values;
    }

    private List<Map<String,Object>> outcomes(TodoDefinitionDocument definition)
    {
        Object raw=definition==null||definition.routing()==null?null:
                definition.routing().config().get("businessOutcomes");
        if(!(raw instanceof List<?> list))return List.of();
        List<Map<String,Object>> result=new ArrayList<>();
        for(Object item:list)
        {
            if(!(item instanceof Map<?,?> source))continue;
            Map<String,Object> copy=new LinkedHashMap<>();
            source.forEach((key,value)->copy.put(String.valueOf(key),value));
            result.add(copy);
        }
        return result;
    }

    private List<String> strings(Object value)
    {
        if(!(value instanceof List<?> list))return List.of();
        return list.stream().filter(item->item!=null&&!String.valueOf(item).isBlank()).map(String::valueOf).toList();
    }

    private Object value(Map<String,Object> row,String camel,String snake)
    {return row.containsKey(camel)?row.get(camel):row.get(snake);}

    private String text(Object value)
    {return value==null?null:String.valueOf(value);}

    private Long longValue(Object value)
    {
        if(value instanceof Number number)return number.longValue();
        try{return value==null?null:Long.valueOf(String.valueOf(value));}
        catch(NumberFormatException invalid){return null;}
    }

    public record OutcomeIssue(String code,String path,String message) { }

    public record BusinessOutcomeOption(String value,String label,String targetTemplateCode,
            String targetTemplateName,Long targetVersionId)
    {
        public BusinessOutcomeOption
        {label=label==null||label.isBlank()?value:label;}
    }

    public record BusinessOutcomeSet(String resultField,String resultFieldName,String description,
            List<BusinessOutcomeOption> options,String recommendationCode)
    {
        public BusinessOutcomeSet
        {options=options==null?List.of():List.copyOf(options);}
        public static BusinessOutcomeSet empty()
        {return new BusinessOutcomeSet(null,null,null,List.of(),null);}
        public boolean available(){return resultField!=null&&!resultField.isBlank()&&!options.isEmpty();}
    }
}

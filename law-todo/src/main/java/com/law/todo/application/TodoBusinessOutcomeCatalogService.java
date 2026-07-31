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

/** Business-facing completion outcomes and safe, business-scoped routing targets. */
@Service
public class TodoBusinessOutcomeCatalogService
{
    private static final Map<String,String> TD001_TARGETS=Map.of(
            "VALID","TD-004",
            "SUSPECT_INVALID","TD-002",
            "UNREACHABLE","TD-003");
    private static final Map<String,GovernedOutcomeSpec> GOVERNED_OUTCOMES=Map.of(
            "TD-002",new GovernedOutcomeSpec("reviewResult","复核结果","主管复核结论","TD002_GOVERNED_OUTCOMES",
                    List.of(outcome("TRUE_INVALID","确认无效","END",null),
                            outcome("MISJUDGED_VALID","误判有效","NEXT_TEMPLATE","TD-001"))),
            "TD-003",new GovernedOutcomeSpec("contactResult","联系结果","无法联系重试的处理结果","TD003_GOVERNED_OUTCOMES",
                    List.of(outcome("CONNECTED","联系成功","NEXT_TEMPLATE","TD-004"),
                            outcome("CONTINUE_CURRENT_WINDOW","本窗口继续","RETAIN_CURRENT",null),
                            outcome("NEXT_WINDOW","进入下一窗口","SCHEDULE_NEXT",null),
                            outcome("EXHAUSTED","全部重试耗尽","END",null))),
            "TD-004",new GovernedOutcomeSpec("result","处理结果","实质进展完成后的系统动作","TD004_GOVERNED_OUTCOMES",
                    List.of(outcome("PROGRESS_RECORDED","已记录实质进展","SCHEDULE_SELF","TD-004"))));

    private final TodoConfigurationResourceCatalogService resources;
    private final TodoConfigurationMapper mapper;

    public TodoBusinessOutcomeCatalogService(TodoConfigurationResourceCatalogService resources,
            TodoConfigurationMapper mapper)
    {this.resources=resources;this.mapper=mapper;}

    public BusinessOutcomeSet resolve(String templateCode,String businessType,TodoDefinitionDocument definition)
    {
        Map<String,RoutingTargetCatalogEntry> targetsByCode=targetsByCode(businessType);
        GovernedOutcomeSpec governed=GOVERNED_OUTCOMES.get(templateCode);
        if(governed!=null)
        {
            List<BusinessOutcomeOption> options=governed.options().stream()
                    .map(option->option(option,targetsByCode)).toList();
            return new BusinessOutcomeSet(governed.resultField(),governed.resultFieldName(),governed.description(),
                    options,governed.recommendationCode());
        }
        List<FieldResource> fields=resources.fields(businessType);
        FieldResource resultField=resultField(fields,definition);
        if(resultField==null)return BusinessOutcomeSet.empty();
        List<BusinessOutcomeOption> options=new ArrayList<>();
        for(Map<String,Object> option:options(resultField))
        {
            String value=text(value(option,"value","dict_value"));
            if(value==null||value.isBlank())continue;
            String targetCode="TD-001".equals(templateCode)?TD001_TARGETS.get(value):null;
            RoutingTargetCatalogEntry target=targetCode==null?null:targetsByCode.get(targetCode);
            options.add(new BusinessOutcomeOption(value,text(value(option,"label","dict_label")),
                    targetCode==null?"END":"NEXT_TEMPLATE",targetCode,
                    target==null?null:target.templateName(),target==null?null:target.versionId()));
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
            Map<String,Object> configuredOutcome=configured.get(index);
            String path="routing.businessOutcomes["+index+"]";
            String field=firstText(configuredOutcome,"resultField","field");
            String result=firstText(configuredOutcome,"resultValue","value");
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
            String configuredLabel=text(configuredOutcome.get("resultLabel"));
            if((configuredLabel==null||configuredLabel.isBlank())
                    &&(configuredOutcome.containsKey("effectKind")||configuredOutcome.containsKey("field")))
                configuredLabel=text(configuredOutcome.get("label"));
            if(configuredLabel!=null&&!configuredLabel.isBlank()&&!option.label().equals(configuredLabel))
                issues.add(new OutcomeIssue("TODO_ROUTING_OUTCOME_LABEL_INVALID",path+".label",
                        "业务结果名称必须使用受治理名称“"+option.label()+"”"));
            String effect=effectKind(configuredOutcome);
            if(!option.effectKind().equals(effect))
                issues.add(new OutcomeIssue("TODO_ROUTING_EFFECT_INVALID",path+".effectKind",
                        "业务结果“"+option.label()+"”必须使用系统动作“"+option.effectKind()+"”"));
            validateTarget(configuredOutcome,option,path,issues);
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

    private void validateTarget(Map<String,Object> configured,BusinessOutcomeOption option,String path,
            List<OutcomeIssue> issues)
    {
        Long targetVersionId=longValue(configured.get("targetVersionId"));
        String targetTemplateCode=text(configured.get("targetTemplateCode"));
        if(option.targetTemplateCode()==null)
        {
            if(targetVersionId!=null||targetTemplateCode!=null&&!targetTemplateCode.isBlank())
                issues.add(new OutcomeIssue("TODO_ROUTING_TARGET_NOT_ALLOWED",path+".targetVersionId",
                        "该系统动作不允许选择后续待办"));
            return;
        }
        if(option.targetVersionId()==null||!option.targetVersionId().equals(targetVersionId)
                ||targetTemplateCode!=null&&!targetTemplateCode.isBlank()
                        &&!option.targetTemplateCode().equals(targetTemplateCode))
            issues.add(new OutcomeIssue("TODO_ROUTING_TARGET_VERSION_INVALID",path+".targetVersionId",
                    "请将“"+option.label()+"”路由到已发布的“"+
                            (option.targetTemplateName()==null?option.targetTemplateCode():option.targetTemplateName())+"”"));
    }

    private Map<String,RoutingTargetCatalogEntry> targetsByCode(String businessType)
    {
        List<Map<String,Object>> rows=mapper.selectPublishedRoutingTargetCatalog(businessType);
        Map<String,RoutingTargetCatalogEntry> result=new LinkedHashMap<>();
        for(Map<String,Object> row:rows==null?List.<Map<String,Object>>of():rows)
        {
            String rowBusiness=text(value(row,"businessType","business_type"));
            String status=text(value(row,"status","status"));
            if(!businessType.equals(rowBusiness)||!"PUBLISHED".equals(status))continue;
            RoutingTargetCatalogEntry target=new RoutingTargetCatalogEntry(
                    longValue(value(row,"templateId","template_id")),text(value(row,"templateCode","template_code")),
                    text(value(row,"templateName","template_name")),rowBusiness,
                    longValue(value(row,"versionId","version_id")),integer(value(row,"versionNo","version_no")),status);
            if(target.templateCode()!=null)result.putIfAbsent(target.templateCode(),target);
        }
        return result;
    }

    private BusinessOutcomeOption option(GovernedOutcome option,Map<String,RoutingTargetCatalogEntry> targetsByCode)
    {
        RoutingTargetCatalogEntry target=option.targetTemplateCode()==null?null:
                targetsByCode.get(option.targetTemplateCode());
        return new BusinessOutcomeOption(option.value(),option.label(),option.effectKind(),option.targetTemplateCode(),
                target==null?null:target.templateName(),target==null?null:target.versionId());
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
    {return !field.options().isEmpty()||field.dictType()!=null&&!field.dictType().isBlank();}

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

    private String effectKind(Map<String,Object> outcome)
    {
        String effect=text(outcome.get("effectKind"));
        if(effect!=null&&!effect.isBlank())return effect;
        String legacy=text(outcome.get("resultType"));
        if(legacy==null)return null;
        return switch(legacy)
        {
            case "NEXT" -> "NEXT_TEMPLATE";
            case "END" -> "END";
            default -> null;
        };
    }

    private static GovernedOutcome outcome(String value,String label,String effectKind,String targetTemplateCode)
    {return new GovernedOutcome(value,label,effectKind,targetTemplateCode);}

    private List<String> strings(Object value)
    {
        if(!(value instanceof List<?> list))return List.of();
        return list.stream().filter(item->item!=null&&!String.valueOf(item).isBlank()).map(String::valueOf).toList();
    }

    private Object value(Map<String,Object> row,String camel,String snake)
    {return row.containsKey(camel)?row.get(camel):row.get(snake);}
    private String firstText(Map<String,Object> row,String first,String second)
    {String value=text(row.get(first));return value==null||value.isBlank()?text(row.get(second)):value;}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private Long longValue(Object value)
    {
        if(value instanceof Number number)return number.longValue();
        try{return value==null?null:Long.valueOf(String.valueOf(value));}
        catch(NumberFormatException invalid){return null;}
    }
    private Integer integer(Object value)
    {
        if(value instanceof Number number)return number.intValue();
        try{return value==null?null:Integer.valueOf(String.valueOf(value));}
        catch(NumberFormatException invalid){return null;}
    }

    private record GovernedOutcome(String value,String label,String effectKind,String targetTemplateCode) { }
    private record GovernedOutcomeSpec(String resultField,String resultFieldName,String description,
            String recommendationCode,List<GovernedOutcome> options) { }
    public record OutcomeIssue(String code,String path,String message) { }
    public record BusinessOutcomeOption(String value,String label,String effectKind,String targetTemplateCode,
            String targetTemplateName,Long targetVersionId)
    {
        public BusinessOutcomeOption
        {label=label==null||label.isBlank()?value:label;}
        public BusinessOutcomeOption(String value,String label,String targetTemplateCode,
                String targetTemplateName,Long targetVersionId)
        {this(value,label,targetTemplateCode==null?"END":"NEXT_TEMPLATE",targetTemplateCode,targetTemplateName,targetVersionId);}
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

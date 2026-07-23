package com.law.todo.application;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.law.todo.application.view.TodoConfigurationJourneyView.EmployeeTodoPreview;
import com.law.todo.application.view.TodoConfigurationJourneyView.PreviewField;
import com.law.todo.application.view.TodoConfigurationJourneyView.PreviewMaterial;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.definition.model.TodoDefinitionDocument;

/** Employee-facing projection from the same editable definition snapshot. */
@Component
public class TodoEmployeeTodoPreviewProjector
{
    private final TodoConfigurationResourceCatalogService resources;
    public TodoEmployeeTodoPreviewProjector(){this(null);}
    @Autowired public TodoEmployeeTodoPreviewProjector(TodoConfigurationResourceCatalogService resources){this.resources=resources;}

    public EmployeeTodoPreview project(TemplateConfigurationDetail detail,TodoDefinitionDocument definition)
    {
        Map<String,Object> ui=config(definition==null?null:definition.ui());Map<String,Object> dod=config(definition==null?null:definition.dod());
        List<String> requiredFields=strings(dod.get("requiredFields"));List<String> previewFields=new ArrayList<>(requiredFields);
        for(Map<String,Object> conditional:conditionalRules(dod))
        {
            String field=text(conditional.get("field"));
            if(field!=null&&!field.isBlank()&&!previewFields.contains(field))previewFields.add(field);
        }
        List<PreviewField> fields=resources==null?List.of():resources.fields(detail.businessType()).stream()
                .filter(field->previewFields.contains(field.code()))
                .map(field->new PreviewField(field.code(),displayLabel(field.name(),"Required field"),field.type(),
                        requiredFields.contains(field.code()))).toList();
        Map<String,Map<String,Object>> materialRulesByType=new java.util.LinkedHashMap<>();
        for(Map<String,Object> material:materialRules(dod))
        {
            String type=text(material.containsKey("type")?material.get("type"):material.get("code"));
            if(type!=null&&!type.isBlank())materialRulesByType.put(type,material);
        }
        List<PreviewMaterial> materials=resources==null?List.of():resources.materials(detail.businessType()).stream()
                .filter(material->materialRulesByType.containsKey(material.code()))
                .map(material->{
                    String configuredLabel=text(materialRulesByType.get(material.code()).get("label"));
                    return new PreviewMaterial(material.code(),displayLabel(configuredLabel,
                            displayLabel(material.name(),"Required material")),true);
                }).toList();
        return new EmployeeTodoPreview(display(ui,"employeeTitle",detail.templateName()),describeOwner(config(definition==null?null:definition.owner())),
                fields,materials,strings(dod.get("employeeInstructions")),describeSla(config(definition==null?null:definition.sla())));
    }

    private List<String> strings(Object value)
    {
        if(value instanceof Collection<?> collection)return collection.stream().filter(item->item!=null&&!String.valueOf(item).isBlank()).map(String::valueOf).toList();
        if(value==null||!value.getClass().isArray())return List.of();List<String> result=new ArrayList<>();for(int index=0;index<Array.getLength(value);index++){Object item=Array.get(value,index);if(item!=null&&!String.valueOf(item).isBlank())result.add(String.valueOf(item));}return List.copyOf(result);
    }
    private List<Map<String,Object>> materialRules(Map<String,Object> dod)
    {
        if(dod.containsKey("materials"))return objects(dod.get("materials"));
        return strings(dod.get("requiredAttachments")).stream()
                .map(type->Map.<String,Object>of("type",type)).toList();
    }
    private List<Map<String,Object>> conditionalRules(Map<String,Object> dod)
    {return objects(dod.containsKey("conditionalRequired")?dod.get("conditionalRequired"):dod.get("conditionalRules"));}
    private List<Map<String,Object>> objects(Object value)
    {
        if(!(value instanceof Collection<?> collection))return List.of();List<Map<String,Object>> result=new ArrayList<>();
        for(Object item:collection)if(item instanceof Map<?,?> source)
        {
            Map<String,Object> row=new java.util.LinkedHashMap<>();source.forEach((key,entry)->row.put(String.valueOf(key),entry));result.add(row);
        }
        return List.copyOf(result);
    }
    private String display(Map<String,Object> value,String key,String fallback)
    {return safeDisplay(text(value.get(key)),fallback);}
    private String describeOwner(Map<String,Object> owner)
    {for(String key:List.of("displayName","label","summary","description")){String value=text(owner.get(key));if(isBusinessSafe(value))return value;}return "按已配置的负责人规则分配";}
    private String describeSla(Map<String,Object> sla)
    {for(String key:List.of("displayName","label","summary","description")){String value=text(sla.get(key));if(isBusinessSafe(value))return value;}return sla.isEmpty()?null:"Due according to the configured service-level agreement";}
    private String displayLabel(String label,String fallback){return safeDisplay(label,fallback);}
    private String safeDisplay(String value,String fallback){return isBusinessSafe(value)?value:fallback;}
    private boolean isBusinessSafe(String value)
    {
        if(value==null||value.isBlank())return false;String trimmed=value.trim();
        return !(trimmed.startsWith("{")||trimmed.startsWith("[")||trimmed.matches("[A-Z][A-Z0-9_]*(?:\\.[A-Z0-9_]+)*"));
    }
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private Map<String,Object> config(Object section)
    {if(section instanceof TodoDefinitionDocument.OwnerRule owner)return owner.config();if(section instanceof TodoDefinitionDocument.DodRule dod)return dod.config();if(section instanceof TodoDefinitionDocument.SlaRule sla)return sla.config();if(section instanceof TodoDefinitionDocument.UiSchema ui)return ui.config();return Map.of();}
}

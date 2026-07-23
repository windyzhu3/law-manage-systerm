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
        List<PreviewField> fields=resources==null?List.of():resources.fields(detail.businessType()).stream()
                .filter(field->strings(dod.get("requiredFields")).contains(field.code()))
                .map(field->new PreviewField(field.code(),displayLabel(field.name(),"Required field"),field.type(),true)).toList();
        List<PreviewMaterial> materials=resources==null?List.of():resources.materials(detail.businessType()).stream()
                .filter(material->strings(dod.get("requiredAttachments")).contains(material.code()))
                .map(material->new PreviewMaterial(material.code(),displayLabel(material.name(),"Required material"),true)).toList();
        return new EmployeeTodoPreview(display(ui,"employeeTitle",detail.templateName()),describeOwner(config(definition==null?null:definition.owner())),
                fields,materials,strings(dod.get("employeeInstructions")),describeSla(config(definition==null?null:definition.sla())));
    }

    private List<String> strings(Object value)
    {
        if(value instanceof Collection<?> collection)return collection.stream().filter(item->item!=null&&!String.valueOf(item).isBlank()).map(String::valueOf).toList();
        if(value==null||!value.getClass().isArray())return List.of();List<String> result=new ArrayList<>();for(int index=0;index<Array.getLength(value);index++){Object item=Array.get(value,index);if(item!=null&&!String.valueOf(item).isBlank())result.add(String.valueOf(item));}return List.copyOf(result);
    }
    private String display(Map<String,Object> value,String key,String fallback)
    {String result=text(value.get(key));return result==null||result.isBlank()?fallback:result;}
    private String describeOwner(Map<String,Object> owner)
    {for(String key:List.of("displayName","label","summary","description")){String value=text(owner.get(key));if(value!=null&&!value.isBlank())return value;}return "Assigned according to the configured ownership rule";}
    private String describeSla(Map<String,Object> sla)
    {for(String key:List.of("displayName","label","summary","description")){String value=text(sla.get(key));if(value!=null&&!value.isBlank())return value;}return sla.isEmpty()?null:"Due according to the configured service-level agreement";}
    private String displayLabel(String label,String fallback){return label==null||label.isBlank()?fallback:label;}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private Map<String,Object> config(Object section)
    {if(section instanceof TodoDefinitionDocument.OwnerRule owner)return owner.config();if(section instanceof TodoDefinitionDocument.DodRule dod)return dod.config();if(section instanceof TodoDefinitionDocument.SlaRule sla)return sla.config();if(section instanceof TodoDefinitionDocument.UiSchema ui)return ui.config();return Map.of();}
}

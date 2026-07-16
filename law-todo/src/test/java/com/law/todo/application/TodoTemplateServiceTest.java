package com.law.todo.application;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.TodoException;

@ExtendWith(MockitoExtension.class)
class TodoTemplateServiceTest
{
    @Mock TodoMapper mapper;
    @Test void createsTemplate(){when(mapper.insertTemplate(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTemplate(new java.util.HashMap<>(Map.of("templateCode","T1","templateName","测试","businessType","LEAD")));verify(mapper).insertTemplate(anyMap());}
    @Test void savesTriggerRule(){when(mapper.insertTriggerRule(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTrigger(new java.util.HashMap<>(Map.of("eventType","LEAD_ASSIGNED","templateId",1L,"templateVersionId",2L,"businessType","LEAD")));verify(mapper).insertTriggerRule(anyMap());}
    @Test void rejectsInvalidTemplateJson(){TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).publish(1L,1,"OWNER","{}","{}",null,"{}","admin"));assertEquals("TODO_TEMPLATE_JSON_INVALID",error.getBusinessCode());}
    @Test void publishesUiSchemaInImmutableVersion(){when(mapper.selectTemplateVersion(1L,1)).thenReturn(null);doAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",8L);return 1;}).when(mapper).insertTemplateVersion(anyMap());new TodoTemplateService(mapper).publish(1L,1,"\"OWNER\"","{}","{}",null,"{\"type\":\"form\"}","admin");verify(mapper).insertTemplateVersion(org.mockito.ArgumentMatchers.argThat(value->"{\"type\":\"form\"}".equals(value.get("uiSchemaJson"))));}

    @Test void springConstructorExplicitlyInjectsConditionValidationDependencies() throws Exception
    {
        assertTrue(TodoTemplateService.class.getConstructor(TodoMapper.class,
                TodoEventCatalogService.class,ConditionValidator.class)
                .isAnnotationPresent(Autowired.class));
    }

    @Test void mapTriggerSaveRejectsUndeclaredConditionField()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        Map<String,Object> update=trigger("{\"unknown\":\"READY\"}");
        update.put("triggerRuleId",4L);
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(update));
        assertEquals("TODO_CONDITION_FIELD_UNKNOWN",error.getBusinessCode());
        verify(mapper,never()).updateTriggerRule(anyMap());
    }

    @Test void triggerSaveRejectsInvalidTypedPredicate()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        String condition="{\"$expression\":{\"version\":1,\"root\":{\"field\":\"amount\",\"operator\":\"GT\",\"value\":true}}}";
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(new TriggerCommand(null,
                        "LEAD_ASSIGNED",1L,2L,"LEAD","Y",condition)));
        assertEquals("TODO_CONDITION_VALUE_TYPE_INVALID",error.getBusinessCode());
    }

    @Test void triggerSaveRejectsMalformedExpressionEnvelope()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        String condition="{\"$expression\":{\"version\":1,\"root\":{\"field\":\"amount\",\"operator\":\"EQ\",\"value\":1},\"extra\":true}}";
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(trigger(condition)));
        assertEquals("TODO_CONDITION_INVALID",error.getBusinessCode());
    }

    @Test void triggerSaveAcceptsLegacyFlatMapWhenSchemaDeclaresFields()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        when(mapper.insertTriggerRule(anyMap())).thenReturn(1);
        assertEquals(1,new TodoTemplateService(mapper).saveTrigger(trigger("{\"stage\":\"READY\"}")));
        verify(mapper).insertTriggerRule(anyMap());
    }

    @Test void triggerManagementRequiresAnActiveCatalogForConditions()
    {
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(trigger("{\"stage\":\"READY\"}")));
        assertEquals("TODO_CONDITION_SCHEMA_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertTriggerRule(anyMap());
    }

    private Map<String,Object> trigger(String conditionJson)
    {
        Map<String,Object> trigger=new HashMap<>(Map.of("eventType","LEAD_ASSIGNED","templateId",1L,
                "templateVersionId",2L,"businessType","LEAD"));
        trigger.put("conditionJson",conditionJson);
        return trigger;
    }

    private Map<String,Object> catalog()
    {
        return Map.of("status","ACTIVE","payload_schema_json","{\"type\":\"object\",\"properties\":{\"stage\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"}}}");
    }
}

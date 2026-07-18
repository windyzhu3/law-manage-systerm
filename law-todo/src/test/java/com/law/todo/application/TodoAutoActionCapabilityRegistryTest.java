package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;

class TodoAutoActionCapabilityRegistryTest
{
    @Test void descriptorNormalizesMissingRetrySchemaForCustomCapabilities()
    {
        TodoAutoActionCapability.Descriptor descriptor=new TodoAutoActionCapability.Descriptor("CUSTOM_NOTIFY",List.of("DUE"),List.of(),List.of());

        assertEquals(List.of("maxAttempts","retryDelayMinutes","claimTimeoutMinutes"),descriptor.retryFields().stream().map(TodoAutoActionCapability.Field::name).toList());
        assertEquals(3,descriptor.retryField("maxAttempts").defaultValue());
        assertEquals(15,descriptor.retryField("claimTimeoutMinutes").defaultValue());
    }

    @Test void registeredCustomCapabilityDrivesCatalogCompilerAndRuntime()
    {
        TodoAutoActionCapability custom=mock(TodoAutoActionCapability.class);
        TodoAutoActionCapability.Descriptor descriptor=new TodoAutoActionCapability.Descriptor("CUSTOM_NOTIFY",List.of("DUE"),
                TodoAutoActionCapability.Descriptor.commonRetryFields(),
                List.of(new TodoAutoActionCapability.Field("channel","text",true,null,null,"Channel","TODO_AUTO_ACTION_FIELD_INVALID")));
        when(custom.actionType()).thenReturn("CUSTOM_NOTIFY");when(custom.descriptor()).thenReturn(descriptor);
        TodoAutoActionCapabilityRegistry registry=new TodoAutoActionCapabilityRegistry(List.of(custom));

        assertEquals(List.of("CUSTOM_NOTIFY"),new TodoAutoActionCapabilityCatalogService(registry).list().stream().map(TodoAutoActionCapabilityCatalogService.CapabilityView::actionType).toList());

        TodoMapper compilerMapper=mock(TodoMapper.class);
        when(compilerMapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of("event_type","LEAD_CREATED","payload_version",1,"payload_schema_json","{\"type\":\"object\"}","status","ACTIVE"));
        TodoDefinitionCompiler compiler=new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(compilerMapper),new TodoDecisionService(compilerMapper),new com.law.todo.expression.ConditionValidator(),registry);
        assertTrue(compiler.compile(definition()).errors().stream().noneMatch(error->error.code().startsWith("TODO_AUTO_ACTION")));

        TodoMapper runtimeMapper=mock(TodoMapper.class);
        when(runtimeMapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(1);
        when(runtimeMapper.selectAutoActionExecution("AUTO:1:custom")).thenReturn(Map.of("execution_key","AUTO:1:custom","todo_id",1L,"rule_key","custom","action_type","CUSTOM_NOTIFY","status","CLAIMED","attempt_count",1));
        when(custom.execute(any(),any(),any())).thenReturn(TodoAutoActionCapability.AutoActionResult.success());
        when(runtimeMapper.completeAutoActionExecution(anyMap())).thenReturn(1);when(runtimeMapper.insertAutoActionAudit(anyMap())).thenReturn(1);
        TodoInstance todo=new TodoInstance();todo.setTodoId(1L);todo.setStatus("SUBMITTED");
        assertEquals(TodoAutoActionCapability.AutoActionStatus.SUCCESS,new TodoAutoActionService(runtimeMapper,registry,new TodoAutoActionResultRecorder(runtimeMapper)).execute(rule(),todo,LocalDateTime.of(2026,7,18,10,0)).status());
        verify(custom).execute(any(),any(),any());
    }

    private TodoDefinitionDocument definition()
    {
        return new TodoDefinitionDocument(1,"TD",new EventRule("LEAD_CREATED",1,Map.of()),new OwnerRule(Map.of()),new DodRule(Map.of()),new SlaRule(Map.of()),new UiSchema(Map.of()),new RoutingGraph(Map.of()),List.of(rule()),List.of(),List.of());
    }
    private AutoActionRule rule(){return new AutoActionRule(Map.of("ruleKey","custom","actionType","CUSTOM_NOTIFY","capability","CUSTOM_NOTIFY","triggerAt","DUE","channel","EMAIL"));}
}

package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoResourceCommands.EventResourceCommand;
import com.law.todo.application.command.TodoResourceCommands.EventResourceStatusCommand;
import com.law.todo.application.view.TodoResourceViews.EventResourceDetail;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoEventResourceServiceTest
{
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoMapper todoMapper;
    private TodoEventResourceService service;
    private final Actor actor=new Actor(7L,"alice",2L);

    @BeforeEach void setUp(){service=new TodoEventResourceService(mapper,todoMapper);}

    @Test void rejectsSchemaWithoutSelectableProperties()
    {
        TodoException error=assertThrows(TodoException.class,()->service.save(command(null,"{}","{}","DRAFT",0),actor));

        assertEquals("TODO_EVENT_RESOURCE_SCHEMA_INVALID",error.getBusinessCode());
        verify(mapper,never()).insertEventResource(anyMap());
    }

    @Test void rejectsSampleThatDoesNotContainRequiredField()
    {
        String schema="{\"type\":\"object\",\"properties\":{\"ownerId\":{\"type\":\"integer\",\"title\":\"负责人\"}},\"required\":[\"ownerId\"]}";
        TodoException error=assertThrows(TodoException.class,()->service.save(command(null,schema,"{}","DRAFT",0),actor));

        assertEquals("TODO_EVENT_RESOURCE_SAMPLE_INVALID",error.getBusinessCode());
    }

    @Test void activeVersionCannotBeEditedInPlace()
    {
        when(mapper.selectEventResource(9L)).thenReturn(row(9L,"ACTIVE",3,1));

        TodoException error=assertThrows(TodoException.class,()->service.save(command(9L,schema(),sample(),"ACTIVE",3),actor));

        assertEquals("TODO_EVENT_RESOURCE_ACTIVE_IMMUTABLE",error.getBusinessCode());
        verify(mapper,never()).updateEventResourceConditionally(anyMap());
    }

    @Test void savingCannotPublishAResourceImplicitly()
    {
        TodoException error=assertThrows(TodoException.class,
                ()->service.save(command(null,schema(),sample(),"ACTIVE",0),actor));

        assertEquals("TODO_EVENT_RESOURCE_STATUS_INVALID",error.getBusinessCode());
        verify(mapper,never()).insertEventResource(anyMap());
    }

    @Test void createsNextDraftVersionFromActiveResource()
    {
        claim("next-event",true);when(mapper.selectEventResource(9L)).thenReturn(row(9L,"ACTIVE",3,1));
        when(mapper.selectNextEventPayloadVersion("LEAD_ASSIGNED")).thenReturn(2);
        when(mapper.insertEventResource(anyMap())).thenAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("eventCatalogId",10L);return 1;});

        assertEquals(10L,service.createNextVersion(9L,"next-event",actor));

        verify(mapper).insertEventResource(argThat(value->"LEAD_ASSIGNED".equals(value.get("eventType"))
                &&Integer.valueOf(2).equals(value.get("payloadVersion"))&&"DRAFT".equals(value.get("status"))));
    }

    @Test void staleDraftUpdateReturnsStableConflict()
    {
        claim("save-event",true);when(mapper.selectEventResource(9L)).thenReturn(row(9L,"DRAFT",3,1));
        when(mapper.updateEventResourceConditionally(anyMap())).thenReturn(0);

        TodoException error=assertThrows(TodoException.class,()->service.save(command(9L,schema(),sample(),"DRAFT",3),actor));

        assertEquals("TODO_EVENT_RESOURCE_VERSION_CONFLICT",error.getBusinessCode());
    }

    @Test void activationRequiresReadySchemaAndUsesOptimisticLock()
    {
        claim("status-event",true);Map<String,Object> draft=row(9L,"DRAFT",3,1);draft.put("schema_status","READY");
        when(mapper.selectEventResource(9L)).thenReturn(draft);when(mapper.updateEventResourceStatusConditionally(anyMap())).thenReturn(1);

        service.changeStatus(9L,new EventResourceStatusCommand("ACTIVE","status-event",3),actor);

        verify(mapper).updateEventResourceStatusConditionally(argThat(value->"ACTIVE".equals(value.get("status"))
                &&Integer.valueOf(3).equals(value.get("expectedVersion"))));
    }

    @Test void detailIncludesReferencesAndTypedMetadata()
    {
        when(mapper.selectEventResource(9L)).thenReturn(row(9L,"ACTIVE",3,1));
        when(mapper.selectEventResourceReferences("LEAD_ASSIGNED",1)).thenReturn(List.of(Map.of(
                "reference_type","TRIGGER_RULE","reference_id",5L,"reference_code","TR-5",
                "reference_name","首联触发","reference_status","0")));

        EventResourceDetail detail=service.detail(9L);

        assertEquals("线索已分配",detail.eventName());
        assertEquals("TR-5",detail.references().get(0).referenceCode());
    }

    private EventResourceCommand command(Long id,String schema,String sample,String status,int version)
    {return new EventResourceCommand(id,"LEAD_ASSIGNED",1,"线索已分配","线索分配后触发","LEAD","lead",schema,sample,status,"save-event",version);}
    private String schema(){return "{\"type\":\"object\",\"properties\":{\"ownerId\":{\"type\":\"integer\",\"title\":\"负责人\"}},\"required\":[\"ownerId\"]}";}
    private String sample(){return "{\"ownerId\":11}";}
    private Map<String,Object> row(long id,String status,int version,int payloadVersion)
    {
        Map<String,Object> row=new HashMap<>();row.put("event_catalog_id",id);row.put("event_type","LEAD_ASSIGNED");
        row.put("event_name","线索已分配");row.put("description","线索分配后触发");row.put("payload_version",payloadVersion);
        row.put("business_object_type","LEAD");row.put("source_module","lead");row.put("payload_schema_json",schema());
        row.put("sample_payload_json",sample());row.put("schema_status","READY");row.put("status",status);row.put("version",version);
        row.put("producer","law-business");return row;
    }
    private void claim(String actionId,boolean inserted)
    {
        when(todoMapper.insertDefinitionActionClaim(anyMap())).thenReturn(inserted?1:0);
        when(todoMapper.selectDefinitionActionForUpdate(actionId)).thenAnswer(invocation->{Map<String,Object> row=new HashMap<>();
            row.put("action_type",actionId.startsWith("next")?"CREATE_EVENT_RESOURCE_VERSION":actionId.startsWith("status")?"CHANGE_EVENT_RESOURCE_STATUS":"SAVE_EVENT_RESOURCE");
            row.put("entity_type","EVENT_RESOURCE");row.put("operator_id",7L);row.put("operator_name","alice");row.put("operator_dept_id",2L);
            Map<String,Object> claim=org.mockito.Mockito.mockingDetails(todoMapper).getInvocations().stream()
                    .filter(call->call.getMethod().getName().equals("insertDefinitionActionClaim")).reduce((first,last)->last)
                    .map(call->(Map<String,Object>)call.getArgument(0)).orElse(Map.of());
            row.put("source_entity_id",claim.get("sourceEntityId"));row.put("request_fingerprint",claim.get("requestFingerprint"));
            row.put("action_status","CLAIMED");return row;});
        lenient().when(todoMapper.completeDefinitionAction(org.mockito.ArgumentMatchers.eq(actionId),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
    }
}

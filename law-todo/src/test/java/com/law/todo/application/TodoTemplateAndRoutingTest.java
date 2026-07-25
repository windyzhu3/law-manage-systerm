package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessValidator;
import com.law.todo.routing.RouteToken;
import com.law.todo.routing.RouteTokenStatus;
import com.law.todo.routing.TodoRoutingEngine.NextTask;
import com.law.todo.routing.TodoRoutingEngine;
import com.law.todo.routing.TodoRoutingEngine.RouteStatus;
import com.law.todo.routing.TodoRoutingEngine.RoutingResult;

@ExtendWith(MockitoExtension.class)
class TodoTemplateAndRoutingTest
{
    @Mock TodoMapper mapper;

    @Test void dodRejectsMissingRequiredField()
    {
        TodoDodService service=new TodoDodService(List.of());
        TodoException error=assertThrows(TodoException.class,()->service.validate(todo(),List.of("contactResult"),List.of(),Map.of(),List.of()));
        assertEquals("TODO_DOD_FIELD_MISSING",error.getBusinessCode());
    }

    @Test void dodInvokesBusinessValidator()
    {
        TodoBusinessValidator validator=(todo,payload)->{throw new TodoException("LEAD_INVALID","线索状态不允许首联");};
        TodoDodService service=new TodoDodService(List.of(validator));
        assertThrows(TodoException.class,()->service.validate(todo(),List.of(),List.of(),Map.of(),List.of()));
    }

    @Test void routingReturnsExistingNextTodo()
    {
        TodoInstance existing=todo();existing.setTodoId(9L);
        when(mapper.selectByNextKey("1:22")).thenReturn(existing);
        TodoInstance result=new TodoRoutingService(mapper).createNext(todo(),22L,"Next","LEAD",7L);
        assertSame(existing,result);verify(mapper,never()).insertInstance(any());
    }

    @Test void routingCreatesNextFromItsOwnImmutableTemplateVersion()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setOwnerDeptId(3L);previous.setBusinessNo("L-7");
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of("template_id",5L,"template_name","后续联系","owner_rule_json","OWNER"));

        TodoInstance next=new TodoRoutingService(mapper).createNext(previous,22L,null,"LEAD",7L);

        assertEquals(5L,next.getTemplateId());assertEquals(8L,next.getOwnerId());assertEquals(3L,next.getOwnerDeptId());assertEquals(1L,next.getPreviousTodoId());
        verify(mapper).insertInstance(next);verify(mapper).insertRelation(anyMap());verify(mapper).insertCandidate(anyMap());
    }

    @Test void graphRoutingSnapshotsDefinitionAndUsesExactOccurrenceIdentity()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setBusinessNo("L-7");previous.setRouteDefinitionVersionId(9L);
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"template_code","FOLLOW_UP","template_name","Follow up",
                "owner_rule_json","OWNER","dod_rule_json","{}","ui_schema_json","{\"type\":\"form\"}",
                "sla_rule_json","{}","status","PUBLISHED"));
        RouteToken token=new RouteToken(1L,"review","legal",3,RouteTokenStatus.ACTIVE);

        TodoInstance next=new TodoRoutingService(mapper).createNext(previous,
                new NextTask("review",22L,token),"abc123",1);

        assertEquals("1:review:LEAD:7:3",next.getNextIdempotencyKey());
        assertEquals("1:review:LEAD:7:3",next.getOccurrenceKey());
        assertEquals("abc123",next.getDefinitionHash());
        assertEquals(9L,next.getRouteDefinitionVersionId());
        assertEquals("{\"type\":\"form\"}",next.getUiSchemaSnapshot());
        assertEquals("{}",next.getSlaSnapshot());
        assertEquals("review",next.getRouteNodeKey());
        assertEquals(1,next.getPayloadSchemaVersion());
        assertTrue(next.getRouteToken().contains("\"branchKey\":\"legal\""));
    }

    @Test void downstreamTemplateContinuesGraphOwnedByOriginalDefinition()
    {
        TodoInstance previous=todo();previous.setTemplateVersionId(22L);previous.setRouteDefinitionVersionId(9L);previous.setDefinitionHash("hash");previous.setRouteNodeKey("review");
        previous.setRouteToken("{\"rootTodoId\":1,\"nodeKey\":\"review\",\"occurrence\":0,\"status\":\"ACTIVE\"}");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of("compiled_json","""
                {"schemaVersion":1,"templateCode":"T","routing":{"config":{"start":"review","nodes":[
                  {"key":"review","type":"TASK","templateVersionId":9},{"key":"end","type":"END"}],
                  "edges":[{"key":"done","from":"review","to":"end","priority":0}]}},
                  "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                ""","definition_hash","hash"));
        TodoRoutingEngine engine=mock(TodoRoutingEngine.class);
        when(engine.advance(org.mockito.ArgumentMatchers.any())).thenReturn(new RoutingResult(RouteStatus.ENDED,List.of()));

        new TodoRoutingService(mapper,new TodoAssignmentResolver(),engine).advance(previous,Map.of("approved",true));

        verify(engine).advance(org.mockito.ArgumentMatchers.argThat(context ->
                "hash".equals(context.definitionHash()) && "review".equals(context.token().nodeKey())));
        verify(mapper,never()).selectByNextKey("1:9");
        verify(mapper).selectTemplateVersionById(9L);
        verify(mapper,never()).selectTemplateVersionById(22L);
    }

    @Test void tamperedRouteDefinitionHashIsRejected()
    {
        TodoInstance previous=todo();previous.setTemplateVersionId(22L);previous.setRouteDefinitionVersionId(9L);previous.setDefinitionHash("tampered");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of("definition_hash","actual","compiled_json","{}"));

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper).advance(previous,Map.of()));

        assertEquals("TODO_ROUTE_DEFINITION_HASH_MISMATCH",error.getBusinessCode());
    }

    @Test void graphTaskCannotCreateFromDraftTemplateVersion()
    {
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of("status","DRAFT","template_id",5L));
        RouteToken token=new RouteToken(1L,"review",null,0,RouteTokenStatus.ACTIVE);

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper).createNext(todo(),
                new NextTask("review",22L,token),"hash",1));

        assertEquals("TODO_ROUTE_TASK_VERSION_NOT_PUBLISHED",error.getBusinessCode());
        verify(mapper,never()).insertInstance(any());
    }

    @Test void scheduledRoutingUsesOccurrenceBoundaryAndPersistedWindowDueAt()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setOwnerDeptId(3L);previous.setBusinessNo("L-7");
        LocalDateTime dueAt=LocalDateTime.of(2026,7,26,11,0);
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"template_code","TD-003","template_name","Retry contact",
                "business_type","LEAD","owner_rule_json","OWNER","sla_rule_json","{\"calendarCode\":\"DEFAULT\"}",
                "status","PUBLISHED"));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "calendar_id",1L,"work_days","1,2,3,4,5,6,7","work_start","00:00:00",
                "work_end","23:59:59","exception_json","{}"));

        TodoInstance next=new TodoRoutingService(mapper).createScheduledNext(previous,22L,"3:T1_AM:1",dueAt);

        assertEquals("SCHEDULE:3:T1_AM:1",next.getNextIdempotencyKey());
        assertEquals("3:T1_AM:1",next.getOccurrenceKey());
        assertEquals(dueAt,next.getDueAt());
        verify(mapper).insertInstance(next);verify(mapper).insertRelation(anyMap());
        verify(mapper).insertSlaRecord(org.mockito.ArgumentMatchers.argThat(row->dueAt.equals(row.get("dueAt"))));
    }

    @Test void scheduledRoutingRejectsBlankOccurrenceAndUnpublishedTemplate()
    {
        TodoRoutingService service=new TodoRoutingService(mapper);
        TodoException blank=assertThrows(TodoException.class,
                ()->service.createScheduledNext(todo(),22L," ",LocalDateTime.now()));
        assertEquals("TODO_SCHEDULE_OCCURRENCE_KEY_REQUIRED",blank.getBusinessCode());
        verify(mapper,never()).selectTemplateVersionById(any());

        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"business_type","LEAD","status","DRAFT"));
        TodoException draft=assertThrows(TodoException.class,
                ()->service.createScheduledNext(todo(),22L,"3:T1_AM:1",LocalDateTime.now()));
        assertEquals("TODO_SCHEDULE_TEMPLATE_NOT_PUBLISHED",draft.getBusinessCode());
    }

    @Test void scheduledRoutingResolvesThePublishedCanonicalOwnerDefinition()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setOwnerDeptId(3L);
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"template_code","TD-003","template_name","Retry contact",
                "business_type","LEAD","status","PUBLISHED","sla_rule_json","{\"calendarCode\":\"DEFAULT\"}",
                "compiled_json","""
                  {"schemaVersion":1,"templateCode":"TD-003",
                   "owner":{"config":{"type":"USER","operand":9}},
                   "routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                  """));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "calendar_id",1L,"work_days","1,2,3,4,5,6,7","work_start","00:00:00",
                "work_end","23:59:59","exception_json","{}"));
        when(mapper.selectUserDeptId(9L)).thenReturn(4L);

        TodoInstance next=new TodoRoutingService(mapper).createScheduledNext(previous,22L,
                "3:T1_AM:1",LocalDateTime.of(2026,7,26,11,0));

        assertEquals(9L,next.getOwnerId());
        assertEquals(4L,next.getOwnerDeptId());
    }

    private TodoInstance todo(){TodoInstance t=new TodoInstance();t.setTodoId(1L);t.setBusinessType("LEAD");t.setBusinessId(7L);t.setRootTodoId(1L);return t;}
}

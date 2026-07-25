package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.TodoException;
import com.law.todo.assignment.OwnerResolutionResult;
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

    @BeforeEach void generatedTodoIdentity()
    {
        lenient().when(mapper.insertInstance(any())).thenAnswer(invocation->{
            TodoInstance value=invocation.getArgument(0);
            if(value.getTodoId()==null)value.setTodoId(77L);
            return 1;
        });
        lenient().when(mapper.updateInitialRouteSnapshot(any(),any(),any(),any())).thenReturn(1);
    }

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

    @Test void graphRoutingResolvesPublishedCanonicalOwnerFromAuthoritativePayload()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setRouteDefinitionVersionId(9L);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of(
                "definition_hash","root-hash","compiled_json","""
                {"schemaVersion":1,"templateCode":"TD-001",
                 "routing":{"config":{"start":"td001","nodes":[],"edges":[]}},
                 "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """));
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"template_code","TD-002","template_name","Invalid review",
                "business_type","LEAD","status","PUBLISHED",
                "compiled_json","""
                {"schemaVersion":1,"templateCode":"TD-002",
                 "owner":{"config":{"type":"PAYLOAD","field":"reviewerId",
                    "skipUnavailable":false,"useDelegation":false,"requireAvailable":true}},
                 "routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """));
        TodoRoutingEngine engine=mock(TodoRoutingEngine.class);
        RouteToken token=new RouteToken(1L,"td002","suspect",0,RouteTokenStatus.ACTIVE);
        when(engine.advance(any())).thenReturn(new RoutingResult(RouteStatus.ADVANCED,
                List.of(new NextTask("td002",22L,token))));

        new TodoRoutingService(mapper,new TodoAssignmentResolver(),engine)
                .advance(previous,Map.of("reviewerId",91L,"ownerId",999L));

        verify(mapper).insertInstance(org.mockito.ArgumentMatchers.argThat(next->
                Long.valueOf(91L).equals(next.getOwnerId())
                &&"TD-002".equals(next.getTemplateCode())));
    }

    @Test void graphRoutingFailsClosedBeforeInsertWhenCanonicalOwnerIsUnresolved()
    {
        TodoInstance previous=todo();previous.setRouteDefinitionVersionId(9L);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of(
                "definition_hash","root-hash","compiled_json","""
                {"schemaVersion":1,"templateCode":"TD-001",
                 "routing":{"config":{"start":"td001","nodes":[],"edges":[]}},
                 "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """));
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"template_code","TD-002","template_name","Invalid review",
                "business_type","LEAD","status","PUBLISHED",
                "compiled_json","""
                {"schemaVersion":1,"templateCode":"TD-002",
                 "owner":{"config":{"type":"PAYLOAD","field":"reviewerId",
                    "skipUnavailable":false,"useDelegation":false,"requireAvailable":true}},
                 "routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """));
        TodoRoutingEngine engine=mock(TodoRoutingEngine.class);
        when(engine.advance(any())).thenReturn(new RoutingResult(RouteStatus.ADVANCED,
                List.of(new NextTask("td002",22L,
                        new RouteToken(1L,"td002","suspect",0,RouteTokenStatus.ACTIVE)))));

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(
                mapper,new TodoAssignmentResolver(),engine).advance(previous,Map.of()));

        assertEquals("TODO_OWNER_UNRESOLVED",error.getBusinessCode());
        verify(mapper,never()).insertInstance(any());
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
                "status","PUBLISHED","definition_json",scheduledDefinition(8L)));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "calendar_id",1L,"work_days","1,2,3,4,5,6,7","work_start","00:00:00",
                "work_end","23:59:59","exception_json","{}"));
        activeScheduleFence();
        when(mapper.linkScheduleOccurrenceByKey(org.mockito.ArgumentMatchers.eq("3:T1_AM:1"),
                any(),org.mockito.ArgumentMatchers.eq(0),any())).thenReturn(1);

        TodoInstance next=new TodoRoutingService(mapper).createScheduledNext(previous,22L,"3:T1_AM:1",dueAt);

        assertEquals("SCHEDULE:3:T1_AM:1",next.getNextIdempotencyKey());
        assertEquals("3:T1_AM:1",next.getOccurrenceKey());
        assertEquals(dueAt,next.getDueAt());
        assertEquals(77L,next.getRootTodoId());
        RouteToken token=JSON.parseObject(next.getRouteToken(),RouteToken.class);
        assertEquals(77L,token.rootTodoId());assertEquals("td003",token.nodeKey());
        assertEquals(0,token.occurrence());assertEquals(RouteTokenStatus.ACTIVE,token.status());
        verify(mapper).insertInstance(next);verify(mapper).insertRelation(anyMap());
        verify(mapper).updateInitialRouteSnapshot(77L,77L,next.getRouteToken(),"3:T1_AM:1");
        verify(mapper).insertScheduledSlaRecord(org.mockito.ArgumentMatchers.argThat(row->dueAt.equals(row.get("dueAt"))));
    }

    @Test void materializedScheduledReplayRejectsMissingLinkedTodoIdentity()
    {
        TodoInstance existing=scheduledExisting(88L);
        materializedScheduleFence(null);
        when(mapper.selectTemplateVersionById(22L)).thenReturn(scheduledVersion());
        when(mapper.selectByNextKey("SCHEDULE:3:T1_AM:1")).thenReturn(existing);

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper)
                .createScheduledNext(todo(),22L,"3:T1_AM:1",LocalDateTime.now().plusHours(1)));

        assertEquals("TODO_SCHEDULE_OCCURRENCE_LINK_INVALID",error.getBusinessCode());
        verifyMaterializedReplayHasNoWrites();
    }

    @Test void materializedScheduledReplayRejectsMismatchedLinkedTodoIdentity()
    {
        TodoInstance existing=scheduledExisting(88L);
        materializedScheduleFence(89L);
        when(mapper.selectTemplateVersionById(22L)).thenReturn(scheduledVersion());
        when(mapper.selectByNextKey("SCHEDULE:3:T1_AM:1")).thenReturn(existing);

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper)
                .createScheduledNext(todo(),22L,"3:T1_AM:1",LocalDateTime.now().plusHours(1)));

        assertEquals("TODO_SCHEDULE_OCCURRENCE_LINK_INVALID",error.getBusinessCode());
        verifyMaterializedReplayHasNoWrites();
    }

    @Test void materializedScheduledReplayRejectsMalformedRouteToken()
    {
        TodoInstance existing=scheduledExisting(88L);existing.setRouteToken("{malformed");
        materializedScheduleFence(88L);
        when(mapper.selectTemplateVersionById(22L)).thenReturn(scheduledVersion());
        when(mapper.selectByNextKey("SCHEDULE:3:T1_AM:1")).thenReturn(existing);

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper)
                .createScheduledNext(todo(),22L,"3:T1_AM:1",LocalDateTime.now().plusHours(1)));

        assertEquals("TODO_SCHEDULE_ROUTE_SNAPSHOT_INVALID",error.getBusinessCode());
        verifyMaterializedReplayHasNoWrites();
    }

    @Test void materializedScheduledReplayReturnsOnlyTheExactValidatedTodo()
    {
        TodoInstance existing=scheduledExisting(88L);
        materializedScheduleFence(88L);
        when(mapper.selectTemplateVersionById(22L)).thenReturn(scheduledVersion());
        when(mapper.selectByNextKey("SCHEDULE:3:T1_AM:1")).thenReturn(existing);

        TodoInstance replayed=new TodoRoutingService(mapper).createScheduledNext(
                todo(),22L,"3:T1_AM:1",LocalDateTime.now().plusHours(1));

        assertSame(existing,replayed);
        verifyMaterializedReplayHasNoWrites();
    }

    @Test void scheduledRoutingExplicitlyLocksPlanBeforeOccurrenceAndWindow()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setOwnerDeptId(3L);
        LocalDateTime dueAt=LocalDateTime.of(2026,7,26,11,0);
        when(mapper.selectScheduleOccurrenceIdentityByKey("3:T1_AM:1")).thenReturn(Map.of(
                "occurrenceId",9L,"planId",3L,"windowId",12L,"occurrenceKey","3:T1_AM:1"));
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of(
                "planId",3L,"status","ACTIVE","templateVersionId",22L));
        when(mapper.selectScheduleOccurrenceWindowForUpdate("3:T1_AM:1",3L)).thenReturn(Map.of(
                "occurrenceId",9L,"planId",3L,"windowId",12L,"occurrenceKey","3:T1_AM:1",
                "version",0,"status","CLAIMED","windowStatus","PROCESSING"));
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"template_code","TD-003","template_name","Retry contact",
                "business_type","LEAD","owner_rule_json","OWNER","status","PUBLISHED",
                "sla_rule_json","{\"calendarCode\":\"DEFAULT\"}",
                "definition_json",scheduledDefinition(8L)));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "calendar_id",1L,"work_days","1,2,3,4,5,6,7","work_start","00:00:00",
                "work_end","23:59:59","exception_json","{}"));
        when(mapper.linkScheduleOccurrenceByKey(org.mockito.ArgumentMatchers.eq("3:T1_AM:1"),
                any(),org.mockito.ArgumentMatchers.eq(0),any())).thenReturn(1);

        new TodoRoutingService(mapper).createScheduledNext(previous,22L,"3:T1_AM:1",dueAt);

        InOrder lockOrder=inOrder(mapper);
        lockOrder.verify(mapper).selectScheduleOccurrenceIdentityByKey("3:T1_AM:1");
        lockOrder.verify(mapper).selectSchedulePlanForUpdate(3L);
        lockOrder.verify(mapper).selectScheduleOccurrenceWindowForUpdate("3:T1_AM:1",3L);
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
        activeScheduleFence();
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
                    "routing":{"config":{"start":"td003","nodes":[
                      {"key":"td003","type":"TASK","templateCode":"TD-003","templateVersionId":22}
                    ]}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                  """));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "calendar_id",1L,"work_days","1,2,3,4,5,6,7","work_start","00:00:00",
                "work_end","23:59:59","exception_json","{}"));
        when(mapper.selectUserDeptId(9L)).thenReturn(4L);
        activeScheduleFence();
        when(mapper.linkScheduleOccurrenceByKey(org.mockito.ArgumentMatchers.eq("3:T1_AM:1"),
                any(),org.mockito.ArgumentMatchers.eq(0),any())).thenReturn(1);

        TodoInstance next=new TodoRoutingService(mapper).createScheduledNext(previous,22L,
                "3:T1_AM:1",LocalDateTime.of(2026,7,26,11,0));

        assertEquals(9L,next.getOwnerId());
        assertEquals(4L,next.getOwnerDeptId());
    }

    @Test void overdueScheduledRoutingCreatesAnOverdueAuditableSla()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setOwnerDeptId(3L);
        previous.setCreatedAt(LocalDateTime.of(2026,7,24,8,0));
        LocalDateTime dueAt=LocalDateTime.of(2026,7,24,11,0);
        activeScheduleFence();
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"template_code","TD-003","template_name","Retry contact",
                "business_type","LEAD","owner_rule_json","OWNER","status","PUBLISHED",
                "sla_rule_json","{\"calendarCode\":\"DEFAULT\"}",
                "definition_json",scheduledDefinition(8L)));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "calendar_id",1L,"work_days","1,2,3,4,5,6,7","work_start","00:00:00",
                "work_end","23:59:59","exception_json","{}"));
        when(mapper.linkScheduleOccurrenceByKey(org.mockito.ArgumentMatchers.eq("3:T1_AM:1"),
                any(),org.mockito.ArgumentMatchers.eq(0),any())).thenReturn(1);

        TodoInstance next=new TodoRoutingService(mapper).createScheduledNext(
                previous,22L,"3:T1_AM:1",dueAt);

        assertEquals(dueAt,next.getDueAt());
        assertEquals("OVERDUE",next.getSlaStatus());
        verify(mapper).insertScheduledSlaRecord(org.mockito.ArgumentMatchers.argThat(row->
                dueAt.equals(row.get("dueAt"))
                && dueAt.equals(row.get("overdue100DueAt"))
                && row.get("overdue100At")!=null
                && ((LocalDateTime)row.get("startAt")).isBefore(dueAt)));
    }

    @Test void cancelledClaimFencesScheduledTodoCreation()
    {
        when(mapper.selectScheduleOccurrenceIdentityByKey("3:T1_AM:1")).thenReturn(scheduleIdentity());
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of(
                "planId",3L,"status","CANCELLED","templateVersionId",22L));

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper)
                .createScheduledNext(todo(),22L,"3:T1_AM:1",LocalDateTime.now()));

        assertEquals("TODO_SCHEDULE_OCCURRENCE_NOT_CLAIMED",error.getBusinessCode());
        verify(mapper,never()).insertInstance(any());
        verify(mapper,never()).selectScheduleOccurrenceWindowForUpdate(any(),any());
    }

    @Test void legacyScheduledOwnerMustPassProductionResolution()
    {
        TodoAssignmentResolver resolver=mock(TodoAssignmentResolver.class);
        when(resolver.resolve(any(com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule.class),
                any(com.law.todo.assignment.OwnerResolutionContext.class)))
                .thenReturn(OwnerResolutionResult.empty());
        activeScheduleFence();
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of(
                "template_id",5L,"template_code","TD-003","template_name","Retry contact",
                "business_type","LEAD","owner_rule_json","USER:9","status","PUBLISHED",
                "sla_rule_json","{\"calendarCode\":\"DEFAULT\"}",
                "definition_json",scheduledDefinition(9L)));

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper,resolver)
                .createScheduledNext(todo(),22L,"3:T1_AM:1",LocalDateTime.now().plusHours(1)));

        assertEquals("TODO_OWNER_UNRESOLVED",error.getBusinessCode());
        verify(mapper,never()).insertInstance(any());
    }

    @Test void scheduledRoutingFailsClosedWhenInitialSnapshotCannotBePersisted()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setOwnerDeptId(3L);
        activeScheduleFence();
        when(mapper.selectTemplateVersionById(22L)).thenReturn(scheduledVersion());
        when(mapper.updateInitialRouteSnapshot(any(),any(),any(),any())).thenReturn(0);

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper)
                .createScheduledNext(previous,22L,"3:T1_AM:1",LocalDateTime.now().plusHours(1)));

        assertEquals("TODO_SCHEDULE_ROUTE_SNAPSHOT_PERSIST_FAILED",error.getBusinessCode());
        verify(mapper,never()).insertRelation(anyMap());
        verify(mapper,never()).linkScheduleOccurrenceByKey(any(),any(),anyInt(),any());
    }

    @Test void concurrentScheduledDuplicateWithIncompleteSnapshotFailsClosed()
    {
        TodoInstance previous=todo();previous.setOwnerId(8L);previous.setOwnerDeptId(3L);
        TodoInstance incomplete=new TodoInstance();incomplete.setTodoId(88L);
        incomplete.setOccurrenceKey("3:T1_AM:1");incomplete.setRouteNodeKey("td003");
        activeScheduleFence();
        when(mapper.selectTemplateVersionById(22L)).thenReturn(scheduledVersion());
        when(mapper.selectByNextKey("SCHEDULE:3:T1_AM:1")).thenReturn(null,incomplete);
        doThrow(new org.springframework.dao.DuplicateKeyException("concurrent"))
                .when(mapper).insertInstance(any());

        TodoException error=assertThrows(TodoException.class,()->new TodoRoutingService(mapper)
                .createScheduledNext(previous,22L,"3:T1_AM:1",LocalDateTime.now().plusHours(1)));

        assertEquals("TODO_SCHEDULE_ROUTE_SNAPSHOT_INVALID",error.getBusinessCode());
        verify(mapper,never()).linkScheduleOccurrenceByKey(any(),any(),anyInt(),any());
    }

    private void activeScheduleFence()
    {
        when(mapper.selectScheduleOccurrenceIdentityByKey("3:T1_AM:1")).thenReturn(scheduleIdentity());
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of(
                "planId",3L,"status","ACTIVE","templateVersionId",22L));
        when(mapper.selectScheduleOccurrenceWindowForUpdate("3:T1_AM:1",3L)).thenReturn(Map.of(
                "occurrenceId",9L,"planId",3L,"windowId",12L,"occurrenceKey","3:T1_AM:1",
                "version",0,"status","CLAIMED","windowStatus","PROCESSING"));
    }

    private void materializedScheduleFence(Long todoId)
    {
        when(mapper.selectScheduleOccurrenceIdentityByKey("3:T1_AM:1")).thenReturn(scheduleIdentity());
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of(
                "planId",3L,"status","ACTIVE","templateVersionId",22L));
        Map<String,Object> fence=new java.util.LinkedHashMap<>();
        fence.put("occurrenceId",9L);fence.put("planId",3L);fence.put("windowId",12L);
        fence.put("occurrenceKey","3:T1_AM:1");fence.put("version",0);
        fence.put("status","MATERIALIZED");fence.put("windowStatus","MATERIALIZED");
        fence.put("todoId",todoId);
        when(mapper.selectScheduleOccurrenceWindowForUpdate("3:T1_AM:1",3L)).thenReturn(fence);
    }

    private TodoInstance scheduledExisting(Long todoId)
    {
        TodoInstance existing=new TodoInstance();existing.setTodoId(todoId);
        existing.setRootTodoId(todoId);existing.setTemplateVersionId(22L);
        existing.setBusinessType("LEAD");existing.setBusinessId(7L);
        existing.setRouteNodeKey("td003");existing.setOccurrenceKey("3:T1_AM:1");
        existing.setRouteToken(JSON.toJSONString(new RouteToken(todoId,"td003",null,0,
                RouteTokenStatus.ACTIVE)));
        return existing;
    }

    private void verifyMaterializedReplayHasNoWrites()
    {
        verify(mapper,never()).insertInstance(any());
        verify(mapper,never()).updateInitialRouteSnapshot(any(),any(),any(),any());
        verify(mapper,never()).insertRelation(anyMap());
        verify(mapper,never()).linkScheduleOccurrenceByKey(any(),any(),anyInt(),any());
    }

    private Map<String,Object> scheduleIdentity()
    {
        return Map.of("occurrenceId",9L,"planId",3L,"windowId",12L,
                "occurrenceKey","3:T1_AM:1");
    }

    private Map<String,Object> scheduledVersion()
    {
        return Map.of("template_id",5L,"template_code","TD-003","template_name","Retry contact",
                "business_type","LEAD","owner_rule_json","OWNER","status","PUBLISHED",
                "sla_rule_json","{\"calendarCode\":\"DEFAULT\"}",
                "definition_json",scheduledDefinition(8L));
    }

    private String scheduledDefinition(long ownerId)
    {
        return """
                {"schemaVersion":1,"templateCode":"TD-003",
                 "owner":{"config":{"type":"USER","operand":%d}},
                 "routing":{"config":{"start":"td003","nodes":[
                   {"key":"td003","type":"TASK","templateCode":"TD-003","templateVersionId":22}
                 ]}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """.formatted(ownerId);
    }

    private TodoInstance todo(){TodoInstance t=new TodoInstance();t.setTodoId(1L);t.setBusinessType("LEAD");t.setBusinessId(7L);t.setRootTodoId(1L);return t;}
}

package com.law.todo.extension;

import static com.law.todo.extension.TodoExtensionCommands.ExtensionStatus.APPROVED;
import static com.law.todo.extension.TodoExtensionCommands.ExtensionStatus.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.dao.DuplicateKeyException;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.extension.TodoExtensionCommands.DecisionCommand;
import com.law.todo.extension.TodoExtensionCommands.ExtensionView;
import com.law.todo.extension.TodoExtensionCommands.RequestCommand;
import com.law.todo.mapper.TodoMapper;

class TodoExtensionServiceTest
{
    private static final LocalDateTime START=LocalDateTime.of(2026,7,10,9,0);
    private static final LocalDateTime ORIGINAL=LocalDateTime.of(2026,7,13,9,0);
    private static final LocalDateTime REQUESTED=LocalDateTime.of(2026,7,14,9,0);
    private final TodoMapper mapper=mock(TodoMapper.class);
    private final Actor requester=new Actor(7L,"requester",3L);
    private final Actor approver=new Actor(8L,"approver",4L);

    @BeforeEach void actionPersistence()
    {
        org.mockito.Mockito.lenient().when(mapper.insertExtensionActionIfAbsent(anyMap())).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.completeExtensionAction(anyMap())).thenReturn(1);
    }

    @Test void pendingRequestExplicitlyKeepsSlaRunning()
    {
        when(mapper.selectExtensionContext(9L)).thenReturn(context());
        when(mapper.countApprovedExtensions(9L,101L)).thenReturn(0);
        when(mapper.insertExtensionRequest(anyMap())).thenAnswer(invocation->{
            Map<String,Object> row=invocation.getArgument(0);row.put("extensionId",31L);return 1;
        });
        when(mapper.selectExtensionById(31L)).thenReturn(extension(PENDING,null,null));

        ExtensionView value=service().request(9L,new RequestCommand("request-1",REQUESTED,"Awaiting court material",List.of(41L)),requester);

        assertEquals(PENDING,value.status());
        assertEquals(TodoExtensionCommands.PendingSlaMode.CONTINUE,value.pendingSlaMode());
        verify(mapper,never()).applyApprovedExtension(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test void approvedExtensionPreservesOriginalDueAndRecomputesOnlyUnfiredThresholds()
    {
        when(mapper.selectExtensionById(31L)).thenReturn(extension(PENDING,null,null));
        when(mapper.decideExtensionConditionally(anyMap())).thenReturn(1);
        when(mapper.applyApprovedExtension(anyMap())).thenReturn(1);
        when(mapper.selectExtensionById(31L)).thenReturn(extension(PENDING,null,null),extension(APPROVED,REQUESTED,"approve-1"));

        ExtensionView value=service().approve(31L,new DecisionCommand("approve-1","Approved"),approver);

        assertEquals(ORIGINAL,value.originalDueAt());
        assertEquals(REQUESTED,value.approvedDueAt());
        verify(mapper).applyApprovedExtension(org.mockito.ArgumentMatchers.argThat(row ->
            ORIGINAL.equals(row.get("expectedDueAt"))
                && row.get("remind80DueAt")==null
                && row.get("overdue100DueAt")!=null
                && row.get("escalate150DueAt")!=null));
    }

    @Test void duplicateDecisionActionReturnsItsPriorResultWithoutOverwritingDecision()
    {
        when(mapper.selectExtensionActionById("approve-1")).thenReturn(action("APPROVE","APPLIED",31L,"APPROVED"));
        when(mapper.selectExtensionById(31L)).thenReturn(extension(APPROVED,REQUESTED,"approve-1"));

        ExtensionView value=service().approve(31L,new DecisionCommand("approve-1","ignored"),approver);

        assertEquals(APPROVED,value.status());
        verify(mapper,never()).decideExtensionConditionally(anyMap());
        verify(mapper,never()).applyApprovedExtension(anyMap());
    }

    @Test void concurrentDecisionCannotOverwritePriorDecision()
    {
        when(mapper.selectExtensionById(31L)).thenReturn(extension(PENDING,null,null),extension(APPROVED,REQUESTED,"approve-other"));
        when(mapper.decideExtensionConditionally(anyMap())).thenReturn(0);

        TodoException error=assertThrows(TodoException.class,
            ()->service().reject(31L,new DecisionCommand("reject-1","Rejected"),approver));

        assertEquals("TODO_EXTENSION_ALREADY_DECIDED",error.getBusinessCode());
        verify(mapper,never()).applyApprovedExtension(anyMap());
    }

    @Test void requestRejectsNonPositiveProofIdsAndConfiguredMaximum()
    {
        assertEquals("TODO_EXTENSION_PROOF_INVALID",assertThrows(TodoException.class,
            ()->service().request(9L,new RequestCommand("r",REQUESTED,"reason",List.of(0L)),requester)).getBusinessCode());

        when(mapper.selectExtensionContext(9L)).thenReturn(context());
        assertEquals("TODO_EXTENSION_DURATION_EXCEEDED",assertThrows(TodoException.class,
            ()->service().request(9L,new RequestCommand("too-long",REQUESTED.plusDays(30),"reason",List.of(1L)),requester)).getBusinessCode());
    }

    @Test void approvalRechecksRequestedDueAgainstCurrentEffectiveDue()
    {
        Map<String,Object> pending=extension(PENDING,null,null);
        pending.put("effective_due_at",REQUESTED.plusDays(1));
        when(mapper.selectExtensionById(31L)).thenReturn(pending);

        TodoException error=assertThrows(TodoException.class,
            ()->service().approve(31L,new DecisionCommand("approve-late","Approved"),approver));

        assertEquals("TODO_EXTENSION_DUE_INVALID",error.getBusinessCode());
        verify(mapper,never()).decideExtensionConditionally(anyMap());
    }

    @Test void zeroConfiguredExtensionCountDisablesNormalExtensions()
    {
        Map<String,Object> disabled=new java.util.HashMap<>(context());disabled.put("max_extension_count",0);
        when(mapper.selectExtensionContext(9L)).thenReturn(disabled);

        TodoException error=assertThrows(TodoException.class,
            ()->service().request(9L,new RequestCommand("disabled",REQUESTED,"reason",List.of(1L)),requester));

        assertEquals("TODO_EXTENSION_COUNT_EXCEEDED",error.getBusinessCode());
    }

    @Test void approveCannotReplayARejectActionAsSuccess()
    {
        when(mapper.selectExtensionActionById("decision-1")).thenReturn(action("REJECT","APPLIED",31L,"REJECTED"));

        TodoException error=assertThrows(TodoException.class,
            ()->service().approve(31L,new DecisionCommand("decision-1","approve"),approver));

        assertEquals("TODO_EXTENSION_ACTION_CONFLICT",error.getBusinessCode());
    }

    @Test void concurrentSameRequestActionReplaysCommittedWinner()
    {
        when(mapper.selectExtensionActionById("same-request")).thenReturn(null,action("REQUEST","APPLIED",31L,"PENDING"));
        when(mapper.insertExtensionActionIfAbsent(anyMap())).thenReturn(0);
        when(mapper.selectExtensionById(31L)).thenReturn(extension(PENDING,null,null));

        ExtensionView value=service().request(9L,new RequestCommand("same-request",REQUESTED,"reason",List.of(1L)),requester);

        assertEquals(31L,value.extensionId());
    }

    @Test void competingRequestForExistingPendingReturnsStableConflict()
    {
        when(mapper.selectExtensionContext(9L)).thenReturn(context());when(mapper.countApprovedExtensions(9L,101L)).thenReturn(0);
        when(mapper.insertExtensionRequest(anyMap())).thenThrow(new DuplicateKeyException("pending todo race"));
        when(mapper.selectPendingExtensionByTodoId(9L)).thenReturn(extension(PENDING,null,null));

        TodoException error=assertThrows(TodoException.class,
            ()->service().request(9L,new RequestCommand("second-request",REQUESTED,"reason",List.of(1L)),requester));

        assertEquals("TODO_EXTENSION_PENDING_EXISTS",error.getBusinessCode());
    }

    private TodoExtensionService service(){return new TodoExtensionService(mapper);}

    private Map<String,Object> context()
    {
        return Map.ofEntries(
            Map.entry("todo_id",9L),Map.entry("todo_status","IN_PROGRESS"),Map.entry("owner_id",7L),
            Map.entry("start_at",START),Map.entry("due_at",ORIGINAL),Map.entry("calendar_id",2L),
            Map.entry("work_days","1,2,3,4,5"),Map.entry("work_start","09:00:00"),Map.entry("work_end","18:00:00"),
            Map.entry("exception_json","{}"),Map.entry("policy_version_id",101L),
            Map.entry("max_extension_count",2),Map.entry("max_extension_value",2),Map.entry("max_extension_unit","WORKING_DAYS"),
            Map.entry("proof_required",true),Map.entry("pending_sla_mode","CONTINUE"));
    }

    private Map<String,Object> extension(TodoExtensionCommands.ExtensionStatus status,LocalDateTime approved,String decisionAction)
    {
        java.util.HashMap<String,Object> row=new java.util.HashMap<>(context());
        row.put("extension_id",31L);row.put("request_action_id","request-1");row.put("status",status.name());
        row.put("original_due_at",ORIGINAL);row.put("requested_due_at",REQUESTED);row.put("approved_due_at",approved);
        row.put("requester_id",7L);row.put("proof_file_ids_json","[41]");row.put("decision_action_id",decisionAction);
        row.put("remind80_at",START.plusHours(1));row.put("overdue100_at",null);row.put("escalate150_at",null);
        return row;
    }

    private Map<String,Object> action(String type,String status,Long extensionId,String resultStatus)
    {
        return Map.of("action_id",type.toLowerCase()+"-action","action_type",type,"action_status",status,
            "todo_id",9L,"extension_id",extensionId,"result_status",resultStatus);
    }
}

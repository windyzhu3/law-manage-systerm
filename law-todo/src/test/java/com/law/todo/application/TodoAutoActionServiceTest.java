package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.spi.TodoAutoActionCapability.AutoActionResult;
import com.law.todo.spi.TodoAutoActionCapability.AutoActionStatus;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoAutoActionCapability;

@ExtendWith(MockitoExtension.class)
class TodoAutoActionServiceTest
{
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 10, 0);
    @Mock TodoMapper mapper;
    @Mock TodoAutoActionCapability complete;
    @Mock TodoAutoActionCapability transfer;
    private TodoAutoActionService service;

    @BeforeEach void setUp()
    {
        when(complete.actionType()).thenReturn("COMPLETE_DEFAULT");
        lenient().when(transfer.actionType()).thenReturn("TRANSFER");
        service = new TodoAutoActionService(mapper, List.of(complete,transfer));
    }

    @Test void unknownAutoActionIsNeverExecuted()
    {
        TodoException error = assertThrows(TodoException.class,
                () -> service.execute(rule("SCRIPT", Map.of()), todo(), NOW));
        assertEquals("TODO_AUTO_ACTION_NOT_ALLOWED", error.getBusinessCode());
        verify(mapper, never()).insertAutoActionExecutionIfAbsent(anyMap());
    }

    @Test void successfulExecutionUsesServiceActorAndWritesImmutableAudit()
    {
        TodoInstance todo = todo();
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.selectAutoActionExecution("AUTO:1:due-complete")).thenReturn(
                Map.of("execution_key", "AUTO:1:due-complete", "todo_id", 1L, "rule_key", "due-complete",
                        "action_type", "COMPLETE_DEFAULT", "status", "CLAIMED", "attempt_count", 1));
        when(complete.execute(any(), any(), any())).thenReturn(AutoActionResult.success());
        when(mapper.completeAutoActionExecution(anyMap())).thenReturn(1);
        when(mapper.insertAutoActionAudit(anyMap())).thenReturn(1);

        AutoActionResult result = service.execute(rule("COMPLETE_DEFAULT", Map.of()), todo, NOW);

        assertEquals(AutoActionStatus.SUCCESS, result.status());
        verify(complete).execute(any(), any(), org.mockito.ArgumentMatchers.eq(TodoAutoActionService.SERVICE_ACTOR));
        verify(mapper).insertAutoActionAudit(org.mockito.ArgumentMatchers.argThat(row ->
                "SUCCESS".equals(row.get("status")) && Integer.valueOf(1).equals(row.get("attemptNo"))));
    }

    @Test void completedExecutionIsReplayedWithoutInvokingCapability()
    {
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(0);
        when(mapper.selectAutoActionExecution("AUTO:1:due-complete")).thenReturn(
                Map.of("execution_key", "AUTO:1:due-complete", "todo_id", 1L, "rule_key", "due-complete",
                        "action_type", "COMPLETE_DEFAULT", "status", "SUCCESS", "attempt_count", 1));

        AutoActionResult result = service.execute(rule("COMPLETE_DEFAULT", Map.of()), todo(), NOW);

        assertEquals(AutoActionStatus.SUCCESS, result.status());
        verify(complete, never()).execute(any(), any(), any());
        verify(mapper, never()).insertAutoActionAudit(anyMap());
    }

    @Test void liveClaimCannotBeExecutedConcurrently()
    {
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(0);
        when(mapper.selectAutoActionExecution("AUTO:1:due-complete")).thenReturn(Map.of(
                "execution_key","AUTO:1:due-complete","todo_id",1L,"rule_key","due-complete",
                "action_type","COMPLETE_DEFAULT","status","CLAIMED","attempt_count",1,"claimed_at",NOW));
        when(mapper.claimStaleAutoActionExecution("AUTO:1:due-complete",1,NOW.minusMinutes(15),NOW)).thenReturn(0);

        AutoActionResult result=service.execute(rule("COMPLETE_DEFAULT",Map.of()),todo(),NOW);

        assertEquals(AutoActionStatus.RETRY,result.status());
        verify(complete,never()).execute(any(),any(),any());
    }

    @Test void staleClaimThatWouldReachMaxIsFinalizedDeadWithoutCapability()
    {
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(0);
        when(mapper.selectAutoActionExecution("AUTO:1:due-complete")).thenReturn(Map.of("execution_key","AUTO:1:due-complete","todo_id",1L,"rule_key","due-complete","action_type","COMPLETE_DEFAULT","status","CLAIMED","attempt_count",2,"claimed_at",NOW.minusHours(1)));
        when(mapper.finalizeStaleAutoActionDead("AUTO:1:due-complete",2,3,NOW.minusMinutes(15),NOW,"TODO_AUTO_ACTION_STALE_MAX_ATTEMPTS","Stale execution reached maximum attempts")).thenReturn(1);
        when(mapper.insertAutoActionAudit(anyMap())).thenReturn(1);

        AutoActionResult result=service.execute(rule("COMPLETE_DEFAULT",Map.of("maxAttempts",3)),todo(),NOW);

        assertEquals(AutoActionStatus.DEAD,result.status());verify(complete,never()).execute(any(),any(),any());
        verify(mapper).insertAutoActionAudit(org.mockito.ArgumentMatchers.argThat(row->"DEAD".equals(row.get("status"))&&Integer.valueOf(3).equals(row.get("attemptNo"))));
    }

    @Test void losingStaleFinalizationRaceReplaysWinnerWithoutDuplicateAudit()
    {
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(0);
        Map<String,Object> claimed=Map.of("execution_key","AUTO:1:due-complete","todo_id",1L,"rule_key","due-complete","action_type","COMPLETE_DEFAULT","status","CLAIMED","attempt_count",2,"claimed_at",NOW.minusHours(1));
        Map<String,Object> dead=Map.of("execution_key","AUTO:1:due-complete","todo_id",1L,"rule_key","due-complete","action_type","COMPLETE_DEFAULT","status","DEAD","attempt_count",3,"last_error_code","TODO_AUTO_ACTION_STALE_MAX_ATTEMPTS","last_error_message","stale");
        when(mapper.selectAutoActionExecution("AUTO:1:due-complete")).thenReturn(claimed,dead);
        when(mapper.finalizeStaleAutoActionDead(any(),any(Integer.class),any(Integer.class),any(),any(),any(),any())).thenReturn(0);

        assertEquals(AutoActionStatus.DEAD,service.execute(rule("COMPLETE_DEFAULT",Map.of("maxAttempts",3)),todo(),NOW).status());
        verify(complete,never()).execute(any(),any(),any());verify(mapper,never()).insertAutoActionAudit(anyMap());
    }

    @Test void staleClaimAlreadyBeyondConfiguredMaxNeverDecrementsAttemptCounter()
    {
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(0);
        when(mapper.selectAutoActionExecution("AUTO:1:due-complete")).thenReturn(Map.of("execution_key","AUTO:1:due-complete","todo_id",1L,"rule_key","due-complete","action_type","COMPLETE_DEFAULT","status","CLAIMED","attempt_count",4,"claimed_at",NOW.minusHours(1)));
        when(mapper.finalizeStaleAutoActionDead("AUTO:1:due-complete",4,4,NOW.minusMinutes(15),NOW,"TODO_AUTO_ACTION_STALE_MAX_ATTEMPTS","Stale execution reached maximum attempts")).thenReturn(1);
        when(mapper.insertAutoActionAudit(anyMap())).thenReturn(1);

        assertEquals(AutoActionStatus.DEAD,service.execute(rule("COMPLETE_DEFAULT",Map.of("maxAttempts",3)),todo(),NOW).status());
        verify(complete,never()).execute(any(),any(),any());
    }

    @Test void invalidRuntimeRuleIsDeadLetteredAndDoesNotAbortLaterDueRule()
    {
        TodoInstance todo=todo();when(mapper.selectAutoActionScanItems(NOW)).thenReturn(List.of(Map.of("todo",todo,"compiled_json","{\"schemaVersion\":1,\"templateCode\":\"T\",\"autoActions\":[{\"config\":{\"ruleKey\":\"bad-transfer\",\"actionType\":\"TRANSFER\",\"capability\":\"TRANSFER\",\"triggerAt\":\"DUE\",\"targetOwnerId\":-2}},{\"config\":{\"ruleKey\":\"good\",\"actionType\":\"COMPLETE_DEFAULT\",\"capability\":\"COMPLETE_DEFAULT\",\"triggerAt\":\"DUE\"}}],\"decisionRefs\":[],\"acceptanceRefs\":[]}","due_at",NOW.minusMinutes(1))));
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.selectAutoActionExecution("AUTO:1:INVALID:bad-transfer")).thenReturn(Map.of("execution_key","AUTO:1:INVALID:bad-transfer","todo_id",1L,"rule_key","INVALID:bad-transfer","action_type","INVALID_RULE","status","CLAIMED","attempt_count",1));
        when(mapper.selectAutoActionExecution("AUTO:1:good")).thenReturn(Map.of("execution_key","AUTO:1:good","todo_id",1L,"rule_key","good","action_type","COMPLETE_DEFAULT","status","CLAIMED","attempt_count",1));
        when(mapper.completeAutoActionExecution(anyMap())).thenReturn(1);when(mapper.insertAutoActionAudit(anyMap())).thenReturn(1);when(complete.execute(any(),any(),any())).thenReturn(AutoActionResult.success());

        assertEquals(2,service.scanDue(NOW));verify(transfer,never()).execute(any(),any(),any());verify(complete).execute(any(),any(),any());
        verify(mapper).insertAutoActionAudit(org.mockito.ArgumentMatchers.argThat(row->"INVALID_RULE".equals(row.get("actionType"))&&"DEAD".equals(row.get("status"))));
    }

    @Test void validatorFailureCannotBeBypassedAndIsRetryableWithImmutableAudit()
    {
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.selectAutoActionExecution("AUTO:1:due-complete")).thenReturn(
                Map.of("execution_key", "AUTO:1:due-complete", "todo_id", 1L, "rule_key", "due-complete",
                        "action_type", "COMPLETE_DEFAULT", "status", "CLAIMED", "attempt_count", 1));
        when(complete.execute(any(), any(), any())).thenThrow(new TodoException("TODO_DOD_FIELD_MISSING", "required"));
        when(mapper.completeAutoActionExecution(anyMap())).thenReturn(1);
        when(mapper.insertAutoActionAudit(anyMap())).thenReturn(1);

        AutoActionResult result = service.execute(rule("COMPLETE_DEFAULT", Map.of()), todo(), NOW);

        assertEquals(AutoActionStatus.RETRY, result.status());
        assertEquals("TODO_DOD_FIELD_MISSING", result.errorCode());
        verify(mapper).insertAutoActionAudit(org.mockito.ArgumentMatchers.argThat(row ->
                "RETRY".equals(row.get("status")) && "TODO_DOD_FIELD_MISSING".equals(row.get("errorCode"))));
    }

    @Test void dueScanExecutesOnlyReachedRulesFromPublishedSnapshot()
    {
        TodoInstance todo = todo();
        when(mapper.selectAutoActionScanItems(NOW)).thenReturn(List.of(Map.of(
                "todo", todo,
                "compiled_json", "{\"schemaVersion\":1,\"templateCode\":\"T\",\"autoActions\":["
                        + "{\"config\":{\"ruleKey\":\"early\",\"actionType\":\"COMPLETE_DEFAULT\",\"capability\":\"COMPLETE_DEFAULT\",\"triggerAt\":\"DUE\"}},"
                        + "{\"config\":{\"ruleKey\":\"late\",\"actionType\":\"COMPLETE_DEFAULT\",\"capability\":\"COMPLETE_DEFAULT\",\"triggerAt\":\"SLA_150\"}}],"
                        + "\"decisionRefs\":[],\"acceptanceRefs\":[]}",
                "due_at", NOW.minusMinutes(1), "escalate150_due_at", NOW.plusHours(1))));
        when(mapper.insertAutoActionExecutionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.selectAutoActionExecution("AUTO:1:early")).thenReturn(Map.of(
                "execution_key", "AUTO:1:early", "todo_id", 1L, "rule_key", "early",
                "action_type", "COMPLETE_DEFAULT", "status", "CLAIMED", "attempt_count", 1));
        when(complete.execute(any(), any(), any())).thenReturn(AutoActionResult.success());
        when(mapper.completeAutoActionExecution(anyMap())).thenReturn(1);
        when(mapper.insertAutoActionAudit(anyMap())).thenReturn(1);

        assertEquals(1, service.scanDue(NOW));
        verify(complete, org.mockito.Mockito.times(1)).execute(any(), any(), any());
    }

    private AutoActionRule rule(String action, Map<String,Object> additions)
    {
        java.util.Map<String,Object> config = new java.util.LinkedHashMap<>();
        config.put("ruleKey", "due-complete");config.put("actionType", action);config.put("capability",action);config.put("triggerAt", "DUE");
        config.putAll(additions);return new AutoActionRule(config);
    }

    private TodoInstance todo()
    {
        TodoInstance todo = new TodoInstance();todo.setTodoId(1L);todo.setStatus("SUBMITTED");
        todo.setTemplateVersionId(9L);todo.setDueAt(NOW.minusMinutes(1));return todo;
    }
}

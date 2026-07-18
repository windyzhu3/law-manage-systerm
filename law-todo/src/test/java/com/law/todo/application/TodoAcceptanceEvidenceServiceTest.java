package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.BatchBindMappingsCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.CreateScenarioCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.MappingTarget;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.UpdateMappingCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.UpdateScenarioCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoAcceptanceEvidenceMapper;

class TodoAcceptanceEvidenceServiceTest
{
    private final TodoAcceptanceEvidenceMapper mapper = Mockito.mock(TodoAcceptanceEvidenceMapper.class);
    private final TodoAcceptanceEvidenceService service = new TodoAcceptanceEvidenceService(mapper);
    private final Actor owner = new Actor(7L, "owner", 3L);
    private final Actor reviewer = new Actor(9L, "reviewer", 4L);
    private final LocalDateTime dueAt = LocalDateTime.of(2026, 7, 31, 18, 0);

    @Test
    void createScenarioAlwaysStartsAsDraft()
    {
        CreateScenarioCommand command = new CreateScenarioCommand("create-scenario", "PHASE_ONE_GOLDEN_PATH",
                "一期黄金路径", "线索至归档", "[]", "[]", "[]", null, null, null,
                null, null, null, null, "DRAFT", null);
        claim("create-scenario", "CREATE_SCENARIO", null, owner, command);
        when(mapper.selectScenarioByCode("PHASE_ONE_GOLDEN_PATH")).thenReturn(Map.of());
        when(mapper.insertScenario(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> value = invocation.getArgument(0); value.put("scenarioId", 11L); return 1;
        });
        when(mapper.completeAction("create-scenario", TodoAcceptanceEvidenceService.fingerprint(
                "CREATE_SCENARIO", null, null, command, owner), 11L)).thenReturn(1);
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("DRAFT", 0));

        var result = service.createScenario(command, owner);

        assertEquals("DRAFT", result.status());
        verify(mapper).insertScenario(org.mockito.ArgumentMatchers.argThat(value -> "DRAFT".equals(value.get("status"))));
    }

    @Test
    void scenarioSubmissionRequiresVersionedGoldenDataAndIndependentAccountability()
    {
        UpdateScenarioCommand command = updateScenario("submit-scenario", "IN_REVIEW", null, null, null,
                7L, 8L, 7L, dueAt, 0);
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("DRAFT", 0));

        TodoException error = assertThrows(TodoException.class, () -> service.updateScenario(command, owner));

        assertEquals("TODO_ACCEPTANCE_REVIEWER_INDEPENDENCE_REQUIRED", error.getBusinessCode());
        verify(mapper, never()).insertActionClaim(anyMap());
    }

    @Test
    void onlySelectedReviewerCanApproveScenario()
    {
        UpdateScenarioCommand command = updateScenario("approve-scenario", "APPROVED", "repo://golden/v1.json",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 1,
                7L, 8L, 9L, dueAt, 0);
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("IN_REVIEW", 0));
        active(7L, 8L, 9L);

        TodoException error = assertThrows(TodoException.class, () -> service.updateScenario(command, owner));

        assertEquals("TODO_ACCEPTANCE_REVIEWER_REQUIRED", error.getBusinessCode());
    }

    @Test
    void approvedScenarioIsImmutableAndStaleUpdateIsRejected()
    {
        UpdateScenarioCommand approved = updateScenario("change-approved", "APPROVED", "repo://golden/v1.json",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 1,
                7L, 8L, 9L, dueAt, 1);
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("APPROVED", 1));
        assertEquals("TODO_ACCEPTANCE_SCENARIO_STATE_INVALID",
                assertThrows(TodoException.class, () -> service.updateScenario(approved, reviewer)).getBusinessCode());

        UpdateScenarioCommand stale = updateScenario("stale-scenario", "DRAFT", null, null, null,
                null, null, null, null, 0);
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("DRAFT", 0));
        claim("stale-scenario", "UPDATE_SCENARIO", 11L, owner, stale);
        when(mapper.updateScenarioConditionally(anyMap())).thenReturn(0);
        assertEquals("TODO_ACCEPTANCE_VERSION_CONFLICT",
                assertThrows(TodoException.class, () -> service.updateScenario(stale, owner)).getBusinessCode());
    }

    @Test
    void bindingAnUnmappedAtCreatesMappedEvidenceButNeverApproval()
    {
        UpdateMappingCommand command = new UpdateMappingCommand("bind-at", 21L, 0, 11L,
                "e2e/phase-one.spec.js#golden", "覆盖首联", null, null, null, "MAPPED", null);
        when(mapper.selectMappingById(21L)).thenReturn(mapping("UNMAPPED", 0), mapping("MAPPED", 1));
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("DRAFT", 0));
        claim("bind-at", "UPDATE_MAPPING", 21L, owner, command);
        when(mapper.updateMappingConditionally(anyMap())).thenReturn(1);
        when(mapper.completeAction("bind-at", TodoAcceptanceEvidenceService.fingerprint(
                "UPDATE_MAPPING", 21L, 0, command, owner), 21L)).thenReturn(1);

        var result = service.updateMapping(command, owner);

        assertEquals("MAPPED", result.status());
        verify(mapper).updateMappingConditionally(org.mockito.ArgumentMatchers.argThat(value ->
                "MAPPED".equals(value.get("status")) && value.get("reviewedBy") == null));
    }

    @Test
    void mappingApprovalRequiresApprovedScenarioAndSelectedReviewer()
    {
        UpdateMappingCommand command = new UpdateMappingCommand("approve-at", 21L, 0, 11L,
                "e2e/phase-one.spec.js#golden", "覆盖首联", 7L, 9L, dueAt, "APPROVED", "通过");
        when(mapper.selectMappingById(21L)).thenReturn(mapping("IN_REVIEW", 0));
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("IN_REVIEW", 0));
        active(7L, 9L);

        TodoException error = assertThrows(TodoException.class, () -> service.updateMapping(command, reviewer));

        assertEquals("TODO_ACCEPTANCE_SCENARIO_NOT_APPROVED", error.getBusinessCode());
    }

    @Test
    void batchBindCanOnlyProduceMappedRows()
    {
        BatchBindMappingsCommand command = new BatchBindMappingsCommand("batch-bind",
                List.of(new MappingTarget(21L, 0), new MappingTarget(22L, 0)), 11L,
                "e2e/phase-one.spec.js#golden", "一期黄金路径");
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("DRAFT", 0));
        when(mapper.selectMappingById(21L)).thenReturn(mapping("UNMAPPED", 0), mapping("MAPPED", 1));
        when(mapper.selectMappingById(22L)).thenReturn(mapping(22L, "REJECTED", 0), mapping(22L, "MAPPED", 1));
        claim("batch-bind", "BATCH_BIND_MAPPING", null, owner, command);
        when(mapper.updateMappingConditionally(anyMap())).thenReturn(1);
        when(mapper.completeAction("batch-bind", TodoAcceptanceEvidenceService.fingerprint(
                "BATCH_BIND_MAPPING", null, null, command, owner), 21L)).thenReturn(1);

        var result = service.batchBind(command, owner);

        assertEquals(2, result.size());
        verify(mapper, Mockito.times(2)).updateMappingConditionally(org.mockito.ArgumentMatchers.argThat(value ->
                "MAPPED".equals(value.get("status")) && value.get("reviewerUserId") == null));
    }

    @Test
    void sameActionReplaysAndChangedFingerprintConflicts()
    {
        UpdateMappingCommand command = new UpdateMappingCommand("replay-at", 21L, 0, 11L,
                "e2e/phase-one.spec.js#golden", null, null, null, null, "MAPPED", null);
        when(mapper.selectMappingById(21L)).thenReturn(mapping("UNMAPPED", 0), mapping("MAPPED", 1));
        when(mapper.selectScenarioById(11L)).thenReturn(scenario("DRAFT", 0));
        String fingerprint = TodoAcceptanceEvidenceService.fingerprint("UPDATE_MAPPING", 21L, 0, command, owner);
        when(mapper.selectActionForUpdate("replay-at")).thenReturn(action("UPDATE_MAPPING", fingerprint, owner.userId(), 21L));

        assertEquals("MAPPED", service.updateMapping(command, owner).status());
        verify(mapper, never()).updateMappingConditionally(anyMap());

        when(mapper.selectActionForUpdate("replay-at")).thenReturn(action("UPDATE_MAPPING", "different", owner.userId(), 21L));
        assertEquals("TODO_ACCEPTANCE_ACTION_CONFLICT",
                assertThrows(TodoException.class, () -> service.updateMapping(command, owner)).getBusinessCode());
    }

    private UpdateScenarioCommand updateScenario(String actionId, String status, String datasetRef,
            String checksum, Integer datasetVersion, Long ownerId, Long acceptorId, Long reviewerId,
            LocalDateTime due, int version)
    {
        return new UpdateScenarioCommand(actionId, 11L, version, "PHASE_ONE_GOLDEN_PATH", "一期黄金路径",
                "线索至归档", "[]", "[{\"step\":1}]", "[{\"result\":\"done\"}]", datasetRef,
                checksum, datasetVersion, ownerId, acceptorId, reviewerId, due, status, "APPROVED".equals(status) ? "通过" : null);
    }

    private void active(Long... ids)
    {
        for (Long id : ids) when(mapper.selectActiveUser(id)).thenReturn(Map.of("user_id", id));
    }

    private void claim(String actionId, String type, Long entityId, Actor actor, Object command)
    {
        Integer version = command instanceof UpdateScenarioCommand value ? value.version()
                : command instanceof UpdateMappingCommand value ? value.version() : null;
        String fingerprint = TodoAcceptanceEvidenceService.fingerprint(type, entityId, version, command, actor);
        when(mapper.insertActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectActionForUpdate(actionId)).thenReturn(action(type, fingerprint, actor.userId(), null));
    }

    private Map<String, Object> scenario(String status, int version)
    {
        Map<String, Object> row = new HashMap<>(); row.put("scenario_id", 11L);
        row.put("scenario_code", "PHASE_ONE_GOLDEN_PATH"); row.put("scenario_name", "一期黄金路径");
        row.put("delivery_phase", "PHASE_ONE"); row.put("business_path", "线索至归档");
        row.put("preconditions_json", "[]"); row.put("steps_json", "[{\"step\":1}]");
        row.put("expected_outcomes_json", "[{\"result\":\"done\"}]"); row.put("status", status);
        row.put("owner_user_id", 7L); row.put("acceptor_user_id", 8L); row.put("reviewer_user_id", 9L);
        row.put("due_at", dueAt); row.put("version", version); return row;
    }

    private Map<String, Object> mapping(String status, int version) { return mapping(21L, status, version); }
    private Map<String, Object> mapping(Long id, String status, int version)
    {
        Map<String, Object> row = new HashMap<>(); row.put("mapping_id", id);
        row.put("acceptance_ref", "AT-TD-001-OWNER"); row.put("template_code", "TD-001");
        row.put("dimension_code", "OWNER"); row.put("scenario_id", 11L);
        row.put("planned_test_ref", "e2e/phase-one.spec.js#golden"); row.put("status", status);
        row.put("version", version); return row;
    }

    private Map<String, Object> action(String type, String fingerprint, Long operatorId, Long resultId)
    {
        Map<String, Object> row = new HashMap<>(); row.put("action_type", type);
        row.put("request_fingerprint", fingerprint); row.put("operator_id", operatorId);
        row.put("action_status", resultId == null ? "CLAIMED" : "APPLIED");
        row.put("result_entity_id", resultId); return row;
    }
}

package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.law.todo.mapper.TodoAcceptanceReadinessMapper;

class TodoAcceptanceReadinessServiceTest
{
    private final TodoAcceptanceReadinessMapper mapper = Mockito.mock(TodoAcceptanceReadinessMapper.class);
    private final TodoAcceptanceReadinessService service = new TodoAcceptanceReadinessService(mapper);

    @Test
    void technicalCatalogueDoesNotMakeBusinessAcceptanceReady()
    {
        when(mapper.selectAcceptanceReadiness("G-07")).thenReturn(rows(state(19, 114, 114, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0)));

        var result = service.readiness("G-07");

        assertEquals(8, result.total());
        assertEquals(3, result.ready());
        assertEquals(4, result.runtimeMissing());
        assertEquals(1, result.runtimeIncomplete());
        assertEquals(0, result.sourceUnresolved());
        assertFalse(result.gateReady());
        assertEquals("READY", status(result, "PHASE_ONE_SCOPE_MANIFEST"));
        assertEquals("READY", status(result, "PHASE_ONE_AT_CATALOG"));
        assertEquals("RUNTIME_MISSING", status(result, "PHASE_ONE_SCENARIO_CATALOG"));
        assertEquals("RUNTIME_MISSING", status(result, "PHASE_ONE_GOLDEN_DATASET"));
        assertEquals("RUNTIME_INCOMPLETE", status(result, "PHASE_ONE_AT_MAPPING"));
        assertEquals(0, result.mockBusinessEvidenceCount());
    }

    @Test
    void partialScenarioAndMappingEvidenceRemainIncomplete()
    {
        when(mapper.selectAcceptanceReadiness("G-07")).thenReturn(rows(state(19, 114, 114, 0,
                2, 1, 1, 0, 1, 40, 20, 10, 20)));

        var result = service.readiness("G-07");

        assertEquals("RUNTIME_INCOMPLETE", status(result, "PHASE_ONE_SCENARIO_CATALOG"));
        assertEquals("RUNTIME_INCOMPLETE", status(result, "PHASE_ONE_GOLDEN_DATASET"));
        assertEquals("RUNTIME_INCOMPLETE", status(result, "PHASE_ONE_AT_MAPPING"));
        assertEquals("RUNTIME_INCOMPLETE", status(result, "PHASE_ONE_ACCEPTOR_ASSIGNMENT"));
        assertEquals("RUNTIME_INCOMPLETE", status(result, "PHASE_ONE_INDEPENDENT_REVIEW"));
        assertFalse(result.gateReady());
    }

    @Test
    void allEightRequirementsReadyMakeGateReady()
    {
        when(mapper.selectAcceptanceReadiness("G-07")).thenReturn(rows(state(19, 114, 114, 0,
                2, 2, 1, 1, 2, 114, 114, 114, 114)));

        var result = service.readiness("G-07");

        assertEquals(8, result.ready());
        assertEquals(0, result.runtimeMissing());
        assertEquals(0, result.runtimeIncomplete());
        assertTrue(result.gateReady());
        assertTrue(service.gateReady("G-07"));
    }

    @Test
    void emptyRequirementCatalogueCanNeverPass()
    {
        when(mapper.selectAcceptanceReadiness("G-07")).thenReturn(List.of());

        var result = service.readiness("G-07");

        assertEquals(0, result.total());
        assertFalse(result.gateReady());
    }

    private List<Map<String, Object>> rows(Map<String, Object> state)
    {
        String[][] requirements = {
            {"PHASE_ONE_SCOPE_MANIFEST", "SCOPE_MANIFEST"},
            {"PHASE_ONE_AT_CATALOG", "AT_CATALOG"},
            {"PHASE_ONE_SCENARIO_CATALOG", "SCENARIO_CATALOG"},
            {"PHASE_ONE_GOLDEN_DATASET", "GOLDEN_DATASET"},
            {"PHASE_ONE_AT_MAPPING", "AT_MAPPING"},
            {"PHASE_ONE_ACCEPTOR_ASSIGNMENT", "ACCEPTOR_ASSIGNMENT"},
            {"PHASE_ONE_INDEPENDENT_REVIEW", "INDEPENDENT_REVIEW"},
            {"MOCK_E2E_SEPARATION", "MOCK_SEPARATION"}
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < requirements.length; index++)
        {
            Map<String, Object> row = new HashMap<>(state);
            row.put("requirement_id", (long) index + 1);
            row.put("gate_code", "G-07");
            row.put("requirement_code", requirements[index][0]);
            row.put("requirement_name", requirements[index][0]);
            row.put("check_kind", requirements[index][1]);
            row.put("source_ref", "repo://acceptance");
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> state(int templates, int catalogAt, int mappingAt, int mismatch,
            int scenarios, int approvedScenarios, int goldenScenarios, int goldenReady,
            int scenarioAccountability, int mappedAt, int mappingAccountability,
            int inReviewAt, int approvedAt)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("phase_one_template_count", templates);
        row.put("catalog_at_count", catalogAt);
        row.put("mapping_at_count", mappingAt);
        row.put("catalog_mismatch_count", mismatch);
        row.put("scenario_count", scenarios);
        row.put("approved_scenario_count", approvedScenarios);
        row.put("golden_scenario_count", goldenScenarios);
        row.put("golden_scenario_ready_count", goldenReady);
        row.put("scenario_accountability_count", scenarioAccountability);
        row.put("mapped_at_count", mappedAt);
        row.put("mapping_accountability_count", mappingAccountability);
        row.put("in_review_at_count", inReviewAt);
        row.put("approved_at_count", approvedAt);
        row.put("mock_business_evidence_count", 0);
        return row;
    }

    private String status(com.law.todo.application.view.TodoAcceptanceReadinessView result, String code)
    {
        return result.requirements().stream().filter(row -> code.equals(row.requirementCode()))
                .findFirst().orElseThrow().readinessStatus();
    }
}

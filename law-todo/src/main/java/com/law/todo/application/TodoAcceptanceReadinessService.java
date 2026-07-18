package com.law.todo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.TodoAcceptanceReadinessView;
import com.law.todo.application.view.TodoAcceptanceRequirementView;
import com.law.todo.mapper.TodoAcceptanceReadinessMapper;

@Service
public class TodoAcceptanceReadinessService
{
    private static final int PHASE_ONE_TEMPLATE_TOTAL = 19;
    private static final int PHASE_ONE_AT_TOTAL = 114;
    private final TodoAcceptanceReadinessMapper mapper;

    public TodoAcceptanceReadinessService(TodoAcceptanceReadinessMapper mapper)
    {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public TodoAcceptanceReadinessView readiness(String gateCode)
    {
        List<Map<String, Object>> rows = mapper.selectAcceptanceReadiness(gateCode);
        Map<String, Object> state = rows.isEmpty() ? Map.of() : rows.get(0);
        List<TodoAcceptanceRequirementView> requirements = rows.stream().map(row -> view(row, state)).toList();
        int ready = count(requirements, "READY");
        int sourceUnresolved = count(requirements, "SOURCE_UNRESOLVED");
        int runtimeMissing = count(requirements, "RUNTIME_MISSING");
        int runtimeIncomplete = count(requirements, "RUNTIME_INCOMPLETE");
        int mismatch = integer(value(state, "catalog_mismatch_count", "catalogMismatchCount"));
        return new TodoAcceptanceReadinessView(gateCode, requirements.size(), ready, sourceUnresolved,
                runtimeMissing, runtimeIncomplete,
                requirements.size() == 8 && ready == requirements.size() && mismatch == 0,
                integer(value(state, "phase_one_template_count", "phaseOneTemplateCount")),
                integer(value(state, "catalog_at_count", "catalogAtCount")),
                integer(value(state, "mapping_at_count", "mappingAtCount")), mismatch,
                integer(value(state, "scenario_count", "scenarioCount")),
                integer(value(state, "approved_scenario_count", "approvedScenarioCount")),
                integer(value(state, "golden_scenario_count", "goldenScenarioCount")),
                integer(value(state, "golden_scenario_ready_count", "goldenScenarioReadyCount")),
                integer(value(state, "scenario_accountability_count", "scenarioAccountabilityCount")),
                integer(value(state, "mapped_at_count", "mappedAtCount")),
                integer(value(state, "mapping_accountability_count", "mappingAccountabilityCount")),
                integer(value(state, "in_review_at_count", "inReviewAtCount")),
                integer(value(state, "approved_at_count", "approvedAtCount")),
                integer(value(state, "mock_business_evidence_count", "mockBusinessEvidenceCount")),
                List.copyOf(requirements));
    }

    @Transactional(readOnly = true)
    public boolean gateReady(String gateCode)
    {
        return readiness(gateCode).gateReady();
    }

    private TodoAcceptanceRequirementView view(Map<String, Object> row, Map<String, Object> state)
    {
        String status = status(text(value(row, "check_kind", "checkKind")), state);
        return new TodoAcceptanceRequirementView(number(value(row, "requirement_id", "requirementId")),
                text(value(row, "gate_code", "gateCode")),
                text(value(row, "requirement_code", "requirementCode")),
                text(value(row, "requirement_name", "requirementName")),
                text(value(row, "check_kind", "checkKind")),
                text(value(row, "source_ref", "sourceRef")), status,
                text(value(row, "remark", "remark")));
    }

    private String status(String checkKind, Map<String, Object> state)
    {
        int templates = integer(value(state, "phase_one_template_count", "phaseOneTemplateCount"));
        int catalogAt = integer(value(state, "catalog_at_count", "catalogAtCount"));
        int mappingAt = integer(value(state, "mapping_at_count", "mappingAtCount"));
        int mismatch = integer(value(state, "catalog_mismatch_count", "catalogMismatchCount"));
        int scenarios = integer(value(state, "scenario_count", "scenarioCount"));
        int approvedScenarios = integer(value(state, "approved_scenario_count", "approvedScenarioCount"));
        int goldenScenarios = integer(value(state, "golden_scenario_count", "goldenScenarioCount"));
        int goldenReady = integer(value(state, "golden_scenario_ready_count", "goldenScenarioReadyCount"));
        int scenarioAccountability = integer(value(state, "scenario_accountability_count", "scenarioAccountabilityCount"));
        int mappingAccountability = integer(value(state, "mapping_accountability_count", "mappingAccountabilityCount"));
        int approvedAt = integer(value(state, "approved_at_count", "approvedAtCount"));
        int mockBusinessEvidence = integer(value(state, "mock_business_evidence_count", "mockBusinessEvidenceCount"));
        return switch (checkKind)
        {
            case "SCOPE_MANIFEST" -> exact(templates, PHASE_ONE_TEMPLATE_TOTAL, mismatch);
            case "AT_CATALOG" -> exactCatalog(catalogAt, mappingAt, mismatch);
            case "SCENARIO_CATALOG" -> scenarios == 0 ? "RUNTIME_MISSING"
                    : approvedScenarios == scenarios ? "READY" : "RUNTIME_INCOMPLETE";
            case "GOLDEN_DATASET" -> goldenScenarios == 0 ? "RUNTIME_MISSING"
                    : goldenReady == 1 ? "READY" : "RUNTIME_INCOMPLETE";
            case "AT_MAPPING" -> mappingAt == 0 ? "RUNTIME_MISSING"
                    : approvedAt == PHASE_ONE_AT_TOTAL && mappingAt == PHASE_ONE_AT_TOTAL && mismatch == 0
                            ? "READY" : "RUNTIME_INCOMPLETE";
            case "ACCEPTOR_ASSIGNMENT" -> scenarios == 0 ? "RUNTIME_MISSING"
                    : scenarioAccountability == scenarios && mappingAccountability == PHASE_ONE_AT_TOTAL
                            ? "READY" : "RUNTIME_INCOMPLETE";
            case "INDEPENDENT_REVIEW" -> scenarios == 0 || approvedScenarios == 0 ? "RUNTIME_MISSING"
                    : approvedScenarios == scenarios && approvedAt == PHASE_ONE_AT_TOTAL
                            ? "READY" : "RUNTIME_INCOMPLETE";
            case "MOCK_SEPARATION" -> mockBusinessEvidence == 0 ? "READY" : "RUNTIME_INCOMPLETE";
            default -> "SOURCE_UNRESOLVED";
        };
    }

    private String exact(int actual, int expected, int mismatch)
    {
        if (actual == 0)
        {
            return "RUNTIME_MISSING";
        }
        return actual == expected && mismatch == 0 ? "READY" : "RUNTIME_INCOMPLETE";
    }

    private String exactCatalog(int catalogAt, int mappingAt, int mismatch)
    {
        if (catalogAt == 0 || mappingAt == 0)
        {
            return "RUNTIME_MISSING";
        }
        return catalogAt == PHASE_ONE_AT_TOTAL && mappingAt == PHASE_ONE_AT_TOTAL && mismatch == 0
                ? "READY" : "RUNTIME_INCOMPLETE";
    }

    private int count(List<TodoAcceptanceRequirementView> rows, String status)
    {
        return (int) rows.stream().filter(row -> status.equals(row.readinessStatus())).count();
    }

    private Object value(Map<String, Object> row, String snakeCase, String camelCase)
    {
        return row.containsKey(snakeCase) ? row.get(snakeCase) : row.get(camelCase);
    }

    private String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private Long number(Object value)
    {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private int integer(Object value)
    {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }
}

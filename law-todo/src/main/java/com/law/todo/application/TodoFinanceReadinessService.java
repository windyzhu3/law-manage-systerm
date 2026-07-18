package com.law.todo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.TodoFinanceReadinessView;
import com.law.todo.application.view.TodoFinanceRequirementView;
import com.law.todo.mapper.TodoFinanceReadinessMapper;

@Service
public class TodoFinanceReadinessService
{
    private final TodoFinanceReadinessMapper mapper;

    public TodoFinanceReadinessService(TodoFinanceReadinessMapper mapper)
    {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public TodoFinanceReadinessView readiness(String gateCode)
    {
        List<Map<String, Object>> rows = mapper.selectFinanceReadiness(gateCode);
        List<TodoFinanceRequirementView> requirements = rows.stream().map(this::view).toList();
        Map<String, Object> state = rows.isEmpty() ? Map.of() : rows.get(0);
        int ready = count(requirements, "READY");
        int sourceUnresolved = count(requirements, "SOURCE_UNRESOLVED");
        int runtimeMissing = count(requirements, "RUNTIME_MISSING");
        return new TodoFinanceReadinessView(gateCode, requirements.size(), ready, sourceUnresolved,
                runtimeMissing, !requirements.isEmpty() && ready == requirements.size(),
                integer(value(state, "fee_plan_core_column_count", "feePlanCoreColumnCount")),
                integer(value(state, "node_fee_column_count", "nodeFeeColumnCount")),
                integer(value(state, "finance_support_table_count", "financeSupportTableCount")),
                integer(value(state, "risk_schema_object_count", "riskSchemaObjectCount")),
                List.copyOf(requirements));
    }

    @Transactional(readOnly = true)
    public boolean gateReady(String gateCode)
    {
        return readiness(gateCode).gateReady();
    }

    private TodoFinanceRequirementView view(Map<String, Object> row)
    {
        String sourceStatus = text(value(row, "source_status", "sourceStatus"));
        String checkKind = text(value(row, "check_kind", "checkKind"));
        boolean runtimeReady = switch (checkKind)
        {
            case "FEE_PLAN_CORE" -> integer(value(row, "fee_plan_core_column_count", "feePlanCoreColumnCount")) == 4;
            case "NODE_FEE_SCHEMA" -> integer(value(row, "node_fee_column_count", "nodeFeeColumnCount")) == 6;
            case "SUPPORT_TABLES" -> integer(value(row, "finance_support_table_count", "financeSupportTableCount")) == 3;
            case "RISK_SCHEMA" -> integer(value(row, "risk_schema_object_count", "riskSchemaObjectCount")) == 4;
            default -> true;
        };
        String readinessStatus = !"CONFIRMED".equals(sourceStatus)
                ? "SOURCE_UNRESOLVED" : runtimeReady ? "READY" : "RUNTIME_MISSING";
        return new TodoFinanceRequirementView(number(value(row, "requirement_id", "requirementId")),
                text(value(row, "gate_code", "gateCode")),
                text(value(row, "requirement_code", "requirementCode")),
                text(value(row, "requirement_name", "requirementName")), checkKind, sourceStatus,
                text(value(row, "decision_ref", "decisionRef")),
                text(value(row, "source_ref", "sourceRef")), readinessStatus,
                text(value(row, "remark", "remark")));
    }

    private int count(List<TodoFinanceRequirementView> rows, String status)
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

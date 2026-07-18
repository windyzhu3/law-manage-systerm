package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.law.todo.mapper.TodoFinanceReadinessMapper;

class TodoFinanceReadinessServiceTest
{
    private final TodoFinanceReadinessMapper mapper=Mockito.mock(TodoFinanceReadinessMapper.class);
    private final TodoFinanceReadinessService service=new TodoFinanceReadinessService(mapper);

    @Test void reports_decision_and_schema_blockers_separately()
    {
        when(mapper.selectFinanceReadiness("G-06")).thenReturn(List.of(
                row("FEE_PLAN_CORE","CONFIRMED",4,6,0,0),row("NODE_FEE_SCHEMA","CONFIRMED",4,0,0,0),
                row("Q009_POLICY","NEEDS_DECISION",4,0,0,0)));
        var report=service.readiness("G-06");
        assertEquals(1,report.ready());assertEquals(1,report.runtimeMissing());assertEquals(1,report.sourceUnresolved());assertFalse(report.gateReady());
    }

    @Test void all_rows_must_be_ready()
    {
        when(mapper.selectFinanceReadiness("G-06")).thenReturn(List.of(row("FEE_PLAN_CORE","CONFIRMED",4,6,3,4),row("PAYMENT_SERVICE","CONFIRMED",4,6,3,4)));
        assertTrue(service.readiness("G-06").gateReady());assertTrue(service.gateReady("G-06"));
    }

    private Map<String,Object> row(String kind,String source,int core,int node,int support,int risk)
    {Map<String,Object> r=new HashMap<>();r.put("requirement_id",1L);r.put("gate_code","G-06");r.put("requirement_code",kind);r.put("requirement_name",kind);r.put("check_kind",kind);r.put("source_status",source);r.put("source_ref","repository");r.put("fee_plan_core_column_count",core);r.put("node_fee_column_count",node);r.put("finance_support_table_count",support);r.put("risk_schema_object_count",risk);r.put("remark","evidence");return r;}
}

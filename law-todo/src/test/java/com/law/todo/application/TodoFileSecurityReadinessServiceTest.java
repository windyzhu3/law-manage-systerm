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

import com.law.todo.mapper.TodoFileSecurityReadinessMapper;

class TodoFileSecurityReadinessServiceTest
{
    private final TodoFileSecurityReadinessMapper mapper=Mockito.mock(TodoFileSecurityReadinessMapper.class);
    private final TodoFileSecurityReadinessService service=new TodoFileSecurityReadinessService(mapper);

    @Test void separates_missing_runtime_controls_from_review_evidence()
    {
        when(mapper.selectSecurityReadiness("G-05")).thenReturn(List.of(
                row("OBJECT_MODEL","CONFIRMED",2,3,4,1,1,1),
                row("TOKEN_CONTROL","CONFIRMED",3,3,3,0,1,1),
                row("SECURITY_REVIEW","NEEDS_REVIEW",3,3,4,1,1,1)));

        var report=service.readiness("G-05");

        assertEquals(3,report.total());
        assertEquals(1,report.sourceUnresolved());
        assertEquals(2,report.runtimeMissing());
        assertFalse(report.gateReady());
    }

    @Test void all_confirmed_controls_and_runtime_invariants_are_required()
    {
        when(mapper.selectSecurityReadiness("G-05")).thenReturn(List.of(
                row("OBJECT_MODEL","CONFIRMED",3,3,4,1,1,1),
                row("TOKEN_CONTROL","CONFIRMED",3,3,4,1,1,1),
                row("ACCESS_AUDIT","CONFIRMED",3,3,4,1,1,1),
                row("CLEANUP_COMPENSATION","CONFIRMED",3,3,4,1,1,1)));

        assertTrue(service.readiness("G-05").gateReady());
        assertTrue(service.gateReady("G-05"));
    }

    private Map<String,Object> row(String kind,String source,int model,int expectedModel,int tokenColumns,int tokenIndex,int audit,int cleanup)
    {
        Map<String,Object> row=new HashMap<>();row.put("requirement_id",1L);row.put("gate_code","G-05");
        row.put("requirement_code",kind);row.put("requirement_name",kind);row.put("check_kind",kind);row.put("source_status",source);
        row.put("source_ref","repository");row.put("object_model_table_count",model);row.put("expected_object_model_table_count",expectedModel);
        row.put("token_security_column_count",tokenColumns);row.put("token_unique_index_count",tokenIndex);
        row.put("access_log_table_exists",audit);row.put("cleanup_table_exists",cleanup);row.put("remark","evidence");return row;
    }
}

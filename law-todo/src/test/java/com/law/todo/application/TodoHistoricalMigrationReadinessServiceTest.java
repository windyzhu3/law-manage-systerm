package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.law.todo.mapper.TodoHistoricalMigrationReadinessMapper;

class TodoHistoricalMigrationReadinessServiceTest
{
    private final TodoHistoricalMigrationReadinessMapper mapper=Mockito.mock(TodoHistoricalMigrationReadinessMapper.class);
    private final TodoHistoricalMigrationReadinessService service=new TodoHistoricalMigrationReadinessService(mapper);

    @Test void separates_source_schema_and_reference_blockers()
    {
        when(mapper.selectMigrationReadiness("G-04")).thenReturn(List.of(
                row(1L,"CASE_BUSINESS_LINE_SCHEMA","CONFIRMED",false,12,7,1),
                row(2L,"HISTORICAL_CASE_DEFAULT","NEEDS_DECISION",false,12,7,1),
                row(3L,"TODO_VERSION_REFERENCE","CONFIRMED",false,12,7,1)));

        var report=service.readiness("G-04");

        assertEquals(3,report.total());
        assertEquals(1,report.sourceUnresolved());
        assertEquals(1,report.runtimeMissing());
        assertEquals(1,report.runtimeInvalid());
        assertEquals(12,report.historicalCaseCount());
        assertEquals(7,report.historicalTodoCount());
        assertEquals(1,report.orphanTodoVersionCount());
        assertFalse(report.gateReady());
    }

    @Test void gate_is_ready_only_when_every_repository_requirement_is_ready()
    {
        when(mapper.selectMigrationReadiness("G-04")).thenReturn(List.of(
                row(1L,"CASE_BUSINESS_LINE_SCHEMA","CONFIRMED",true,12,7,0),
                row(2L,"HISTORICAL_CASE_DEFAULT","CONFIRMED",true,12,7,0),
                row(3L,"TODO_VERSION_REFERENCE","CONFIRMED",true,12,7,0)));

        var report=service.readiness("G-04");

        assertEquals(3,report.ready());
        assertTrue(report.gateReady());
        assertTrue(service.gateReady("G-04"));
    }

    private Map<String,Object> row(Long id,String code,String sourceStatus,boolean caseColumn,int cases,int todos,int orphans)
    {
        String kind=code.equals("CASE_BUSINESS_LINE_SCHEMA")?"CASE_COLUMN":code.equals("TODO_VERSION_REFERENCE")?"TODO_VERSION_REFERENCE":"SOURCE_ONLY";
        Map<String,Object> row=new HashMap<>();
        row.put("requirement_id",id);row.put("gate_code","G-04");row.put("requirement_code",code);row.put("requirement_name",code);
        row.put("check_kind",kind);row.put("source_status",sourceStatus);row.put("source_ref","repository");
        row.put("case_column_exists",caseColumn?1:0);row.put("historical_case_count",cases);
        row.put("historical_todo_count",todos);row.put("orphan_todo_version_count",orphans);row.put("remark","evidence");
        return row;
    }
}

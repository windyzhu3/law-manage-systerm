package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoHistoricalMigrationReadinessMapper;

class TodoHistoricalMigrationPreflightServiceTest
{
    private final TodoHistoricalMigrationReadinessMapper mapper=Mockito.mock(TodoHistoricalMigrationReadinessMapper.class);
    private final TodoHistoricalMigrationPreflightService service=new TodoHistoricalMigrationPreflightService(mapper);

    @Test void inventories_g04_historical_cases_and_rejects_other_gates()
    {
        when(mapper.selectHistoricalMigrationPreflightCounts()).thenReturn(Map.of(
                "active_case_count",12L,"deleted_case_count",2L,"historical_todo_count",7L,
                "orphan_todo_version_count",0L));
        Map<String,Object> nullType=new HashMap<>();
        nullType.put("case_status","pending");nullType.put("case_type",null);nullType.put("case_count",4L);
        when(mapper.selectHistoricalCaseGroups()).thenReturn(List.of(
                Map.of("case_status","processing","case_type","litigation","case_count",8L),
                nullType));

        var result=service.preflight("G-04");

        assertEquals(12,result.activeCaseCount());
        assertEquals(12,result.exceptionCandidateCount());
        assertEquals("<NULL>",result.groups().get(1).caseType());
        TodoException error=assertThrows(TodoException.class,()->service.preflight("G-05"));
        assertEquals("TODO_MIGRATION_GATE_UNSUPPORTED",error.getBusinessCode());
        verify(mapper).selectHistoricalMigrationPreflightCounts();
        verify(mapper).selectHistoricalCaseGroups();
        verifyNoMoreInteractions(mapper);
    }
}

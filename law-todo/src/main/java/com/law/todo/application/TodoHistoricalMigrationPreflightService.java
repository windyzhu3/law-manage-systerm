package com.law.todo.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.HistoricalCaseGroupView;
import com.law.todo.application.view.TodoHistoricalMigrationPreflightView;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoHistoricalMigrationReadinessMapper;

@Service
public class TodoHistoricalMigrationPreflightService
{
    private final TodoHistoricalMigrationReadinessMapper mapper;
    private final Clock clock;

    public TodoHistoricalMigrationPreflightService(TodoHistoricalMigrationReadinessMapper mapper)
    {
        this(mapper,Clock.systemUTC());
    }

    TodoHistoricalMigrationPreflightService(TodoHistoricalMigrationReadinessMapper mapper,Clock clock)
    {
        this.mapper=mapper;
        this.clock=clock;
    }

    @Transactional(readOnly=true)
    public TodoHistoricalMigrationPreflightView preflight(String gateCode)
    {
        requireGate(gateCode);
        Map<String,Object> counts=mapper.selectHistoricalMigrationPreflightCounts();
        List<HistoricalCaseGroupView> groups=mapper.selectHistoricalCaseGroups().stream()
                .map(TodoHistoricalMigrationPreflightService::group).toList();
        long active=number(counts,"active_case_count");
        return new TodoHistoricalMigrationPreflightView("G-04",clock.instant(),active,
                number(counts,"deleted_case_count"),number(counts,"historical_todo_count"),
                number(counts,"orphan_todo_version_count"),active,List.copyOf(groups));
    }

    private static void requireGate(String value)
    {
        if(!"G-04".equals(value)) throw new TodoException("TODO_MIGRATION_GATE_UNSUPPORTED","Only G-04 is supported");
    }

    private static HistoricalCaseGroupView group(Map<String,Object> row)
    {
        return new HistoricalCaseGroupView(text(row,"case_status"),text(row,"case_type"),number(row,"case_count"));
    }

    private static long number(Map<String,Object> row,String key)
    {
        Object value=row.get(key);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static String text(Map<String,Object> row,String key)
    {
        Object value=row.get(key);
        return value == null ? "<NULL>" : String.valueOf(value);
    }
}

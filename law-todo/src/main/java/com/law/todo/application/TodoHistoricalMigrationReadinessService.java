package com.law.todo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.TodoHistoricalMigrationReadinessView;
import com.law.todo.application.view.TodoHistoricalMigrationRequirementView;
import com.law.todo.mapper.TodoHistoricalMigrationReadinessMapper;

@Service
public class TodoHistoricalMigrationReadinessService
{
    private final TodoHistoricalMigrationReadinessMapper mapper;
    public TodoHistoricalMigrationReadinessService(TodoHistoricalMigrationReadinessMapper mapper){this.mapper=mapper;}

    @Transactional(readOnly=true)
    public TodoHistoricalMigrationReadinessView readiness(String gateCode)
    {
        List<Map<String,Object>> rows=mapper.selectMigrationReadiness(gateCode);
        List<TodoHistoricalMigrationRequirementView> requirements=rows.stream().map(this::view).toList();
        int ready=count(requirements,"READY");
        int source=count(requirements,"SOURCE_UNRESOLVED");
        int missing=count(requirements,"RUNTIME_MISSING");
        int invalid=count(requirements,"RUNTIME_INVALID");
        Map<String,Object> inventory=rows.isEmpty()?Map.of():rows.get(0);
        return new TodoHistoricalMigrationReadinessView(gateCode,requirements.size(),ready,source,missing,invalid,
                !requirements.isEmpty()&&ready==requirements.size(),integer(value(inventory,"historical_case_count","historicalCaseCount")),
                integer(value(inventory,"historical_todo_count","historicalTodoCount")),
                integer(value(inventory,"orphan_todo_version_count","orphanTodoVersionCount")),
                truth(value(inventory,"case_column_exists","caseColumnExists")),List.copyOf(requirements));
    }

    @Transactional(readOnly=true)
    public boolean gateReady(String gateCode){return readiness(gateCode).gateReady();}

    private TodoHistoricalMigrationRequirementView view(Map<String,Object> row)
    {
        String source=text(value(row,"source_status","sourceStatus"));
        String kind=text(value(row,"check_kind","checkKind"));
        String readiness;
        if(!"CONFIRMED".equals(source))readiness="SOURCE_UNRESOLVED";
        else if("CASE_COLUMN".equals(kind)&&!truth(value(row,"case_column_exists","caseColumnExists")))readiness="RUNTIME_MISSING";
        else if(("TODO_VERSION_REFERENCE".equals(kind)||"TODO_VERSION_IMMUTABILITY".equals(kind))
                &&integer(value(row,"orphan_todo_version_count","orphanTodoVersionCount"))>0)readiness="RUNTIME_INVALID";
        else readiness="READY";
        return new TodoHistoricalMigrationRequirementView(number(value(row,"requirement_id","requirementId")),
                text(value(row,"gate_code","gateCode")),text(value(row,"requirement_code","requirementCode")),
                text(value(row,"requirement_name","requirementName")),kind,source,text(value(row,"source_ref","sourceRef")),
                text(value(row,"decision_ref","decisionRef")),readiness,text(value(row,"remark","remark")));
    }

    private int count(List<TodoHistoricalMigrationRequirementView> rows,String status){return (int)rows.stream().filter(row->status.equals(row.readinessStatus())).count();}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
    private boolean truth(Object value){return value instanceof Boolean flag?flag:value!=null&&Integer.parseInt(String.valueOf(value))!=0;}
}

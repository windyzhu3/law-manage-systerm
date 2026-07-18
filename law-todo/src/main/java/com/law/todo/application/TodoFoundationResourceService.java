package com.law.todo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.TodoFoundationResourceReadinessView;
import com.law.todo.application.view.TodoFoundationResourceView;
import com.law.todo.mapper.TodoFoundationResourceMapper;

@Service
public class TodoFoundationResourceService
{
    private final TodoFoundationResourceMapper mapper;
    public TodoFoundationResourceService(TodoFoundationResourceMapper mapper){this.mapper=mapper;}

    @Transactional(readOnly=true)
    public TodoFoundationResourceReadinessView readiness(String gateCode)
    {
        List<TodoFoundationResourceView> resources=mapper.selectResourceReadiness(gateCode).stream().map(this::view).toList();
        int ready=count(resources,"READY");
        int sourceUnresolved=count(resources,"SOURCE_UNRESOLVED");
        int runtimeMissing=count(resources,"RUNTIME_MISSING");
        int runtimeIncomplete=count(resources,"RUNTIME_INCOMPLETE");
        return new TodoFoundationResourceReadinessView(gateCode,resources.size(),ready,sourceUnresolved,runtimeMissing,
                runtimeIncomplete,!resources.isEmpty()&&ready==resources.size(),List.copyOf(resources));
    }

    @Transactional(readOnly=true)
    public boolean gateReady(String gateCode){return readiness(gateCode).gateReady();}

    private int count(List<TodoFoundationResourceView> rows,String status)
    {
        return (int)rows.stream().filter(row->status.equals(row.readinessStatus())).count();
    }

    private TodoFoundationResourceView view(Map<String,Object> row)
    {
        String sourceStatus=text(value(row,"source_status","sourceStatus"));
        boolean exists=truth(value(row,"resource_exists","resourceExists"));
        int active=integer(value(row,"active_item_count","activeItemCount"));
        int expected=integer(value(row,"expected_item_count","expectedItemCount"));
        int matched=integer(value(row,"matched_expected_item_count","matchedExpectedItemCount"));
        int minimum=integer(value(row,"minimum_active_items","minimumActiveItems"));
        String readiness=!"CONFIRMED".equals(sourceStatus)?"SOURCE_UNRESOLVED":!exists?"RUNTIME_MISSING":
                (active<minimum||matched<expected)?"RUNTIME_INCOMPLETE":"READY";
        return new TodoFoundationResourceView(number(value(row,"resource_id","resourceId")),text(value(row,"gate_code","gateCode")),
                text(value(row,"resource_type","resourceType")),text(value(row,"resource_code","resourceCode")),
                text(value(row,"domain_code","domainCode")),text(value(row,"delivery_phase","deliveryPhase")),
                text(value(row,"source_ref","sourceRef")),sourceStatus,text(value(row,"decision_ref","decisionRef")),
                text(value(row,"expected_values_json","expectedValuesJson")),minimum,exists,active,expected,matched,readiness,
                text(value(row,"remark","remark")));
    }

    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
    private boolean truth(Object value){return value instanceof Boolean flag?flag:value!=null&&Integer.parseInt(String.valueOf(value))!=0;}
}

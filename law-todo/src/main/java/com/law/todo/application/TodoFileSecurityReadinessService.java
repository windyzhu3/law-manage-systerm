package com.law.todo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.TodoFileSecurityReadinessView;
import com.law.todo.application.view.TodoFileSecurityRequirementView;
import com.law.todo.mapper.TodoFileSecurityReadinessMapper;

@Service
public class TodoFileSecurityReadinessService
{
    private final TodoFileSecurityReadinessMapper mapper;
    public TodoFileSecurityReadinessService(TodoFileSecurityReadinessMapper mapper){this.mapper=mapper;}

    @Transactional(readOnly=true)
    public TodoFileSecurityReadinessView readiness(String gateCode)
    {
        List<Map<String,Object>> rows=mapper.selectSecurityReadiness(gateCode);
        List<TodoFileSecurityRequirementView> requirements=rows.stream().map(this::view).toList();
        int ready=count(requirements,"READY"),source=count(requirements,"SOURCE_UNRESOLVED"),missing=count(requirements,"RUNTIME_MISSING");
        Map<String,Object> state=rows.isEmpty()?Map.of():rows.get(0);
        boolean model=integer(value(state,"object_model_table_count","objectModelTableCount"))==integer(value(state,"expected_object_model_table_count","expectedObjectModelTableCount"));
        boolean token=integer(value(state,"token_security_column_count","tokenSecurityColumnCount"))==4&&integer(value(state,"token_unique_index_count","tokenUniqueIndexCount"))==1;
        boolean audit=truth(value(state,"access_log_table_exists","accessLogTableExists"));
        boolean cleanup=truth(value(state,"cleanup_table_exists","cleanupTableExists"));
        return new TodoFileSecurityReadinessView(gateCode,requirements.size(),ready,source,missing,!requirements.isEmpty()&&ready==requirements.size(),model,token,audit,cleanup,List.copyOf(requirements));
    }

    @Transactional(readOnly=true)
    public boolean gateReady(String gateCode){return readiness(gateCode).gateReady();}

    private TodoFileSecurityRequirementView view(Map<String,Object> row)
    {
        String source=text(value(row,"source_status","sourceStatus")),kind=text(value(row,"check_kind","checkKind"));
        boolean runtime=switch(kind){
            case "OBJECT_MODEL"->integer(value(row,"object_model_table_count","objectModelTableCount"))==integer(value(row,"expected_object_model_table_count","expectedObjectModelTableCount"));
            case "TOKEN_CONTROL"->integer(value(row,"token_security_column_count","tokenSecurityColumnCount"))==4&&integer(value(row,"token_unique_index_count","tokenUniqueIndexCount"))==1;
            case "ACCESS_AUDIT"->truth(value(row,"access_log_table_exists","accessLogTableExists"));
            case "CLEANUP_COMPENSATION"->truth(value(row,"cleanup_table_exists","cleanupTableExists"));
            default->true;
        };
        String readiness=!"CONFIRMED".equals(source)?"SOURCE_UNRESOLVED":runtime?"READY":"RUNTIME_MISSING";
        return new TodoFileSecurityRequirementView(number(value(row,"requirement_id","requirementId")),text(value(row,"gate_code","gateCode")),
                text(value(row,"requirement_code","requirementCode")),text(value(row,"requirement_name","requirementName")),kind,source,
                text(value(row,"source_ref","sourceRef")),readiness,text(value(row,"remark","remark")));
    }
    private int count(List<TodoFileSecurityRequirementView> rows,String status){return (int)rows.stream().filter(row->status.equals(row.readinessStatus())).count();}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
    private boolean truth(Object value){return value instanceof Boolean flag?flag:value!=null&&Integer.parseInt(String.valueOf(value))!=0;}
}

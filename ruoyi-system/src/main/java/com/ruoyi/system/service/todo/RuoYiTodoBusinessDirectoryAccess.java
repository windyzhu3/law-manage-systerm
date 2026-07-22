package com.ruoyi.system.service.todo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.spi.TodoBusinessDirectoryAccess;
import com.ruoyi.system.mapper.TodoBusinessDirectoryMapper;

/** SQL-backed directory whose rows and total are both calculated inside the actor's data scope. */
@Component
public class RuoYiTodoBusinessDirectoryAccess implements TodoBusinessDirectoryAccess
{
    private static final Map<String,String> PERMISSIONS=Map.of(
            "CUSTOMER","customer:list,customer:query",
            "CONTRACT","contract:list,contract:query",
            "CASE","case:list,case:query",
            "MATTER","matter:list,matter:query,matter:mine:list,matter:mine:query");
    private static final List<String> TYPES=List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER");
    private final TodoBusinessDirectoryMapper mapper;

    public RuoYiTodoBusinessDirectoryAccess(TodoBusinessDirectoryMapper mapper){this.mapper=mapper;}

    @Override public boolean supports(String businessType){return TYPES.contains(businessType);}

    @Override public DirectoryPage search(String type,String keyword,int offset,int limit,Actor actor)
    {
        Map<String,Object> query=query(type,actor);query.put("keyword",keyword);query.put("offset",offset);query.put("limit",limit);
        List<DirectoryEntry> rows=mapper.selectVisibleBusinessObjects(query).stream().map(this::entry).toList();
        long total=mapper.countVisibleBusinessObjects(query);if(total>0||!rows.isEmpty())return new DirectoryPage(rows,total,null,"BUSINESS_DATA");
        Map<String,Object> unscoped=new LinkedHashMap<>(query);unscoped.put("dataScope",false);
        long matching=mapper.countUnscopedBusinessObjects(unscoped);String reason;
        if(matching>0)reason="NO_PERMISSION";
        else
        {
            unscoped.remove("keyword");unscoped.remove("businessId");
            reason=mapper.countUnscopedBusinessObjects(unscoped)>0?"NO_MATCH":"NO_DATA";
        }
        return new DirectoryPage(rows,0,reason,"BUSINESS_DATA");
    }

    @Override public Optional<DirectoryEntry> findVisible(String type,Long id,Actor actor)
    {
        Map<String,Object> query=query(type,actor);query.put("businessId",id);
        Map<String,Object> row=mapper.selectVisibleBusinessObject(query);
        return row==null||row.isEmpty()?Optional.empty():Optional.of(entry(row));
    }

    private Map<String,Object> query(String type,Actor actor)
    {
        Map<String,Object> query=new LinkedHashMap<>();query.put("businessType",type);query.put("currentUserId",actor.userId());
        query.put("currentDeptId",actor.deptId());query.put("dataScope",!Long.valueOf(1L).equals(actor.userId()));
        query.put("permissions",PERMISSIONS.get(type));return query;
    }

    private DirectoryEntry entry(Map<String,Object> row)
    {return new DirectoryEntry(number(row,"business_id","businessId"),text(row,"business_no","businessNo"),
            text(row,"business_name","businessName"),text(row,"business_type","businessType"));}
    private long number(Map<String,Object> row,String snake,String camel){return Long.parseLong(String.valueOf(row.containsKey(snake)?row.get(snake):row.get(camel)));}
    private String text(Map<String,Object> row,String snake,String camel){Object value=row.containsKey(snake)?row.get(snake):row.get(camel);return value==null?null:String.valueOf(value);}
}

package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.law.todo.application.view.TodoConfigurationViews.ConfigurationDashboard;
import com.law.todo.application.view.TodoConfigurationViews.ReleaseRecord;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Read-only projections for the configuration centre; version and action rows remain authoritative. */
@Service
public class TodoConfigurationQueryService
{
    private final TodoConfigurationMapper mapper;

    public TodoConfigurationQueryService(TodoConfigurationMapper mapper){this.mapper=mapper;}

    public ConfigurationDashboard dashboard()
    {return new ConfigurationDashboard(mapper.countPublishedTemplates(),mapper.countDraftTemplates(),
            mapper.countEnabledSlaRules(),mapper.countTodayTriggeredTodos());}

    public TemplateConfigurationDetail template(Long id)
    {
        Map<String,Object> row=require(mapper.selectTemplateConfiguration(id));
        Long draft=number(row,"draft_version_id","draftVersionId");
        return new TemplateConfigurationDetail(requiredNumber(row,"template_id","templateId"),text(row,"template_code","templateCode"),
                text(row,"template_name","templateName"),text(row,"business_type","businessType"),
                integer(row,"current_version","currentVersion"),draft,text(row,"draft_status","draftStatus"),
                draft==null?List.of():mapper.selectDraftRuleRefs(draft));
    }

    public List<ReleaseRecord> releases(Map<String,Object> query)
    {return mapper.selectReleaseRecords(query==null?Map.of():query).stream().map(this::release).toList();}

    private ReleaseRecord release(Map<String,Object> row)
    {
        Map<String,Object> action=new LinkedHashMap<>();
        copy(row,action,"action_id","actionId");copy(row,action,"action_type","actionType");
        copy(row,action,"operator_id","operatorId");copy(row,action,"operator_name","operatorName");
        copy(row,action,"action_create_time","createTime");
        return new ReleaseRecord(requiredNumber(row,"version_id","versionId"),requiredNumber(row,"template_id","templateId"),
                text(row,"template_code","templateCode"),text(row,"template_name","templateName"),
                integer(row,"version_no","versionNo"),text(row,"status","status"),text(row,"change_summary","changeSummary"),
                text(row,"impact_scope","impactScope"),number(row,"rollback_source_version_id","rollbackSourceVersionId"),
                text(row,"published_by","publishedBy"),time(row,"published_time","publishedTime"),time(row,"update_time","updateTime"),action);
    }

    private Map<String,Object> require(Map<String,Object> row)
    {if(row==null||row.isEmpty())throw new TodoException("TODO_TEMPLATE_NOT_FOUND","Todo template not found");return row;}
    private long requiredNumber(Map<String,Object> row,String snake,String camel)
    {Long value=number(row,snake,camel);if(value==null)throw new TodoException("TODO_TEMPLATE_NOT_FOUND","Todo template not found");return value;}
    private Long number(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);return value==null?null:Long.valueOf(String.valueOf(value));}
    private Integer integer(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);return value==null?null:Integer.valueOf(String.valueOf(value));}
    private String text(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);return value==null?null:String.valueOf(value);}
    private LocalDateTime time(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);if(value instanceof LocalDateTime time)return time;if(value instanceof java.sql.Timestamp timestamp)return timestamp.toLocalDateTime();return null;}
    private Object value(Map<String,Object> row,String snake,String camel)
    {return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private void copy(Map<String,Object> row,Map<String,Object> target,String snake,String camel)
    {Object value=value(row,snake,camel);if(value!=null)target.put(camel,value);}
}

package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.law.todo.application.view.TodoConfigurationViews.ConfigurationDashboard;
import com.law.todo.application.view.TodoConfigurationViews.ReleaseRecord;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.application.view.TodoConfigurationViews.TemplateListItem;
import com.law.todo.application.view.TodoConfigurationViews.TemplatePage;
import com.law.todo.application.view.TodoConfigurationViews.TemplateRuleReference;
import com.law.todo.application.view.TodoConfigurationViews.TemplateVersionDetail;
import com.law.todo.application.view.TodoConfigurationViews.OwnerCatalogEntry;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Read-only projections for the configuration centre; version and action rows remain authoritative. */
@Service
public class TodoConfigurationQueryService
{
    private static final int DEFAULT_RELEASE_LIMIT=100;
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
                text(row,"status","status"),integer(row,"version","version"),integer(row,"current_version","currentVersion"),
                draft,text(row,"draft_status","draftStatus"),number(row,"published_version_id","publishedVersionId"),
                integer(row,"published_version_no","publishedVersionNo"),version(row),
                draft==null?List.of():mapper.selectDraftRuleRefs(draft).stream().map(this::ruleReference).toList());
    }

    public TemplatePage templatePage(Map<String,Object> query)
    {
        Map<String,Object> normalized=new LinkedHashMap<>(query==null?Map.of():query);
        int offset=paginationInteger(normalized.get("offset"),"offset")==null?0:paginationInteger(normalized.get("offset"),"offset");
        int limit=paginationInteger(normalized.get("limit"),"limit")==null?20:paginationInteger(normalized.get("limit"),"limit");
        if(offset<0||limit<=0||limit>200)throw invalidTemplatePagination();
        normalized.put("offset",offset);normalized.put("limit",limit);
        List<TemplateListItem> rows=mapper.selectTemplateConfigurations(normalized).stream().map(this::templateItem).toList();
        return new TemplatePage(rows,mapper.countTemplateConfigurations(normalized));
    }

    public List<OwnerCatalogEntry> ownerCatalog()
    {return mapper.selectTemplateOwnerCatalog().stream().map(row->new OwnerCatalogEntry(text(row,"type","type"),
            text(row,"value","value"),text(row,"label","label"),text(row,"secondary_label","secondaryLabel"))).toList();}

    public List<ReleaseRecord> releases(Map<String,Object> query)
    {
        Map<String,Object> normalized=new LinkedHashMap<>(query==null?Map.of():query);
        Integer offset=paginationInteger(normalized.get("offset"),"offset");
        Integer limit=paginationInteger(normalized.get("limit"),"limit");
        if(offset!=null)
        {
            if(offset<0)throw invalidPagination("offset must be zero or greater");
            normalized.put("offset",offset);
        }
        if(limit!=null)
        {
            if(limit<=0)throw invalidPagination("limit must be greater than zero");
            normalized.put("limit",limit);
        }
        else if(offset!=null)normalized.put("limit",DEFAULT_RELEASE_LIMIT);
        return mapper.selectReleaseRecords(normalized).stream().map(this::release).toList();
    }

    public ReleaseRecord release(long versionId)
    {return release(require(mapper.selectReleaseRecord(versionId)));}
    public ReleasePage releasePage(Map<String,Object> query){return new ReleasePage(releases(query),mapper.countReleaseRecords(query==null?Map.of():query));}
    public record ReleasePage(List<ReleaseRecord> rows,long total) { }

    private Integer paginationInteger(Object value,String field)
    {
        if(value==null)return null;
        try{return Integer.valueOf(String.valueOf(value));}
        catch(NumberFormatException invalid){throw invalidPagination(field+" must be an integer");}
    }
    private TodoException invalidPagination(String detail)
    {return new TodoException("TODO_CONFIGURATION_QUERY_INVALID","Release pagination "+detail);}

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

    private TemplateListItem templateItem(Map<String,Object> row)
    {return new TemplateListItem(requiredNumber(row,"template_id","templateId"),text(row,"template_code","templateCode"),
            text(row,"template_name","templateName"),text(row,"business_type","businessType"),text(row,"status","status"),
            integer(row,"version","version"),text(row,"business_stage","businessStage"),text(row,"template_type","templateType"),
            text(row,"priority","priority"),text(row,"publish_status","publishStatus"),number(row,"draft_version_id","draftVersionId"),
            integer(row,"draft_version_no","draftVersionNo"),number(row,"published_version_id","publishedVersionId"),
            integer(row,"published_version_no","publishedVersionNo"),text(row,"event_type","eventType"),
            text(row,"owner_summary","ownerSummary"),time(row,"update_time","updateTime"));}

    private TemplateRuleReference ruleReference(Map<String,Object> row)
    {return new TemplateRuleReference(text(row,"ref_type","refType"),number(row,"ref_id_value","refIdValue"),
            integer(row,"sort_order","sortOrder"),text(row,"rule_code","ruleCode"),text(row,"rule_name","ruleName"),
            text(row,"rule_status","ruleStatus"),text(row,"config_json","configJson"));}

    private TemplateVersionDetail version(Map<String,Object> row)
    {
        Long id=number(row,"detail_version_id","detailVersionId");if(id==null)return null;
        return new TemplateVersionDetail(id,integer(row,"detail_version_no","detailVersionNo"),text(row,"detail_version_status","detailVersionStatus"),
                number(row,"detail_source_version_id","detailSourceVersionId"),integer(row,"detail_definition_schema_version","detailDefinitionSchemaVersion"),
                text(row,"detail_definition_json","detailDefinitionJson"),text(row,"detail_owner_rule_json","detailOwnerRuleJson"),
                text(row,"detail_dod_rule_json","detailDodRuleJson"),text(row,"detail_sla_rule_json","detailSlaRuleJson"),
                text(row,"detail_next_rule_json","detailNextRuleJson"),text(row,"detail_ui_schema_json","detailUiSchemaJson"),
                text(row,"detail_definition_hash","detailDefinitionHash"),text(row,"detail_validation_report_json","detailValidationReportJson"),
                text(row,"detail_change_summary","detailChangeSummary"),text(row,"detail_impact_scope","detailImpactScope"),
                number(row,"detail_rollback_source_version_id","detailRollbackSourceVersionId"),text(row,"detail_published_by","detailPublishedBy"),
                time(row,"detail_published_time","detailPublishedTime"),time(row,"detail_create_time","detailCreateTime"));
    }

    private TodoException invalidTemplatePagination()
    {return new TodoException("TODO_CONFIGURATION_QUERY_INVALID","Template pagination must use offset >= 0 and 1 <= limit <= 200");}

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

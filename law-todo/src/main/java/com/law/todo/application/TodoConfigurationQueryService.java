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
import com.law.todo.application.view.TodoConfigurationViews.BusinessObjectItem;
import com.law.todo.application.view.TodoConfigurationViews.BusinessObjectPage;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Read-only projections for the configuration centre; version and action rows remain authoritative. */
@Service
public class TodoConfigurationQueryService
{
    private static final int DEFAULT_RELEASE_LIMIT=20;
    private static final int MAX_RELEASE_LIMIT=500;
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
        offset=offset==null?0:offset;limit=limit==null?DEFAULT_RELEASE_LIMIT:limit;
        if(offset<0)throw invalidPagination("offset must be zero or greater");
        if(limit<=0)throw invalidPagination("limit must be greater than zero");
        normalized.put("offset",offset);normalized.put("limit",Math.min(limit,MAX_RELEASE_LIMIT));
        return mapper.selectReleaseRecords(normalized).stream().map(this::release).toList();
    }

    public ReleaseRecord release(long versionId)
    {return release(require(mapper.selectReleaseRecord(versionId)));}
    public List<TemplateVersionDetail> releaseVersions(long releaseVersionId)
    {
        ReleaseRecord source=release(releaseVersionId);
        return mapper.selectImmutableTemplateVersions(source.templateId()).stream()
                .filter(row->java.util.Set.of("PUBLISHED","RETIRED").contains(text(row,"status","status")))
                .map(this::immutableVersion).toList();
    }
    public ReleasePage releasePage(Map<String,Object> query){return new ReleasePage(releases(query),mapper.countReleaseRecords(query==null?Map.of():query));}
    public record ReleasePage(List<ReleaseRecord> rows,long total) { }

    public BusinessObjectPage businessObjects(String businessType,String keyword,int pageNum,int pageSize)
    {
        requireBusinessType(businessType);
        if(pageNum<1||pageSize<1||pageSize>100)throw invalidPagination("business object page is out of range");
        Map<String,Object> query=new LinkedHashMap<>();query.put("businessType",businessType);query.put("keyword",keyword);
        query.put("offset",(pageNum-1)*pageSize);query.put("limit",pageSize);
        return new BusinessObjectPage(mapper.selectBusinessObjects(query).stream().map(this::businessObject).toList(),
                mapper.countBusinessObjects(query));
    }

    public BusinessObjectItem requireBusinessObject(String businessType,Long businessId)
    {
        requireBusinessType(businessType);
        Map<String,Object> row=businessId==null?null:mapper.selectBusinessObject(businessType,businessId);
        if(row==null||row.isEmpty())throw new TodoException("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND","Simulation business object does not exist");
        return businessObject(row);
    }

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

    private BusinessObjectItem businessObject(Map<String,Object> row)
    {return new BusinessObjectItem(requiredBusinessNumber(row),text(row,"business_no","businessNo"),
            text(row,"business_name","businessName"),text(row,"business_type","businessType"));}

    private long requiredBusinessNumber(Map<String,Object> row)
    {Long value=number(row,"business_id","businessId");if(value==null)throw new TodoException("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND","Simulation business object does not exist");return value;}

    private void requireBusinessType(String businessType)
    {
        if(!java.util.Set.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER").contains(businessType))
            throw new TodoException("TODO_SIMULATION_BUSINESS_TYPE_UNSUPPORTED","Unsupported simulation business type");
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

    private TemplateVersionDetail immutableVersion(Map<String,Object> row)
    {
        return new TemplateVersionDetail(number(row,"version_id","versionId"),integer(row,"version_no","versionNo"),
                text(row,"status","status"),number(row,"source_version_id","sourceVersionId"),
                integer(row,"definition_schema_version","definitionSchemaVersion"),text(row,"definition_json","definitionJson"),
                text(row,"owner_rule_json","ownerRuleJson"),text(row,"dod_rule_json","dodRuleJson"),
                text(row,"sla_rule_json","slaRuleJson"),text(row,"next_rule_json","nextRuleJson"),
                text(row,"ui_schema_json","uiSchemaJson"),text(row,"definition_hash","definitionHash"),
                text(row,"validation_report_json","validationReportJson"),text(row,"change_summary","changeSummary"),
                text(row,"impact_scope","impactScope"),number(row,"rollback_source_version_id","rollbackSourceVersionId"),
                text(row,"published_by","publishedBy"),time(row,"published_time","publishedTime"),time(row,"create_time","createTime"));
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

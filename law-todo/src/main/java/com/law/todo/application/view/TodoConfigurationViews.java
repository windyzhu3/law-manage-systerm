package com.law.todo.application.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class TodoConfigurationViews
{
    private TodoConfigurationViews() { }

    public record ConfigurationDashboard(long publishedTemplateCount,long draftTemplateCount,long enabledSlaRuleCount,
            long todayTriggeredTodoCount) { }

    public record SlaRuleListItem(long slaRuleId,String ruleCode,String ruleName,String slaType,Integer durationValue,
            String durationUnit,String calendarCode,String startStrategy,String status,Integer version,
            long referenceCount,LocalDateTime updateTime) { }

    public record SlaRuleDetail(long slaRuleId,String ruleCode,String ruleName,String slaType,Integer durationValue,
            String durationUnit,String calendarCode,String startStrategy,Integer softRemindPercent,
            Integer hardRemindPercent,Integer escalatePercent,String pausePolicyJson,String escalationPolicyJson,
            String autoActionJson,String status,Integer version,long referenceCount,String createBy,
            LocalDateTime createTime,String updateBy,LocalDateTime updateTime) { }

    public record SlaCalculationResult(LocalDateTime createdAt,LocalDateTime remind80At,LocalDateTime overdue100At,
            LocalDateTime escalate150At) { }
    public record SlaJourneyCalculationResult(long minutes,LocalDateTime createdAt,LocalDateTime remind80At,
            LocalDateTime overdue100At,LocalDateTime escalate150At) { }

    public record DodRuleListItem(long dodRuleId,String ruleCode,String ruleName,String ruleType,String businessType,String status,
            Integer version,long referenceCount,LocalDateTime updateTime,String requiredFieldsJson,
            String requiredAttachmentsJson,String conditionalRulesJson) { }

    public record DodRuleDetail(long dodRuleId,String ruleCode,String ruleName,String ruleType,String businessType,
            String requiredFieldsJson,String requiredAttachmentsJson,String conditionalRulesJson,String validatorRefsJson,
            String errorMessagesJson,String status,Integer version,long referenceCount,String createBy,
            LocalDateTime createTime,String updateBy,LocalDateTime updateTime) { }

    public record TemplateListItem(long templateId,String templateCode,String templateName,String businessType,String status,
            Integer version,String businessStage,String templateType,String priority,String publishStatus,
            Long draftVersionId,Integer draftVersionNo,Long publishedVersionId,Integer publishedVersionNo,
            String eventType,String ownerSummary,LocalDateTime updateTime) { }

    public record TemplatePage(List<TemplateListItem> rows,long total)
    { public TemplatePage { rows=rows==null?List.of():List.copyOf(rows); } }

    public record TemplateRuleReference(String type,Long id,Integer order,String ruleCode,String ruleName,
            String ruleStatus,String configJson) { }

    public record TemplateVersionDetail(Long versionId,Integer versionNo,String status,Long sourceVersionId,
            Integer definitionSchemaVersion,String definitionJson,String ownerRuleJson,String dodRuleJson,
            String slaRuleJson,String nextRuleJson,String uiSchemaJson,String definitionHash,
            String validationReportJson,String changeSummary,String impactScope,Long rollbackSourceVersionId,
            String publishedBy,LocalDateTime publishedTime,LocalDateTime createTime) { }

    public record OwnerCatalogEntry(String type,String value,String label,String secondaryLabel) { }
    public record EventCatalogEntry(String eventType,Integer payloadVersion,String businessObjectType,
            String payloadSchemaJson,String status) { }
    public record RoutingTargetCatalogEntry(Long templateId,String templateCode,String templateName,String businessType,
            Long versionId,Integer versionNo,String status) { }

    public record TemplateConfigurationDetail(long templateId,String templateCode,String templateName,String businessType,
            String status,Integer version,Integer currentVersion,Long draftVersionId,String draftStatus,
            Long publishedVersionId,Integer publishedVersionNo,TemplateVersionDetail editableVersion,
            List<TemplateRuleReference> ruleReferences)
    {
        public TemplateConfigurationDetail
        {
            ruleReferences=ruleReferences==null?List.of():List.copyOf(ruleReferences);
        }
    }

    public record ReleaseRecord(long versionId,long templateId,String templateCode,String templateName,Integer versionNo,
            String status,String changeSummary,String impactScope,Long rollbackSourceVersionId,String publishedBy,
            LocalDateTime publishedTime,LocalDateTime updateTime,Map<String,Object> action)
    {
        public ReleaseRecord
        {
            action=immutableMap(action);
        }
    }

    public record BusinessObjectItem(long businessId,String businessNo,String businessName,String businessType,String source,boolean sample)
    {public BusinessObjectItem(long businessId,String businessNo,String businessName,String businessType)
        {this(businessId,businessNo,businessName,businessType,"BUSINESS_DATA",false);}}
    public record BusinessObjectPage(List<BusinessObjectItem> rows,long total,String emptyReason,boolean sampleFallback)
    { public BusinessObjectPage { rows=rows==null?List.of():List.copyOf(rows); }
      public BusinessObjectPage(List<BusinessObjectItem> rows,long total){this(rows,total,null,false);} }

    public record ConfigurationSimulationResult(TodoSimulationView simulation,long durationMs) { }

    public record JourneyImpact(List<String> changedPaths,List<String> affectedSteps,
            List<String> invalidatedEvidence,String message)
    {
        public JourneyImpact
        {
            changedPaths=changedPaths==null?List.of():List.copyOf(changedPaths);
            affectedSteps=affectedSteps==null?List.of():List.copyOf(affectedSteps);
            invalidatedEvidence=invalidatedEvidence==null?List.of():List.copyOf(invalidatedEvidence);
        }
        public static JourneyImpact none()
        {return new JourneyImpact(List.of(),List.of(),List.of(),"本次保存未改变配置内容。");}
    }
    public record JourneySaveResult(TodoConfigurationJourneyView journey,JourneyImpact impact)
    {
        public JourneySaveResult
        {impact=impact==null?JourneyImpact.none():impact;}
    }

    public record PublishedSimulationDiagnostic(long versionId,long templateId,String templateCode,
            String templateName,String eventType,Integer payloadVersion,String businessType,String status,
            String ownerStatus,List<String> issueCodes,String message,TodoSimulationView simulation)
    { public PublishedSimulationDiagnostic { issueCodes=issueCodes==null?List.of():List.copyOf(issueCodes); } }

    public record PublishedSimulationDiagnosticSummary(int total,int passed,int warning,int failed,
            List<PublishedSimulationDiagnostic> items,LocalDateTime generatedAt)
    { public PublishedSimulationDiagnosticSummary { items=items==null?List.of():List.copyOf(items); } }

    private static Map<String,Object> immutableMap(Map<String,Object> source)
    {
        if(source==null)return Map.of();
        Map<String,Object> copy=new java.util.LinkedHashMap<>();
        source.forEach((key,value)->copy.put(key,immutableValue(value)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value)
    {
        if(value instanceof Map<?,?> map)
        {
            Map<Object,Object> copy=new java.util.LinkedHashMap<>();
            map.forEach((key,nested)->copy.put(key,immutableValue(nested)));
            return java.util.Collections.unmodifiableMap(copy);
        }
        if(value instanceof List<?> list)
            return java.util.Collections.unmodifiableList(list.stream().map(TodoConfigurationViews::immutableValue).toList());
        return value;
    }
}

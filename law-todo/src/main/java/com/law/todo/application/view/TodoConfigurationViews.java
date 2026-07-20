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

    public record DodRuleListItem(long dodRuleId,String ruleCode,String ruleName,String ruleType,String status,
            Integer version,long referenceCount,LocalDateTime updateTime) { }

    public record DodRuleDetail(long dodRuleId,String ruleCode,String ruleName,String ruleType,
            String requiredFieldsJson,String requiredAttachmentsJson,String conditionalRulesJson,String validatorRefsJson,
            String errorMessagesJson,String status,Integer version,long referenceCount,String createBy,
            LocalDateTime createTime,String updateBy,LocalDateTime updateTime) { }

    public record TemplateConfigurationDetail(long templateId,String templateCode,String templateName,String businessType,
            Integer currentVersion,Long draftVersionId,String draftStatus,List<Map<String,Object>> ruleReferences)
    {
        public TemplateConfigurationDetail
        {
            ruleReferences=ruleReferences==null?List.of():ruleReferences.stream()
                    .map(TodoConfigurationViews::immutableMap).toList();
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

    public record ConfigurationSimulationResult(TodoSimulationView simulation,long durationMs) { }

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

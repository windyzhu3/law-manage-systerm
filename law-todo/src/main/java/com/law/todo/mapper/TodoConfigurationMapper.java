package com.law.todo.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface TodoConfigurationMapper
{
    int countEnabledDictionaryValue(@Param("dictType") String dictType,@Param("dictValue") String dictValue);
    List<Map<String,Object>> selectSlaRules(Map<String,Object> query);
    Map<String,Object> selectSlaRule(Long id);
    int insertSlaRule(Map<String,Object> row);
    int updateSlaRuleConditionally(Map<String,Object> row);
    int countSlaRuleReferences(Long id);
    List<Map<String,Object>> selectDodRules(Map<String,Object> query);
    Map<String,Object> selectDodRule(Long id);
    int insertDodRule(Map<String,Object> row);
    int updateDodRuleConditionally(Map<String,Object> row);
    int updateDodRuleStatusConditionally(Map<String,Object> row);
    int countDodRuleReferences(Long id);
    long countPublishedTemplates();
    long countDraftTemplates();
    long countEnabledSlaRules();
    long countTodayTriggeredTodos();
    int deleteDraftRuleRefs(Long versionId);
    int insertDraftRuleRef(Map<String,Object> row);
    List<Map<String,Object>> selectDraftRuleRefs(Long versionId);
    Map<String,Object> selectTemplateConfiguration(Long templateId);
    List<Map<String,Object>> selectTemplateConfigurations(Map<String,Object> query);
    long countTemplateConfigurations(Map<String,Object> query);
    List<Map<String,Object>> selectTemplateOwnerCatalog();
    List<Map<String,Object>> selectReleaseRecords(Map<String,Object> query);
    long countReleaseRecords(Map<String,Object> query);
    Map<String,Object> selectReleaseRecord(Long versionId);
    List<Map<String,Object>> selectImmutableTemplateVersions(Long templateId);
    int insertSimulationRecord(Map<String,Object> row);
}

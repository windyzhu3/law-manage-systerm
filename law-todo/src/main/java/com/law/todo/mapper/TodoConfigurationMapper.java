package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

public interface TodoConfigurationMapper
{
    List<Map<String,Object>> selectSlaRules(Map<String,Object> query);
    Map<String,Object> selectSlaRule(Long id);
    int insertSlaRule(Map<String,Object> row);
    int updateSlaRuleConditionally(Map<String,Object> row);
    int countSlaRuleReferences(Long id);
    List<Map<String,Object>> selectDodRules(Map<String,Object> query);
    Map<String,Object> selectDodRule(Long id);
    int insertDodRule(Map<String,Object> row);
    int updateDodRuleConditionally(Map<String,Object> row);
    int countDodRuleReferences(Long id);
    int deleteDraftRuleRefs(Long versionId);
    int insertDraftRuleRef(Map<String,Object> row);
    List<Map<String,Object>> selectDraftRuleRefs(Long versionId);
    Map<String,Object> selectTemplateConfiguration(Long templateId);
    List<Map<String,Object>> selectReleaseRecords(Map<String,Object> query);
    int insertSimulationRecord(Map<String,Object> row);
}

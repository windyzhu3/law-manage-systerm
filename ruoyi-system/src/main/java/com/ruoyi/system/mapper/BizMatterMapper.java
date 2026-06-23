package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BizMatterMapper
{
    List<Map<String, Object>> selectDashboardCards(Map<String, Object> params);

    List<Map<String, Object>> selectCaseTypeStats(Map<String, Object> params);

    List<Map<String, Object>> selectReminders(Map<String, Object> params);

    List<Map<String, Object>> selectMatterList(Map<String, Object> params);

    Map<String, Object> selectMatterById(Long caseId);

    Map<String, Object> selectMatterByContractId(Long contractId);

    int insertMatter(Map<String, Object> matter);

    int updateMatter(Map<String, Object> matter);

    int updateMatterStatus(Map<String, Object> matter);

    int countMatterInDataScope(@Param("caseId") Long caseId, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("permissions") String permissions);

    int countUnfinishedNodes(Long caseId);

    Map<String, Object> selectCurrentOpenNode(Long caseId);

    int countExpenseByCaseId(Long caseId);

    int countUnpaidExpenseByCaseId(Long caseId);

    List<Map<String, Object>> selectFieldValues(Long caseId);

    List<Map<String, Object>> selectFieldConfigs(String caseType);

    int deleteFieldValues(Long caseId);

    int insertFieldValue(Map<String, Object> field);

    List<Map<String, Object>> selectProgressList(Map<String, Object> params);

    Map<String, Object> selectProgressById(Long progressId);

    int insertProgress(Map<String, Object> progress);

    int updateProgress(Map<String, Object> progress);

    int deleteProgress(@Param("progressId") Long progressId, @Param("updateBy") String updateBy);

    List<Map<String, Object>> selectNodeList(Map<String, Object> params);

    Map<String, Object> selectNodeById(Long nodeId);

    int insertNode(Map<String, Object> node);

    int updateNode(Map<String, Object> node);

    int deleteNode(@Param("nodeId") Long nodeId, @Param("updateBy") String updateBy);

    List<Map<String, Object>> selectNodeMaterials(Long nodeId);

    int deleteNodeMaterials(Long nodeId);

    int insertNodeMaterial(Map<String, Object> material);

    List<Map<String, Object>> selectExpenseList(Map<String, Object> params);

    Map<String, Object> selectExpenseById(Long expenseId);

    int insertExpense(Map<String, Object> expense);

    int updateExpense(Map<String, Object> expense);

    int deleteExpense(@Param("expenseId") Long expenseId, @Param("updateBy") String updateBy);

    List<Map<String, Object>> selectDocumentList(Map<String, Object> params);

    Map<String, Object> selectDocumentById(Long documentId);

    int insertDocument(Map<String, Object> document);

    int deleteDocument(@Param("documentId") Long documentId, @Param("updateBy") String updateBy);

    Map<String, Object> selectArchiveByCaseId(Long caseId);

    int insertArchive(Map<String, Object> archive);

    int updateArchive(Map<String, Object> archive);

    int insertArchiveMaterial(Map<String, Object> material);

    int deleteArchiveMaterials(Long archiveId);

    List<Map<String, Object>> selectArchiveMaterials(Long archiveId);

    int countArchiveMaterials(Long archiveId);

    int countNotReadyArchiveMaterials(Long archiveId);

    List<Map<String, Object>> selectStatusLogs(Map<String, Object> params);

    int insertStatusLog(Map<String, Object> log);
}

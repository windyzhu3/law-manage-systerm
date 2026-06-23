package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;

public interface IBizMatterService
{
    Map<String, Object> selectDashboard();

    List<Map<String, Object>> selectMatterList(Map<String, Object> params);

    Map<String, Object> selectMatterDetail(Long caseId);

    List<Map<String, Object>> selectFieldConfigs(String caseType);

    int insertMatter(Map<String, Object> matter);

    int updateMatter(Map<String, Object> matter);

    List<Map<String, Object>> selectProgressList(Map<String, Object> params);

    int insertProgress(Map<String, Object> progress);

    int updateProgress(Map<String, Object> progress);

    int deleteProgress(Long progressId);

    List<Map<String, Object>> selectNodeList(Map<String, Object> params);

    int insertNode(Map<String, Object> node);

    int updateNode(Map<String, Object> node);

    int deleteNode(Long nodeId);

    int saveNodeMaterials(Long nodeId, List<Map<String, Object>> materials);

    List<Map<String, Object>> selectExpenseList(Map<String, Object> params);

    int insertExpense(Map<String, Object> expense);

    int updateExpense(Map<String, Object> expense);

    int deleteExpense(Long expenseId);

    List<Map<String, Object>> selectDocumentList(Map<String, Object> params);

    int insertDocument(Map<String, Object> document);

    int deleteDocument(Long documentId);

    Map<String, Object> selectArchive(Long caseId);

    int applyArchive(Map<String, Object> archive);

    int confirmClose(Map<String, Object> archive);

    int confirmArchive(Map<String, Object> archive);

    List<Map<String, Object>> selectStatusLogs(Map<String, Object> params);
}

package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.system.service.IBizMatterService;
import com.ruoyi.system.service.matter.MatterArchiveService;
import com.ruoyi.system.service.matter.MatterCommandService;
import com.ruoyi.system.service.matter.MatterDocumentService;
import com.ruoyi.system.service.matter.MatterExpenseService;
import com.ruoyi.system.service.matter.MatterNodeService;
import com.ruoyi.system.service.matter.MatterProgressService;
import com.ruoyi.system.service.matter.MatterQueryService;

@Service
public class BizMatterServiceImpl implements IBizMatterService
{
    private final MatterCommandService commandService;
    private final MatterQueryService queryService;
    private final MatterProgressService progressService;
    private final MatterNodeService nodeService;
    private final MatterExpenseService expenseService;
    private final MatterDocumentService documentService;
    private final MatterArchiveService archiveService;

    public BizMatterServiceImpl(MatterCommandService commandService, MatterQueryService queryService,
            MatterProgressService progressService, MatterNodeService nodeService,
            MatterExpenseService expenseService, MatterDocumentService documentService,
            MatterArchiveService archiveService)
    {
        this.commandService = commandService;
        this.queryService = queryService;
        this.progressService = progressService;
        this.nodeService = nodeService;
        this.expenseService = expenseService;
        this.documentService = documentService;
        this.archiveService = archiveService;
    }

    @Override public Map<String, Object> selectDashboard() { return queryService.dashboard(); }
    @Override public List<Map<String, Object>> selectMatterList(Map<String, Object> params) { return queryService.matters(params); }
    @Override public Map<String, Object> selectMatterDetail(Long caseId) { return queryService.detail(caseId); }
    @Override public List<Map<String, Object>> selectFieldConfigs(String caseType) { return queryService.fieldConfigs(caseType); }
    @Override public int insertMatter(Map<String, Object> matter) { return commandService.create(matter); }
    @Override public int updateMatter(Map<String, Object> matter) { return commandService.update(matter); }
    @Override public List<Map<String, Object>> selectProgressList(Map<String, Object> params) { return queryService.progress(params); }
    @Override public int insertProgress(Map<String, Object> progress) { return progressService.create(progress); }
    @Override public int updateProgress(Map<String, Object> progress) { return progressService.update(progress); }
    @Override public int deleteProgress(Long progressId) { return progressService.delete(progressId); }
    @Override public List<Map<String, Object>> selectNodeList(Map<String, Object> params) { return queryService.nodes(params); }
    @Override public int insertNode(Map<String, Object> node) { return nodeService.create(node); }
    @Override public int updateNode(Map<String, Object> node) { return nodeService.update(node); }
    @Override public int deleteNode(Long nodeId) { return nodeService.delete(nodeId); }
    @Override public int saveNodeMaterials(Long nodeId, List<Map<String, Object>> materials) { return nodeService.saveMaterials(nodeId, materials); }
    @Override public List<Map<String, Object>> selectExpenseList(Map<String, Object> params) { return queryService.expenses(params); }
    @Override public int insertExpense(Map<String, Object> expense) { return expenseService.create(expense); }
    @Override public int updateExpense(Map<String, Object> expense) { return expenseService.update(expense); }
    @Override public int deleteExpense(Long expenseId) { return expenseService.delete(expenseId); }
    @Override public List<Map<String, Object>> selectDocumentList(Map<String, Object> params) { return queryService.documents(params); }
    @Override public int insertDocument(Map<String, Object> document) { return documentService.create(document); }
    @Override public int deleteDocument(Long documentId) { return documentService.delete(documentId); }
    @Override public Map<String, Object> selectArchive(Long caseId) { return archiveService.select(caseId); }
    @Override public int applyArchive(Map<String, Object> archive) { return archiveService.apply(archive); }
    @Override public int confirmClose(Map<String, Object> archive) { return archiveService.close(archive); }
    @Override public int confirmArchive(Map<String, Object> archive) { return archiveService.archive(archive); }
    @Override public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params) { return queryService.statusLogs(params); }
}


package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.service.IBizMatterService;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class BizMatterServiceImpl implements IBizMatterService
{
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONFIRMING = "confirming";
    private static final String STATUS_TRANSFERING = "transfering";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_CLOSING = "closing";
    private static final String STATUS_CLOSED = "closed";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String STATUS_TERMINATED = "terminated";
    private static final String CONTRACT_AUDIT_PASSED = "2";
    private static final String CONTRACT_SIGNED = "1";
    private static final String CONTRACT_PERFORMING = "1";
    private static final String MATTER_PERMISSIONS =
            "matter:list,matter:query,matter:mine:list,matter:mine:query,matter:add,matter:edit,matter:import,matter:export,"
                    + "matter:progress:list,matter:progress:add,matter:progress:edit,matter:progress:remove,"
                    + "matter:node:list,matter:node:add,matter:node:edit,matter:node:remove,matter:node:remind,"
                    + "matter:expense:list,matter:expense:add,matter:expense:edit,matter:expense:remove,"
                    + "matter:document:list,matter:document:add,matter:document:remove,"
                    + "matter:archive:list,matter:archive:apply,matter:archive:confirm,matter:status:list";

    @Autowired
    private BizMatterMapper matterMapper;

    @Autowired
    private BizContractMapper contractMapper;

    @Autowired
    private BizCustomerMapper customerMapper;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Override
    public Map<String, Object> selectDashboard()
    {
        Map<String, Object> params = scopeParams(new HashMap<>());
        Map<String, Object> data = new HashMap<>();
        data.put("cards", matterMapper.selectDashboardCards(params));
        data.put("types", matterMapper.selectCaseTypeStats(params));
        data.put("reminders", matterMapper.selectReminders(params));
        return data;
    }

    @Override
    public List<Map<String, Object>> selectMatterList(Map<String, Object> params)
    {
        return matterMapper.selectMatterList(scopeParams(params));
    }

    @Override
    public Map<String, Object> selectMatterDetail(Long caseId)
    {
        Map<String, Object> matter = requireMatter(caseId);
        List<Map<String, Object>> fieldConfigs = matterMapper.selectFieldConfigs(text(matter.get("case_type")));
        matter.put("fieldConfigs", fieldConfigs);
        matter.put("fieldValues", buildDetailFieldValues(fieldConfigs, matterMapper.selectFieldValues(caseId)));
        matter.put("progress", matterMapper.selectProgressList(scopeParams(Map.of("caseId", caseId, "pageSize", 10))));
        matter.put("nodes", selectNodeList(Map.of("caseId", caseId, "pageSize", 20)));
        matter.put("expenses", matterMapper.selectExpenseList(scopeParams(Map.of("caseId", caseId, "pageSize", 10))));
        matter.put("documents", matterMapper.selectDocumentList(scopeParams(Map.of("caseId", caseId, "pageSize", 10))));
        Map<String, Object> archive = matterMapper.selectArchiveByCaseId(caseId);
        if (archive != null)
        {
            archive.put("materials", matterMapper.selectArchiveMaterials(toLong(archive.get("archive_id"), "归档记录不存在")));
        }
        matter.put("archive", archive);
        matter.put("statusLogs", matterMapper.selectStatusLogs(scopeParams(Map.of("caseId", caseId, "pageSize", 20))));
        return matter;
    }

    @Override
    public List<Map<String, Object>> selectFieldConfigs(String caseType)
    {
        assertDictValue("law_case_type", caseType, "案件类型不合法");
        return matterMapper.selectFieldConfigs(caseType);
    }

    @Override
    @Transactional
    public int insertMatter(Map<String, Object> matter)
    {
        Long contractId = toLong(matter.get("contractId"), "请选择来源合同");
        if (matterMapper.selectMatterByContractId(contractId) != null)
        {
            throw new ServiceException("该合同已生成案件");
        }
        BizContract contract = contractMapper.selectContractById(contractId);
        if (contract == null)
        {
            throw new ServiceException("来源合同不存在");
        }
        if (contract.getCustomerId() == null)
        {
            throw new ServiceException("合同未关联客户，不能创建案件");
        }
        if (!CONTRACT_AUDIT_PASSED.equals(contract.getAuditStatus()) || !CONTRACT_SIGNED.equals(contract.getSignStatus()) || !CONTRACT_PERFORMING.equals(contract.getContractStatus()))
        {
            throw new ServiceException("只有审核通过、已签订且履约中的合同可以创建案件");
        }
        BizCustomer customer = customerMapper.selectCustomerById(contract.getCustomerId());
        if (customer == null || "2".equals(customer.getDelFlag()))
        {
            throw new ServiceException("客户不存在或已删除");
        }
        matter.put("caseNo", "AJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        matter.put("caseName", defaultText(matter.get("caseName"), contract.getContractName()));
        matter.put("customerId", contract.getCustomerId());
        matter.put("customerName", contract.getCustomerName());
        matter.put("contractId", contractId);
        matter.put("contractNo", contract.getContractNo());
        matter.put("caseType", defaultText(matter.get("caseType"), contract.getCaseType()));
        matter.put("caseStatus", STATUS_PROCESSING);
        matter.put("archiveStatus", "none");
        matter.put("feeStatus", "none");
        matter.put("caseStage", defaultText(matter.get("caseStage"), "opening"));
        matter.put("riskLevel", defaultText(matter.get("riskLevel"), "medium"));
        matter.put("currentNode", "\u529e\u7406\u4e2d");
        matter.put("createBy", SecurityUtils.getUsername());
        matter.put("ownerId", SecurityUtils.getUserId());
        matter.put("deptId", SecurityUtils.getDeptId());
        matter.put("mainLawyerId", SecurityUtils.getUserId());
        matter.put("mainLawyerName", SecurityUtils.getLoginUser().getUser().getNickName());
        validateMatter(matter);
        validateFieldValues(text(matter.get("caseType")), matter.get("fieldValues"));
        int rows = matterMapper.insertMatter(matter);
        assertRows(rows, "案件创建失败");
        Long caseId = toLong(matter.get("caseId"), "案件创建失败");
        saveFieldValues(caseId, matter.get("fieldValues"));
        insertStatusLog(caseId, null, STATUS_PROCESSING, "create", "\u624b\u52a8\u521b\u5efa\u529e\u7406\u4e2d\u6848\u4ef6");
        return rows;
    }

    @Override
    @Transactional
    public int updateMatter(Map<String, Object> matter)
    {
        Long caseId = toLong(matter.get("caseId"), "请选择案件");
        Map<String, Object> existed = requireEditableMatter(caseId);
        matter.put("updateBy", SecurityUtils.getUsername());
        matter.put("expectedStatus", existed.get("case_status"));
        validateMatterUpdate(matter);
        validateFieldValues(text(matter.get("caseType")), matter.get("fieldValues"));
        int rows = matterMapper.updateMatter(matter);
        assertRows(rows, "案件已变化，请刷新后重试");
        saveFieldValues(caseId, matter.get("fieldValues"));
        insertStatusLog(caseId, text(existed.get("case_status")), text(existed.get("case_status")), "edit", "编辑案件信息");
        return rows;
    }

    @Override public List<Map<String, Object>> selectProgressList(Map<String, Object> params) { return matterMapper.selectProgressList(scopeParams(params)); }
    @Override public List<Map<String, Object>> selectNodeList(Map<String, Object> params) { return fillNodeMaterials(matterMapper.selectNodeList(scopeParams(params))); }
    @Override public List<Map<String, Object>> selectExpenseList(Map<String, Object> params) { return matterMapper.selectExpenseList(scopeParams(params)); }
    @Override public List<Map<String, Object>> selectDocumentList(Map<String, Object> params) { return matterMapper.selectDocumentList(scopeParams(params)); }
    @Override public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params) { return matterMapper.selectStatusLogs(scopeParams(params)); }

    @Override
    @Transactional
    public int insertProgress(Map<String, Object> progress)
    {
        Long caseId = toLong(progress.get("caseId"), "请选择案件");
        requireProcessEditableMatter(caseId);
        requiredText(progress.get("content"), "请输入进展内容");
        progress.put("recordUserId", SecurityUtils.getUserId());
        progress.put("recordUserName", SecurityUtils.getLoginUser().getUser().getNickName());
        progress.put("createBy", SecurityUtils.getUsername());
        int rows = matterMapper.insertProgress(progress);
        assertRows(rows, "进度记录创建失败");
        updateMatterTouch(caseId, progress.get("content"), "progress_add", "新增进度记录");
        return rows;
    }

    @Override
    @Transactional
    public int updateProgress(Map<String, Object> progress)
    {
        Map<String, Object> existed = requireOwnedProgress(toLong(progress.get("progressId"), "请选择进度记录"));
        requireProcessEditableMatter(toLong(existed.get("case_id"), "请选择案件"));
        requiredText(progress.get("content"), "请输入进展内容");
        progress.put("updateBy", SecurityUtils.getUsername());
        int rows = matterMapper.updateProgress(progress);
        assertRows(rows, "进度记录已变化，请刷新后重试");
        insertStatusLog(toLong(existed.get("case_id"), "请选择案件"), null, null, "progress_edit", "编辑进度记录");
        return rows;
    }

    @Override
    @Transactional
    public int deleteProgress(Long progressId)
    {
        Map<String, Object> existed = requireOwnedProgress(progressId);
        requireProcessEditableMatter(toLong(existed.get("case_id"), "请选择案件"));
        int rows = matterMapper.deleteProgress(progressId, SecurityUtils.getUsername());
        assertRows(rows, "进度记录已变化，请刷新后重试");
        insertStatusLog(toLong(existed.get("case_id"), "请选择案件"), null, null, "progress_remove", "删除进度记录");
        return rows;
    }

    @Override
    @Transactional
    public int insertNode(Map<String, Object> node)
    {
        Long caseId = toLong(node.get("caseId"), "请选择案件");
        requireProcessEditableMatter(caseId);
        validateNode(node);
        node.put("createBy", SecurityUtils.getUsername());
        int rows = matterMapper.insertNode(node);
        assertRows(rows, "关键节点创建失败");
        saveNodeMaterials(toLong(node.get("nodeId"), "关键节点创建失败"), listValue(node.get("materials")));
        syncMatterNodeState(caseId);
        insertStatusLog(caseId, null, null, "node_add", "新增关键节点：" + node.get("nodeName"));
        return rows;
    }

    @Override
    @Transactional
    public int updateNode(Map<String, Object> node)
    {
        Map<String, Object> existed = requireOwnedNode(toLong(node.get("nodeId"), "请选择关键节点"));
        requireProcessEditableMatter(toLong(existed.get("case_id"), "请选择案件"));
        validateNode(node);
        node.put("updateBy", SecurityUtils.getUsername());
        int rows = matterMapper.updateNode(node);
        assertRows(rows, "关键节点已变化，请刷新后重试");
        saveNodeMaterials(toLong(node.get("nodeId"), "请选择关键节点"), listValue(node.get("materials")));
        syncMatterNodeState(toLong(existed.get("case_id"), "请选择案件"));
        insertStatusLog(toLong(existed.get("case_id"), "请选择案件"), null, null, "node_edit", "编辑关键节点：" + node.get("nodeName"));
        return rows;
    }

    @Override
    @Transactional
    public int deleteNode(Long nodeId)
    {
        Map<String, Object> existed = requireOwnedNode(nodeId);
        Long caseId = toLong(existed.get("case_id"), "请选择案件");
        requireProcessEditableMatter(caseId);
        matterMapper.deleteNodeMaterials(nodeId);
        int rows = matterMapper.deleteNode(nodeId, SecurityUtils.getUsername());
        assertRows(rows, "关键节点已变化，请刷新后重试");
        syncMatterNodeState(caseId);
        insertStatusLog(caseId, null, null, "node_remove", "删除关键节点");
        return rows;
    }

    @Override
    @Transactional
    public int saveNodeMaterials(Long nodeId, List<Map<String, Object>> materials)
    {
        Map<String, Object> node = requireOwnedNode(nodeId);
        requireProcessEditableMatter(toLong(node.get("case_id"), "请选择案件"));
        matterMapper.deleteNodeMaterials(nodeId);
        if (materials == null)
        {
            return 1;
        }
        for (Map<String, Object> material : materials)
        {
            if (StringUtils.isEmpty(text(material.get("materialName"))))
            {
                continue;
            }
            material.put("nodeId", nodeId);
            material.put("materialStatus", defaultText(material.get("materialStatus"), "pending"));
            assertDictValue("law_case_material_status", material.get("materialStatus"), "材料状态不合法");
            material.put("createBy", SecurityUtils.getUsername());
            matterMapper.insertNodeMaterial(material);
        }
        return 1;
    }

    @Override
    @Transactional
    public int insertExpense(Map<String, Object> expense)
    {
        Long caseId = toLong(expense.get("caseId"), "请选择案件");
        requireProcessEditableMatter(caseId);
        validateExpense(expense);
        expense.put("expenseNo", "FY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        expense.put("createBy", SecurityUtils.getUsername());
        int rows = matterMapper.insertExpense(expense);
        assertRows(rows, "费用创建失败");
        updateMatterFeeStatus(caseId);
        insertStatusLog(caseId, null, null, "expense_add", "新增案件费用：" + expense.get("amount"));
        return rows;
    }

    @Override
    @Transactional
    public int updateExpense(Map<String, Object> expense)
    {
        Map<String, Object> existed = requireOwnedExpense(toLong(expense.get("expenseId"), "请选择费用"));
        requireProcessEditableMatter(toLong(existed.get("case_id"), "请选择案件"));
        validateExpense(expense);
        expense.put("updateBy", SecurityUtils.getUsername());
        int rows = matterMapper.updateExpense(expense);
        assertRows(rows, "费用已变化，请刷新后重试");
        updateMatterFeeStatus(toLong(existed.get("case_id"), "请选择案件"));
        insertStatusLog(toLong(existed.get("case_id"), "请选择案件"), null, null, "expense_edit", "编辑案件费用");
        return rows;
    }

    @Override
    @Transactional
    public int deleteExpense(Long expenseId)
    {
        Map<String, Object> existed = requireOwnedExpense(expenseId);
        Long caseId = toLong(existed.get("case_id"), "请选择案件");
        requireProcessEditableMatter(caseId);
        int rows = matterMapper.deleteExpense(expenseId, SecurityUtils.getUsername());
        assertRows(rows, "费用已变化，请刷新后重试");
        updateMatterFeeStatus(caseId);
        insertStatusLog(caseId, null, null, "expense_remove", "删除案件费用");
        return rows;
    }

    @Override
    @Transactional
    public int insertDocument(Map<String, Object> document)
    {
        Long caseId = toLong(document.get("caseId"), "请选择案件");
        requireProcessEditableMatter(caseId);
        requiredText(document.get("documentType"), "请选择文档类型");
        requiredText(document.get("fileName"), "请填写文件名称");
        requiredText(document.get("fileUrl"), "请上传文件");
        assertDictValue("law_case_document_type", document.get("documentType"), "文档类型不合法");
        document.put("createBy", SecurityUtils.getUsername());
        int rows = matterMapper.insertDocument(document);
        assertRows(rows, "文档创建失败");
        insertStatusLog(caseId, null, null, "document_add", "新增案件文档：" + document.get("fileName"));
        return rows;
    }

    @Override
    @Transactional
    public int deleteDocument(Long documentId)
    {
        Map<String, Object> existed = matterMapper.selectDocumentById(documentId);
        if (existed == null)
        {
            throw new ServiceException("文档不存在");
        }
        Long caseId = toLong(existed.get("case_id"), "请选择案件");
        requireProcessEditableMatter(caseId);
        int rows = matterMapper.deleteDocument(documentId, SecurityUtils.getUsername());
        assertRows(rows, "文档已变化，请刷新后重试");
        insertStatusLog(caseId, null, null, "document_remove", "删除案件文档");
        return rows;
    }

    @Override
    public Map<String, Object> selectArchive(Long caseId)
    {
        requireMatter(caseId);
        Map<String, Object> archive = matterMapper.selectArchiveByCaseId(caseId);
        if (archive != null)
        {
            archive.put("materials", matterMapper.selectArchiveMaterials(toLong(archive.get("archive_id"), "归档记录不存在")));
        }
        return archive;
    }

    @Override
    @Transactional
    public int applyArchive(Map<String, Object> archive)
    {
        Long caseId = toLong(archive.get("caseId"), "请选择案件");
        Map<String, Object> matter = requireProcessingMatter(caseId);
        validateCloseReady(caseId);
        validateArchive(archive);
        archive.put("archiveStatus", "pending");
        archive.put("createBy", SecurityUtils.getUsername());
        Map<String, Object> existed = matterMapper.selectArchiveByCaseId(caseId);
        int rows;
        if (existed == null)
        {
            rows = matterMapper.insertArchive(archive);
        }
        else
        {
            archive.put("archiveId", existed.get("archive_id"));
            archive.put("updateBy", SecurityUtils.getUsername());
            rows = matterMapper.updateArchive(archive);
        }
        assertRows(rows, "结案申请保存失败");
        Long archiveId = toLong(existed == null ? archive.get("archiveId") : existed.get("archive_id"), "归档记录不存在");
        saveArchiveMaterials(archiveId, listValue(archive.get("materials")));
        Map<String, Object> status = new HashMap<>();
        status.put("caseId", caseId);
        status.put("caseStatus", STATUS_CLOSING);
        status.put("archiveStatus", "pending");
        status.put("currentNode", "结案申请");
        status.put("expectedStatus", matter.get("case_status"));
        status.put("updateBy", SecurityUtils.getUsername());
        assertRows(matterMapper.updateMatterStatus(status), "案件状态已变化，请刷新后重试");
        insertStatusLog(caseId, text(matter.get("case_status")), STATUS_CLOSING, "archive_apply", "发起结案申请");
        return rows;
    }

    @Override
    @Transactional
    public int confirmClose(Map<String, Object> archive)
    {
        Long caseId = toLong(archive.get("caseId"), "请选择案件");
        Map<String, Object> matter = requireMatter(caseId);
        if (!STATUS_CLOSING.equals(text(matter.get("case_status"))))
        {
            throw new ServiceException("只有结案申请中的案件可以确认结案");
        }
        Map<String, Object> existed = matterMapper.selectArchiveByCaseId(caseId);
        if (existed == null)
        {
            throw new ServiceException("请先提交结案申请");
        }
        validateArchive(archive);
        validateCloseReady(caseId);
        validateArchiveFeeMarkedCleared(archive);
        validateCaseFeeCleared(caseId);
        archive.put("archiveId", existed.get("archive_id"));
        archive.put("archiveStatus", "pending");
        archive.put("updateBy", SecurityUtils.getUsername());
        int rows = matterMapper.updateArchive(archive);
        assertRows(rows, "确认结案失败");
        saveArchiveMaterials(toLong(existed.get("archive_id"), "归档记录不存在"), listValue(archive.get("materials")));
        Map<String, Object> status = new HashMap<>();
        status.put("caseId", caseId);
        status.put("caseStatus", STATUS_CLOSED);
        status.put("archiveStatus", "pending");
        status.put("currentNode", "已结案");
        status.put("expectedStatus", matter.get("case_status"));
        status.put("updateBy", SecurityUtils.getUsername());
        assertRows(matterMapper.updateMatterStatus(status), "案件状态已变化，请刷新后重试");
        insertStatusLog(caseId, text(matter.get("case_status")), STATUS_CLOSED, "archive_close", "确认结案");
        return rows;
    }

    @Override
    @Transactional
    public int confirmArchive(Map<String, Object> archive)
    {
        Long caseId = toLong(archive.get("caseId"), "请选择案件");
        Map<String, Object> matter = requireMatter(caseId);
        if (!STATUS_CLOSED.equals(text(matter.get("case_status"))))
        {
            throw new ServiceException("只有结案中或已结案案件可以归档");
        }
        Map<String, Object> existed = matterMapper.selectArchiveByCaseId(caseId);
        if (existed == null)
        {
            throw new ServiceException("请先提交结案申请");
        }
        validateArchive(archive);
        archive.put("archiveId", existed.get("archive_id"));
        archive.put("archiveStatus", "archived");
        archive.put("archiveNo", defaultText(archive.get("archiveNo"), "JG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))));
        archive.put("archiverId", SecurityUtils.getUserId());
        archive.put("archiverName", SecurityUtils.getLoginUser().getUser().getNickName());
        archive.put("updateBy", SecurityUtils.getUsername());
        int rows = matterMapper.updateArchive(archive);
        assertRows(rows, "归档失败");
        saveArchiveMaterials(toLong(existed.get("archive_id"), "归档记录不存在"), listValue(archive.get("materials")));
        validateCaseFeeCleared(caseId);
        validateArchiveReady(toLong(existed.get("archive_id"), "归档记录不存在"), archive);
        Map<String, Object> status = new HashMap<>();
        status.put("caseId", caseId);
        status.put("caseStatus", STATUS_ARCHIVED);
        status.put("archiveStatus", "archived");
        status.put("currentNode", "已归档");
        status.put("expectedStatus", matter.get("case_status"));
        status.put("updateBy", SecurityUtils.getUsername());
        assertRows(matterMapper.updateMatterStatus(status), "案件状态已变化，请刷新后重试");
        insertStatusLog(caseId, text(matter.get("case_status")), STATUS_ARCHIVED, "archive_confirm", "确认归档");
        return rows;
    }

    private void saveArchiveMaterials(Long archiveId, List<Map<String, Object>> materials)
    {
        matterMapper.deleteArchiveMaterials(archiveId);
        if (materials == null)
        {
            return;
        }
        for (Map<String, Object> material : materials)
        {
            if (StringUtils.isEmpty(text(material.get("materialName"))))
            {
                continue;
            }
            material.put("archiveId", archiveId);
            material.put("materialStatus", defaultText(material.get("materialStatus"), "pending"));
            assertDictValue("law_case_material_status", material.get("materialStatus"), "材料状态不合法");
            material.put("createBy", SecurityUtils.getUsername());
            matterMapper.insertArchiveMaterial(material);
        }
    }

    private List<Map<String, Object>> fillNodeMaterials(List<Map<String, Object>> nodes)
    {
        if (nodes == null || nodes.isEmpty())
        {
            return nodes;
        }
        for (Map<String, Object> node : nodes)
        {
            Long nodeId = toLong(node.get("node_id"), "关键节点不存在");
            node.put("materials", matterMapper.selectNodeMaterials(nodeId));
        }
        return nodes;
    }

    private void validateMatter(Map<String, Object> matter)
    {
        requiredText(matter.get("caseName"), "请输入案件名称");
        requiredText(matter.get("caseType"), "请选择案件类型");
        assertDictValue("law_case_type", matter.get("caseType"), "案件类型不合法");
    }

    private void validateMatterUpdate(Map<String, Object> matter)
    {
        requiredText(matter.get("caseName"), "请输入案件名称");
        assertDictValue("law_case_type", matter.get("caseType"), "案件类型不合法");
        assertDictValue("law_case_stage", matter.get("caseStage"), "办理阶段不合法");
        assertDictValue("law_case_risk_level", matter.get("riskLevel"), "风险等级不合法");
    }

    private void validateNode(Map<String, Object> node)
    {
        requiredText(node.get("nodeName"), "请输入节点名称");
        requiredText(node.get("planDate"), "请选择计划日期");
        assertDictValue("law_case_node_status", node.get("nodeStatus"), "节点状态不合法");
        assertDictValue("law_case_node_type", node.get("nodeType"), "节点类型不合法");
        String nodeStatus = text(node.get("nodeStatus"));
        String actualDate = text(node.get("actualDate"));
        if ("done".equals(nodeStatus) && StringUtils.isEmpty(actualDate))
        {
            throw new ServiceException("已完成节点必须填写实际日期");
        }
        if (!StringUtils.isEmpty(actualDate) && ("pending".equals(nodeStatus) || "current".equals(nodeStatus)))
        {
            throw new ServiceException("已填写实际日期的节点不能保持待开始或当前节点状态");
        }
    }

    private void validateExpense(Map<String, Object> expense)
    {
        requiredText(expense.get("expenseType"), "请选择费用类型");
        BigDecimal amount = decimalValue(requiredText(expense.get("amount"), "请输入费用金额"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("费用金额必须大于0");
        }
        requiredText(expense.get("occurDate"), "请选择发生日期");
        assertDictValue("law_case_expense_type", expense.get("expenseType"), "费用类型不合法");
        assertDictValue("law_case_pay_status", expense.get("payStatus"), "付款状态不合法");
        assertDictValue("law_case_reimburse_status", expense.get("reimburseStatus"), "报销状态不合法");
        assertDictValue("law_case_voucher_status", expense.get("voucherStatus"), "凭证状态不合法");
    }

    private void validateCloseReady(Long caseId)
    {
        if (matterMapper.countUnfinishedNodes(caseId) > 0)
        {
            throw new ServiceException("仍有未完成或超期的关键节点，不能发起结案");
        }
    }

    private void validateCaseFeeCleared(Long caseId)
    {
        if (matterMapper.countUnpaidExpenseByCaseId(caseId) > 0)
        {
            throw new ServiceException("仍有未付款的案件费用，不能确认结案或归档");
        }
    }

    private void validateArchiveReady(Long archiveId, Map<String, Object> archive)
    {
        validateArchiveFeeMarkedCleared(archive);
        if (matterMapper.countArchiveMaterials(archiveId) == 0)
        {
            throw new ServiceException("请维护归档资料清单后再确认归档");
        }
        if (matterMapper.countNotReadyArchiveMaterials(archiveId) > 0)
        {
            throw new ServiceException("仍有未准备完成的归档资料，不能确认归档");
        }
    }

    private void validateArchiveFeeMarkedCleared(Map<String, Object> archive)
    {
        if (!"cleared".equals(text(archive.get("feeClearStatus"))))
        {
            throw new ServiceException("费用未结清，不能确认结案或归档");
        }
    }

    private void validateArchive(Map<String, Object> archive)
    {
        requiredText(archive.get("closeResult"), "请选择结案结果");
        requiredText(archive.get("closeDate"), "请选择结案日期");
        requiredText(archive.get("summary"), "请输入办案总结");
        assertDictValue("law_case_close_result", archive.get("closeResult"), "结案结果不合法");
        assertDictValue("law_case_fee_clear_status", archive.get("feeClearStatus"), "费用结清状态不合法");
    }

    private void validateFieldValues(String caseType, Object rawFields)
    {
        List<Map<String, Object>> configs = matterMapper.selectFieldConfigs(caseType);
        if (configs == null || configs.isEmpty())
        {
            return;
        }
        Map<String, String> values = new HashMap<>();
        for (Map<String, Object> field : listValue(rawFields))
        {
            String code = text(field.get("fieldCode"));
            if (StringUtils.isEmpty(code))
            {
                code = text(field.get("field_code"));
            }
            if (!StringUtils.isEmpty(code))
            {
                values.put(code, text(defaultText(field.get("fieldValue"), field.get("field_value"))));
            }
        }
        for (Map<String, Object> config : configs)
        {
            String required = text(config.get("required_flag"));
            String code = text(config.get("field_code"));
            String value = values.get(code);
            if ("Y".equals(required) && StringUtils.isEmpty(value))
            {
                throw new ServiceException("请填写案件专属信息：" + config.get("field_name"));
            }
            validateFieldValueType(config, value);
        }
    }

    private void validateFieldValueType(Map<String, Object> config, String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return;
        }
        String fieldName = text(config.get("field_name"));
        String fieldType = text(config.get("field_type"));
        try
        {
            if ("number".equals(fieldType))
            {
                new BigDecimal(value);
            }
            else if ("date".equals(fieldType))
            {
                LocalDate.parse(value);
            }
            else if ("switch".equals(fieldType) && !"Y".equals(value) && !"N".equals(value))
            {
                throw new IllegalArgumentException();
            }
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("案件专属信息格式不正确：" + fieldName);
        }
    }

    private Map<String, Object> requireMatter(Long caseId)
    {
        Map<String, Object> matter = matterMapper.selectMatterById(caseId);
        if (matter == null)
        {
            throw new ServiceException("案件不存在或已删除");
        }
        if (!SecurityUtils.isAdmin() && matterMapper.countMatterInDataScope(caseId, SecurityUtils.getUserId(), SecurityUtils.getDeptId(), true, MATTER_PERMISSIONS) == 0)
        {
            throw new ServiceException("无权访问该案件");
        }
        return matter;
    }

    private Map<String, Object> requireEditableMatter(Long caseId)
    {
        Map<String, Object> matter = requireMatter(caseId);
        String status = text(matter.get("case_status"));
        if (!STATUS_PROCESSING.equals(status))
        {
            throw new ServiceException("只有办理中案件允许编辑办案信息");
        }
        return matter;
    }

    private Map<String, Object> requireProcessingMatter(Long caseId)
    {
        Map<String, Object> matter = requireMatter(caseId);
        if (!STATUS_PROCESSING.equals(text(matter.get("case_status"))))
        {
            throw new ServiceException("只有办理中案件可以发起该操作");
        }
        return matter;
    }

    private void requireProcessEditableMatter(Long caseId)
    {
        requireProcessingMatter(caseId);
    }

    private Map<String, Object> requireOwnedProgress(Long progressId)
    {
        Map<String, Object> row = matterMapper.selectProgressById(progressId);
        if (row == null || "2".equals(text(row.get("del_flag"))))
        {
            throw new ServiceException("进度记录不存在");
        }
        requireMatter(toLong(row.get("case_id"), "请选择案件"));
        return row;
    }

    private Map<String, Object> requireOwnedNode(Long nodeId)
    {
        Map<String, Object> row = matterMapper.selectNodeById(nodeId);
        if (row == null || "2".equals(text(row.get("del_flag"))))
        {
            throw new ServiceException("关键节点不存在");
        }
        requireMatter(toLong(row.get("case_id"), "请选择案件"));
        return row;
    }

    private Map<String, Object> requireOwnedExpense(Long expenseId)
    {
        Map<String, Object> row = matterMapper.selectExpenseById(expenseId);
        if (row == null || "2".equals(text(row.get("del_flag"))))
        {
            throw new ServiceException("费用不存在");
        }
        requireMatter(toLong(row.get("case_id"), "请选择案件"));
        return row;
    }

    private void updateMatterTouch(Long caseId, Object content, String action, String logContent)
    {
        Map<String, Object> update = new HashMap<>();
        update.put("caseId", caseId);
        update.put("recentProgress", limitText(text(content), 300));
        update.put("currentNode", "办理中");
        update.put("updateBy", SecurityUtils.getUsername());
        matterMapper.updateMatter(update);
        insertStatusLog(caseId, null, null, action, logContent);
    }

    private void syncMatterNodeState(Long caseId)
    {
        Map<String, Object> currentNode = matterMapper.selectCurrentOpenNode(caseId);
        Map<String, Object> update = new HashMap<>();
        update.put("caseId", caseId);
        update.put("updateBy", SecurityUtils.getUsername());
        update.put("refreshNextDate", true);
        if (currentNode == null)
        {
            update.put("currentNode", "办理中");
        }
        else
        {
            update.put("currentNode", currentNode.get("node_name"));
            update.put("caseStage", nodeTypeToStage(text(currentNode.get("node_type"))));
        }
        matterMapper.updateMatter(update);
    }

    private String nodeTypeToStage(String nodeType)
    {
        if ("evidence".equals(nodeType))
        {
            return "evidence";
        }
        if ("hearing".equals(nodeType) || "judgment".equals(nodeType))
        {
            return "hearing";
        }
        if ("execution".equals(nodeType))
        {
            return "execution";
        }
        if ("archive".equals(nodeType))
        {
            return "archive";
        }
        return "opening";
    }

    private void updateMatterFeeStatus(Long caseId)
    {
        Map<String, Object> update = new HashMap<>();
        update.put("caseId", caseId);
        int expenseCount = matterMapper.countExpenseByCaseId(caseId);
        String feeStatus = "none";
        if (expenseCount > 0)
        {
            feeStatus = matterMapper.countUnpaidExpenseByCaseId(caseId) == 0 ? "settled" : "partial";
        }
        update.put("feeStatus", feeStatus);
        update.put("updateBy", SecurityUtils.getUsername());
        matterMapper.updateMatter(update);
    }

    private void saveFieldValues(Long caseId, Object rawFields)
    {
        matterMapper.deleteFieldValues(caseId);
        Map<String, Object> matter = matterMapper.selectMatterById(caseId);
        Map<String, Map<String, Object>> configMap = new HashMap<>();
        if (matter != null)
        {
            for (Map<String, Object> config : matterMapper.selectFieldConfigs(text(matter.get("case_type"))))
            {
                configMap.put(text(config.get("field_code")), config);
            }
        }
        for (Map<String, Object> field : listValue(rawFields))
        {
            String fieldCode = defaultText(field.get("fieldCode"), field.get("field_code"));
            String fieldValue = defaultText(field.get("fieldValue"), field.get("field_value"));
            if (StringUtils.isEmpty(fieldCode) || StringUtils.isEmpty(fieldValue))
            {
                continue;
            }
            Map<String, Object> config = configMap.get(fieldCode);
            field.put("caseId", caseId);
            field.put("fieldCode", fieldCode);
            field.put("fieldValue", fieldValue);
            if (config != null)
            {
                field.put("fieldName", config.get("field_name"));
                field.put("orderNum", config.get("order_num"));
            }
            field.put("createBy", SecurityUtils.getUsername());
            matterMapper.insertFieldValue(field);
        }
    }

    private List<Map<String, Object>> buildDetailFieldValues(List<Map<String, Object>> configs, List<Map<String, Object>> values)
    {
        Map<String, Map<String, Object>> valueMap = new HashMap<>();
        for (Map<String, Object> value : values)
        {
            valueMap.put(text(value.get("field_code")), value);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> config : configs)
        {
            String code = text(config.get("field_code"));
            Map<String, Object> merged = new HashMap<>(config);
            Map<String, Object> value = valueMap.get(code);
            if (value != null)
            {
                merged.putAll(value);
            }
            else
            {
                merged.put("field_code", code);
                merged.put("field_name", config.get("field_name"));
                merged.put("field_value", "");
            }
            result.add(merged);
        }
        return result;
    }

    private void insertStatusLog(Long caseId, String fromStatus, String toStatus, String actionType, String content)
    {
        assertDictValue("law_case_status_action", actionType, "案件状态动作不合法");
        Map<String, Object> log = new HashMap<>();
        log.put("caseId", caseId);
        log.put("fromStatus", fromStatus);
        log.put("toStatus", toStatus);
        log.put("actionType", actionType);
        log.put("content", content);
        log.put("createBy", SecurityUtils.getUsername());
        assertRows(matterMapper.insertStatusLog(log), "案件状态记录创建失败");
    }

    private Map<String, Object> scopeParams(Map<String, Object> params)
    {
        Map<String, Object> target = new HashMap<>();
        if (params != null)
        {
            target.putAll(params);
        }
        target.put("currentUserId", SecurityUtils.getUserId());
        target.put("currentDeptId", SecurityUtils.getDeptId());
        target.put("dataScope", !SecurityUtils.isAdmin());
        target.put("permissions", MATTER_PERMISSIONS);
        return target;
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String valueText = text(value);
        if (StringUtils.isEmpty(valueText))
        {
            return;
        }
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (containsDictValue(options, valueText))
        {
            return;
        }
        dictTypeService.resetDictCache();
        options = dictTypeService.selectDictDataByType(dictType);
        if (options == null || options.isEmpty())
        {
            throw new ServiceException("字典未初始化：" + dictType);
        }
        if (containsDictValue(options, valueText))
        {
            return;
        }
        throw new ServiceException(message);
    }

    private boolean containsDictValue(List<SysDictData> options, String value)
    {
        if (options == null)
        {
            return false;
        }
        for (SysDictData item : options)
        {
            if (value.equals(item.getDictValue()))
            {
                return true;
            }
        }
        return false;
    }

    private Long toLong(Object value, String message)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)))
        {
            throw new ServiceException(message);
        }
        return Long.valueOf(String.valueOf(value));
    }

    private BigDecimal decimalValue(String value)
    {
        try
        {
            return new BigDecimal(value);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("金额格式不正确");
        }
    }

    private String requiredText(Object value, String message)
    {
        String text = text(value);
        if (StringUtils.isEmpty(text))
        {
            throw new ServiceException(message);
        }
        return text;
    }

    private String defaultText(Object value, Object fallback)
    {
        String text = text(value);
        return StringUtils.isEmpty(text) ? text(fallback) : text;
    }

    private String text(Object value)
    {
        return value == null || "null".equalsIgnoreCase(String.valueOf(value)) ? null : String.valueOf(value).trim();
    }

    private String limitText(String value, int maxLength)
    {
        if (value == null || value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listValue(Object value)
    {
        if (value instanceof List)
        {
            return (List<Map<String, Object>>) value;
        }
        return List.of();
    }

    private void assertRows(int rows, String message)
    {
        if (rows <= 0)
        {
            throw new ServiceException(message);
        }
    }
}

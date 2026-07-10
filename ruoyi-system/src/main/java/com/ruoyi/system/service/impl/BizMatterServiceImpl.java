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
import com.ruoyi.system.service.matter.MatterProgressService;
import com.ruoyi.system.service.matter.MatterDocumentService;
import com.ruoyi.system.service.matter.MatterExpenseService;
import com.ruoyi.system.service.matter.MatterNodeService;
import com.ruoyi.system.service.matter.MatterArchiveService;
import com.ruoyi.system.service.matter.MatterQueryService;
import com.law.business.shared.status.CaseStatus;

@Service
public class BizMatterServiceImpl implements IBizMatterService
{
    private static final String STATUS_PENDING = CaseStatus.PENDING.code();
    private static final String STATUS_CONFIRMING = CaseStatus.CONFIRMING.code();
    private static final String STATUS_TRANSFERING = CaseStatus.TRANSFERRING.code();
    private static final String STATUS_PROCESSING = CaseStatus.PROCESSING.code();
    private static final String STATUS_CLOSING = CaseStatus.CLOSING.code();
    private static final String STATUS_CLOSED = CaseStatus.CLOSED.code();
    private static final String STATUS_ARCHIVED = CaseStatus.ARCHIVED.code();
    private static final String STATUS_TERMINATED = CaseStatus.TERMINATED.code();
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

    @Autowired
    private MatterProgressService progressService;

    @Autowired
    private MatterDocumentService documentService;

    @Autowired
    private MatterExpenseService expenseService;

    @Autowired
    private MatterNodeService nodeService;

    @Autowired
    private MatterArchiveService archiveService;

    @Autowired
    private MatterQueryService queryService;

    @Override
    public Map<String, Object> selectDashboard()
    {
        return queryService.dashboard();
    }

    @Override
    public List<Map<String, Object>> selectMatterList(Map<String, Object> params)
    {
        return queryService.matters(params);
    }

    @Override
    public Map<String, Object> selectMatterDetail(Long caseId)
    {
        return queryService.detail(caseId);
    }

    @Override
    public List<Map<String, Object>> selectFieldConfigs(String caseType)
    {
        return queryService.fieldConfigs(caseType);
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

    @Override public List<Map<String, Object>> selectProgressList(Map<String, Object> params) { return queryService.progress(params); }
    @Override public List<Map<String, Object>> selectNodeList(Map<String, Object> params) { return queryService.nodes(params); }
    @Override public List<Map<String, Object>> selectExpenseList(Map<String, Object> params) { return queryService.expenses(params); }
    @Override public List<Map<String, Object>> selectDocumentList(Map<String, Object> params) { return queryService.documents(params); }
    @Override public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params) { return queryService.statusLogs(params); }

    @Override
    @Transactional
    public int insertProgress(Map<String, Object> progress)
    {
        return progressService.create(progress);
    }

    @Override
    @Transactional
    public int updateProgress(Map<String, Object> progress)
    {
        return progressService.update(progress);
    }

    @Override
    @Transactional
    public int deleteProgress(Long progressId)
    {
        return progressService.delete(progressId);
    }

    @Override
    @Transactional
    public int insertNode(Map<String, Object> node)
    {
        return nodeService.create(node);
    }

    @Override
    @Transactional
    public int updateNode(Map<String, Object> node)
    {
        return nodeService.update(node);
    }

    @Override
    @Transactional
    public int deleteNode(Long nodeId)
    {
        return nodeService.delete(nodeId);
    }

    @Override
    @Transactional
    public int saveNodeMaterials(Long nodeId, List<Map<String, Object>> materials)
    {
        return nodeService.saveMaterials(nodeId, materials);
    }

    @Override
    @Transactional
    public int insertExpense(Map<String, Object> expense)
    {
        return expenseService.create(expense);
    }

    @Override
    @Transactional
    public int updateExpense(Map<String, Object> expense)
    {
        return expenseService.update(expense);
    }

    @Override
    @Transactional
    public int deleteExpense(Long expenseId)
    {
        return expenseService.delete(expenseId);
    }

    @Override
    @Transactional
    public int insertDocument(Map<String, Object> document)
    {
        return documentService.create(document);
    }

    @Override
    @Transactional
    public int deleteDocument(Long documentId)
    {
        return documentService.delete(documentId);
    }

    @Override
    public Map<String, Object> selectArchive(Long caseId)
    {
        return archiveService.select(caseId);
    }

    @Override
    @Transactional
    public int applyArchive(Map<String, Object> archive)
    {
        return archiveService.apply(archive);
    }

    @Override
    @Transactional
    public int confirmClose(Map<String, Object> archive)
    {
        return archiveService.close(archive);
    }

    @Override
    @Transactional
    public int confirmArchive(Map<String, Object> archive)
    {
        return archiveService.archive(archive);
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

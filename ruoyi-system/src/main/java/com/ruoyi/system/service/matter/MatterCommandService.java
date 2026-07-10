package com.ruoyi.system.service.matter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.shared.status.CaseStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class MatterCommandService
{
    private static final String PROCESSING = CaseStatus.PROCESSING.code();
    private static final String MATTER_PERMISSIONS = "matter:list,matter:query,matter:mine:list,matter:mine:query,matter:add,matter:edit,matter:import,matter:export";
    private final BizMatterMapper mapper;
    private final BizContractMapper contractMapper;
    private final BizCustomerMapper customerMapper;
    private final ISysDictTypeService dictService;

    public MatterCommandService(BizMatterMapper mapper, BizContractMapper contractMapper,
            BizCustomerMapper customerMapper, ISysDictTypeService dictService)
    {
        this.mapper = mapper;
        this.contractMapper = contractMapper;
        this.customerMapper = customerMapper;
        this.dictService = dictService;
    }

    @Transactional
    public int create(Map<String, Object> matter)
    {
        Long contractId = toLong(matter.get("contractId"), "请选择来源合同");
        if (mapper.selectMatterByContractId(contractId) != null) throw new ServiceException("该合同已生成案件");
        BizContract contract = contractMapper.selectContractById(contractId);
        if (contract == null) throw new ServiceException("来源合同不存在");
        if (contract.getCustomerId() == null) throw new ServiceException("合同未关联客户，不能创建案件");
        if (!"2".equals(contract.getAuditStatus()) || !"1".equals(contract.getSignStatus()) || !"1".equals(contract.getContractStatus()))
            throw new ServiceException("只有审核通过、已签订且履约中的合同可以创建案件");
        BizCustomer customer = customerMapper.selectCustomerById(contract.getCustomerId());
        if (customer == null || "2".equals(customer.getDelFlag())) throw new ServiceException("客户不存在或已删除");
        matter.put("caseNo", "AJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        matter.put("caseName", defaultText(matter.get("caseName"), contract.getContractName()));
        matter.put("customerId", contract.getCustomerId()); matter.put("customerName", contract.getCustomerName());
        matter.put("contractId", contractId); matter.put("contractNo", contract.getContractNo());
        matter.put("caseType", defaultText(matter.get("caseType"), contract.getCaseType()));
        matter.put("caseStatus", PROCESSING); matter.put("archiveStatus", "none"); matter.put("feeStatus", "none");
        matter.put("caseStage", defaultText(matter.get("caseStage"), "opening"));
        matter.put("riskLevel", defaultText(matter.get("riskLevel"), "medium")); matter.put("currentNode", "办理中");
        matter.put("createBy", SecurityUtils.getUsername()); matter.put("ownerId", SecurityUtils.getUserId());
        matter.put("deptId", SecurityUtils.getDeptId()); matter.put("mainLawyerId", SecurityUtils.getUserId());
        matter.put("mainLawyerName", SecurityUtils.getLoginUser().getUser().getNickName());
        requiredText(matter.get("caseName"), "请输入案件名称"); requiredText(matter.get("caseType"), "请选择案件类型");
        assertDict("law_case_type", matter.get("caseType"), "案件类型不合法");
        validateFields(text(matter.get("caseType")), matter.get("fieldValues"));
        int rows = mapper.insertMatter(matter); assertRows(rows, "案件创建失败");
        Long caseId = toLong(matter.get("caseId"), "案件创建失败");
        saveFields(caseId, matter.get("fieldValues")); log(caseId, null, PROCESSING, "create", "手动创建办理中案件");
        return rows;
    }

    @Transactional
    public int update(Map<String, Object> matter)
    {
        Long caseId = toLong(matter.get("caseId"), "请选择案件");
        Map<String, Object> existed = requireEditable(caseId);
        matter.put("updateBy", SecurityUtils.getUsername()); matter.put("expectedStatus", existed.get("case_status"));
        requiredText(matter.get("caseName"), "请输入案件名称");
        assertDict("law_case_type", matter.get("caseType"), "案件类型不合法");
        assertDict("law_case_stage", matter.get("caseStage"), "办理阶段不合法");
        assertDict("law_case_risk_level", matter.get("riskLevel"), "风险等级不合法");
        validateFields(text(matter.get("caseType")), matter.get("fieldValues"));
        int rows = mapper.updateMatter(matter); assertRows(rows, "案件已变化，请刷新后重试");
        saveFields(caseId, matter.get("fieldValues"));
        log(caseId, text(existed.get("case_status")), text(existed.get("case_status")), "edit", "编辑案件信息");
        return rows;
    }

    private Map<String, Object> requireEditable(Long caseId)
    {
        Map<String, Object> matter = mapper.selectMatterById(caseId);
        if (matter == null) throw new ServiceException("案件不存在或已删除");
        if (!SecurityUtils.isAdmin() && mapper.countMatterInDataScope(caseId, SecurityUtils.getUserId(), SecurityUtils.getDeptId(), true, MATTER_PERMISSIONS) == 0)
            throw new ServiceException("无权访问该案件");
        if (!PROCESSING.equals(text(matter.get("case_status")))) throw new ServiceException("只有办理中案件允许编辑办案信息");
        return matter;
    }

    private void validateFields(String caseType, Object raw)
    {
        List<Map<String, Object>> configs = mapper.selectFieldConfigs(caseType);
        if (configs == null || configs.isEmpty()) return;
        Map<String, String> values = new HashMap<>();
        for (Map<String, Object> field : list(raw))
        {
            String code = defaultText(field.get("fieldCode"), field.get("field_code"));
            if (!StringUtils.isEmpty(code)) values.put(code, text(defaultText(field.get("fieldValue"), field.get("field_value"))));
        }
        for (Map<String, Object> config : configs)
        {
            String value = values.get(text(config.get("field_code")));
            if ("Y".equals(text(config.get("required_flag"))) && StringUtils.isEmpty(value))
                throw new ServiceException("请填写案件专属信息：" + config.get("field_name"));
            validateFieldType(config, value);
        }
    }

    private void validateFieldType(Map<String, Object> config, String value)
    {
        if (StringUtils.isEmpty(value)) return;
        try
        {
            String type = text(config.get("field_type"));
            if ("number".equals(type)) new BigDecimal(value);
            else if ("date".equals(type)) LocalDate.parse(value);
            else if ("switch".equals(type) && !"Y".equals(value) && !"N".equals(value)) throw new IllegalArgumentException();
        }
        catch (RuntimeException e) { throw new ServiceException("案件专属信息格式不正确：" + config.get("field_name")); }
    }

    private void saveFields(Long caseId, Object raw)
    {
        mapper.deleteFieldValues(caseId);
        Map<String, Object> matter = mapper.selectMatterById(caseId);
        Map<String, Map<String, Object>> configs = new HashMap<>();
        if (matter != null) for (Map<String, Object> item : mapper.selectFieldConfigs(text(matter.get("case_type")))) configs.put(text(item.get("field_code")), item);
        for (Map<String, Object> field : list(raw))
        {
            String code = defaultText(field.get("fieldCode"), field.get("field_code"));
            String value = defaultText(field.get("fieldValue"), field.get("field_value"));
            if (StringUtils.isEmpty(code) || StringUtils.isEmpty(value)) continue;
            Map<String, Object> config = configs.get(code);
            field.put("caseId", caseId); field.put("fieldCode", code); field.put("fieldValue", value);
            if (config != null) { field.put("fieldName", config.get("field_name")); field.put("orderNum", config.get("order_num")); }
            field.put("createBy", SecurityUtils.getUsername()); mapper.insertFieldValue(field);
        }
    }

    private void log(Long id, String from, String to, String action, String content)
    {
        assertDict("law_case_status_action", action, "案件状态动作不合法");
        Map<String, Object> row = new HashMap<>(); row.put("caseId", id); row.put("fromStatus", from); row.put("toStatus", to);
        row.put("actionType", action); row.put("content", content); row.put("createBy", SecurityUtils.getUsername());
        assertRows(mapper.insertStatusLog(row), "案件状态记录创建失败");
    }

    private void assertDict(String type, Object value, String message)
    {
        String expected = text(value); if (StringUtils.isEmpty(expected)) return;
        List<SysDictData> values = dictService.selectDictDataByType(type); if (contains(values, expected)) return;
        dictService.resetDictCache(); values = dictService.selectDictDataByType(type);
        if (values == null || values.isEmpty()) throw new ServiceException("字典未初始化：" + type);
        if (!contains(values, expected)) throw new ServiceException(message);
    }

    private boolean contains(List<SysDictData> values, String expected) { if (values != null) for (SysDictData item : values) if (expected.equals(item.getDictValue())) return true; return false; }
    private Long toLong(Object value, String message) { if (value == null || StringUtils.isEmpty(String.valueOf(value))) throw new ServiceException(message); return Long.valueOf(String.valueOf(value)); }
    private String requiredText(Object value, String message) { String result = text(value); if (StringUtils.isEmpty(result)) throw new ServiceException(message); return result; }
    private String defaultText(Object value, Object fallback) { String result = text(value); return StringUtils.isEmpty(result) ? text(fallback) : result; }
    private String text(Object value) { return value == null || "null".equalsIgnoreCase(String.valueOf(value)) ? null : String.valueOf(value).trim(); }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> list(Object value) { return value instanceof List ? (List<Map<String, Object>>) value : List.of(); }
    private void assertRows(int rows, String message) { if (rows <= 0) throw new ServiceException(message); }
}


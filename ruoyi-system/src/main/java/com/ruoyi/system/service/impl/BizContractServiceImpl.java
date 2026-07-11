package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
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
import com.ruoyi.system.service.IBizCaseService;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.contract.ContractTemplateService;
import com.ruoyi.system.service.contract.ContractNumberService;
import com.ruoyi.system.service.contract.ContractQueryService;
import com.ruoyi.system.service.contract.ContractAttachmentService;
import com.ruoyi.system.service.contract.ContractLifecycleService;
import com.ruoyi.system.service.contract.ContractInvoiceService;
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.law.business.security.ContractPermissions;

@Service
public class BizContractServiceImpl implements IBizContractService
{
    private static final String CUSTOMER_NORMAL = "0";

    private static final String CUSTOMER_MODULE_PERMISSIONS =
            "customer:list,customer:query,customer:add,customer:edit,customer:remove,customer:import,customer:export,"
                    + "customer:contact:list,customer:contact:add,customer:contact:edit,customer:contact:remove,"
                    + "customer:followup:list,customer:followup:add,customer:followup:remove,"
                    + "customer:tag:list,customer:tag:add,customer:tag:edit,customer:tag:remove,customer:tag:assign,"
                    + "customer:merge:list,customer:merge:merge";
    private static final String CONTRACT_MODULE_PERMISSIONS = ContractPermissions.DATA_SCOPE;

    private static final String AUDIT_PENDING = ContractAuditStatus.PENDING.code();
    private static final String AUDIT_REVIEWING = ContractAuditStatus.REVIEWING.code();
    private static final String AUDIT_PASSED = ContractAuditStatus.PASSED.code();
    private static final String AUDIT_REJECTED = ContractAuditStatus.REJECTED.code();
    private static final String AUDIT_BACK = ContractAuditStatus.BACK.code();

    private static final String CONTRACT_DRAFT = ContractStatus.DRAFT.code();
    private static final String CONTRACT_PERFORMING = ContractStatus.PERFORMING.code();
    private static final String CONTRACT_ARCHIVED = ContractStatus.ARCHIVED.code();
    private static final String CONTRACT_VOID = ContractStatus.VOID.code();
    private static final String CONTRACT_TERMINATED = ContractStatus.TERMINATED.code();

    private static final String SIGN_UNSIGNED = ContractSignStatus.UNSIGNED.code();
    private static final String SIGN_SIGNED = ContractSignStatus.SIGNED.code();
    private static final String SIGN_PARTIAL = ContractSignStatus.PARTIAL.code();

    private static final String RECEIVE_PENDING = "0";
    private static final String RECEIVE_CONFIRMED = "1";
    private static final String RECEIVE_REJECTED = "2";

    private static final String INVOICE_NONE = "0";
    private static final String INVOICE_DONE = "1";
    private static final String INVOICE_PARTIAL = "2";

    @Autowired
    private BizContractMapper contractMapper;

    @Autowired
    private BizCustomerMapper customerMapper;

    @Autowired
    private IBizCaseService caseService;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Autowired
    private BusinessEventPublisher eventPublisher;

    @Autowired
    private ContractTemplateService templateService;

    @Autowired
    private ContractNumberService numberService;

    @Autowired
    private ContractQueryService queryService;

    @Autowired
    private ContractAttachmentService attachmentService;

    @Autowired
    private ContractLifecycleService lifecycleService;

    @Autowired
    private ContractInvoiceService invoiceService;

    @Override
    public List<BizContract> selectContractList(BizContract contract)
    {
        return queryService.contracts(contract);
    }

    @Override
    public BizContract selectContractById(Long contractId)
    {
        BizContract contract = contractMapper.selectContractById(contractId);
        if (contract == null || "2".equals(contract.getDelFlag()))
        {
            throw new ServiceException("åˆåŒä¸å­˜åœ¨æˆ–å·²åˆ é™¤");
        }
        assertContractAccess(contractId);
        return contract;
    }

    @Override
    @Transactional
    public int insertContract(BizContract contract)
    {
        normalizeNewContract(contract);
        validateNewContract(contract);
        validateCustomer(contract);
        contract.setContractNo(numberService.nextNumber());
        contract.setCreateBy(SecurityUtils.getUsername());
        if (contract.getOwnerId() == null)
        {
            contract.setOwnerId(SecurityUtils.getUserId());
            contract.setDeptId(SecurityUtils.getDeptId());
        }
        int rows = contractMapper.insertContract(contract);
        assertRowsChanged(rows, "Contract was not created");
        insertStatusLog(contract.getContractId(), null, contract.getContractStatus(), "create", "åˆ›å»ºåˆåŒ");
        return rows;
    }

    @Override
    public int updateContract(BizContract contract)
    {
        BizContract existed = selectContractById(contract.getContractId());
        assertEditable(existed);
        normalizeContractUpdate(contract);
        validateNewContract(contract);
        validateCustomer(contract);
        contract.setContractNo(null);
        contract.setAuditStatus(null);
        contract.setContractStatus(null);
        contract.setSignStatus(null);
        contract.setUpdateBy(SecurityUtils.getUsername());
        int rows = contractMapper.updateContract(contract);
        assertRowsChanged(rows, "Contract was changed, please refresh and try again");
        return rows;
    }

    @Override
    public int deleteContractByIds(Long[] contractIds)
    {
        for (Long contractId : contractIds)
        {
            BizContract contract = selectContractById(contractId);
            if (AUDIT_REVIEWING.equals(contract.getAuditStatus()) || CONTRACT_PERFORMING.equals(contract.getContractStatus()) || CONTRACT_ARCHIVED.equals(contract.getContractStatus()))
            {
                throw new ServiceException("å½“å‰åˆåŒçŠ¶æ€ä¸å…è®¸åˆ é™¤");
            }
        }
        int rows = contractMapper.deleteContractByIds(contractIds, SecurityUtils.getUsername());
        assertRowsChanged(rows, "Contract was changed, please refresh and try again");
        return rows;
    }

    @Override
    @Transactional
    public String importContract(List<BizContract> contractList, Boolean updateSupport, String operName)
    {
        if (StringUtils.isNull(contractList) || contractList.isEmpty())
        {
            throw new ServiceException("å¯¼å…¥åˆåŒæ•°æ®ä¸èƒ½ä¸ºç©º");
        }
        int successNum = 0;
        for (BizContract contract : contractList)
        {
            if (StringUtils.isEmpty(contract.getContractName()))
            {
                continue;
            }
            resolveImportCustomer(contract);
            List<BizContract> duplicates = contractMapper.selectContractsByExactNameInScope(contract.getContractName(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(), !SecurityUtils.isAdmin(), CONTRACT_MODULE_PERMISSIONS);
            if (!duplicates.isEmpty())
            {
                if (!Boolean.TRUE.equals(updateSupport))
                {
                    throw new ServiceException("åˆåŒå·²å­˜åœ¨ï¼Œå‹¾é€‰æ›´æ–°åå¯è¦†ç›–å¯¼å…¥ï¼š" + contract.getContractName());
                }
                if (duplicates.size() > 1)
                {
                    throw new ServiceException("åˆåŒåç§°é‡å¤ï¼Œè¯·åœ¨é¡µé¢æ‰‹åŠ¨ç¼–è¾‘ï¼š" + contract.getContractName());
                }
                contract.setContractId(duplicates.get(0).getContractId());
                updateContract(contract);
            }
            else
            {
                insertContract(contract);
            }
            successNum++;
        }
        return "å¯¼å…¥æˆåŠŸï¼Œå…± " + successNum + " æ¡";
    }

    @Override
    @Transactional
    public int submitContract(Long contractId)
    {
        return lifecycleService.submit(contractId);
    }

    @Override
    @Transactional
    public int approveContract(Long contractId, String action, String opinion)
    {
        return lifecycleService.approve(contractId, action, opinion);
    }

    @Override
    @Transactional
    public int signContract(Long contractId, String signStatus)
    {
        return lifecycleService.sign(contractId, signStatus);
    }

    @Override
    @Transactional
    public int archiveContract(Long contractId, String reason)
    {
        return lifecycleService.archive(contractId, reason);
    }

    @Override
    @Transactional
    public int voidContract(Long contractId, String reason)
    {
        return lifecycleService.voidContract(contractId, reason);
    }

    @Override
    @Transactional
    public int terminateContract(Long contractId, String reason)
    {
        return lifecycleService.terminate(contractId, reason);
    }

    @Override
    public Map<String, Object> selectDashboard()
    {
        return queryService.dashboard();
    }

    @Override public List<Map<String, Object>> selectRules() { return numberService.selectRules(); }
    @Override
    @Transactional
    public int updateRule(Map<String, Object> rule) {
        return numberService.updateRule(rule);
    }
    @Override public List<Map<String, Object>> selectTemplates(Map<String, Object> params) { return templateService.select(params); }
    @Override public int insertTemplate(Map<String, Object> template) { return templateService.create(template); }
    @Override public int updateTemplate(Map<String, Object> template) { return templateService.update(template); }
    @Override public int deleteTemplate(Long templateId) { return templateService.delete(templateId); }
    @Override public List<Map<String, Object>> selectApprovals(Map<String, Object> params) { return queryService.approvals(params); }
    @Override public List<Map<String, Object>> selectFeePlans(Map<String, Object> params) { return queryService.feePlans(params); }
    @Override
    @Transactional
    public int insertFeePlan(Map<String, Object> plan) {
        Long contractId = toLong(plan.get("contractId"));
        assertContractAccess(contractId);
        requireFeeEditableContract(contractId);
        validateFeePlan(plan);
        plan.put("receivedAmount", BigDecimal.ZERO);
        plan.put("confirmStatus", RECEIVE_PENDING);
        plan.put("invoiceStatus", INVOICE_NONE);
        plan.put("createBy", SecurityUtils.getUsername());
        int rows = contractMapper.insertFeePlan(plan);
        assertRowsChanged(rows, "Fee plan was not created");
        insertStatusLog(contractId, null, RECEIVE_PENDING, "fee_create", feeContent("æ–°å¢æ”¶è´¹è®¡åˆ’", plan));
        return rows;
    }
    @Override
    @Transactional
    public int updateFeePlan(Map<String, Object> plan) {
        Map<String, Object> existed = feePlanInScope(toLong(plan.get("planId")));
        requireFeeEditableContractStatus(existed.get("contractStatus"));
        if (RECEIVE_CONFIRMED.equals(String.valueOf(existed.get("confirm_status")))) {
            throw new ServiceException("Confirmed fee plans cannot be edited by normal update");
        }
        if (!INVOICE_NONE.equals(String.valueOf(existed.get("invoice_status")))) {
            throw new ServiceException("Invoiced fee plans cannot be edited by normal update");
        }
        plan.put("contractId", existed.get("contract_id"));
        plan.remove("receivedAmount");
        plan.remove("received_amount");
        plan.remove("confirmStatus");
        plan.remove("confirm_status");
        plan.remove("invoiceStatus");
        plan.remove("invoice_status");
        validateFeePlan(plan);
        plan.put("expectedConfirmStatus", existed.get("confirm_status"));
        plan.put("expectedInvoiceStatus", existed.get("invoice_status"));
        plan.put("expectedContractStatus", existed.get("contractStatus"));
        plan.put("updateBy", SecurityUtils.getUsername());
        int rows = contractMapper.updateFeePlan(plan);
        assertRowsChanged(rows, "Fee plan was changed, please refresh and try again");
        if (RECEIVE_REJECTED.equals(String.valueOf(existed.get("confirm_status")))) {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("planId", plan.get("planId"));
            statusUpdate.put("confirmStatus", RECEIVE_PENDING);
            statusUpdate.put("expectedConfirmStatus", existed.get("confirm_status"));
            statusUpdate.put("expectedInvoiceStatus", existed.get("invoice_status"));
            statusUpdate.put("expectedContractStatus", existed.get("contractStatus"));
            statusUpdate.put("updateBy", SecurityUtils.getUsername());
            assertStateChanged(contractMapper.updateFeePlanStatus(statusUpdate));
        }
        insertStatusLog(Long.valueOf(String.valueOf(existed.get("contract_id"))), null, RECEIVE_PENDING, "fee_update", feeContent("è°ƒæ•´æ”¶è´¹è®¡åˆ’", plan));
        return rows;
    }
    @Override
    @Transactional
    public int deleteFeePlan(Long planId) {
        Map<String, Object> existed = feePlanInScope(planId);
        requireFeeEditableContractStatus(existed.get("contractStatus"));
        if (RECEIVE_CONFIRMED.equals(String.valueOf(existed.get("confirm_status"))) || !INVOICE_NONE.equals(String.valueOf(existed.get("invoice_status")))) {
            throw new ServiceException("Fee plans with received or invoiced records cannot be deleted");
        }
        int rows = contractMapper.deleteFeePlan(planId, existed.get("confirm_status"), existed.get("invoice_status"), existed.get("contractStatus"));
        assertRowsChanged(rows, "Fee plan was changed, please refresh and try again");
        insertStatusLog(Long.valueOf(String.valueOf(existed.get("contract_id"))), RECEIVE_PENDING, null, "fee_delete", "åˆ é™¤æ”¶è´¹è®¡åˆ’: ç¬¬ " + existed.get("period_no") + " æœŸ");
        return rows;
    }
    @Override
    @Transactional
    p×Í4¶‰Ëkºwµçd¤ì(€€€€€€€É•ÑÕÉ¸É½İÌì(€€€ô(€€€=Ù•ÉÉ¥‘”(€€€QÉ…¹Í…Ñ¥½¹…°(€€€ÁÕ‰±¥Œ¥¹Ğ¥¹Ù½¥•••A±…¸¡1½¹œÁ±…¹%°MÑÉ¥¹œ¥¹Ù½¥•MÑ…ÑÕÌ¤ì(€€€€€€€É•ÑÕÉ¸¥¹Ù½¥•M•ÉÙ¥”¹¥¹Ù½¥”¡Á±…¹%°¥¹Ù½¥•MÑ…ÑÕÌ°¹Õ±°°¹Õ±°¤ì(€€€ô((€€€=Ù•ÉÉ¥‘”(€€€QÉ…¹Í…Ñ¥½¹…°(€€€ÁÕ‰±¥Œ¥¹Ğ¥¹Ù½¥•••A±…¸¡1½¹œÁ±…¹%°MÑÉ¥¹œ¥¹Ù½¥•MÑ…ÑÕÌ°MÑÉ¥¹œÉ•µ…É¬¤ì(€€€€€€€É•ÑÕÉ¸¥¹Ù½¥•M•ÉÙ¥”¹¥¹Ù½¥”¡Á±…¹%°¥¹Ù½¥•MÑ…ÑÕÌ°É•µ…É¬°¹Õ±°¤ì(€€€ô((€€€=Ù•ÉÉ¥‘”(€€€QÉ…¹Í…Ñ¥½¹…°(€€€ÁÕ‰±¥Œ¥¹Ğ¥¹Ù½¥•••A±…¸¡1½¹œÁ±…¹%°MÑÉ¥¹œ¥¹Ù½¥•MÑ…ÑÕÌ°MÑÉ¥¹œÉ•µ…É¬°MÑÉ¥¹œ¥¹Ù½¥•QåÁ”¤ì(€€€€€€€É•ÑÕÉ¸¥¹Ù½¥•M•ÉÙ¥”¹¥¹Ù½¥”¡Á±…¹%°¥¹Ù½¥•MÑ…ÑÕÌ°É•µ…É¬°¥¹Ù½¥•QåÁ”¤ì(€€€ô((€€€=Ù•ÉÉ¥‘”ÁÕ‰±¥Œ1¥ÍĞñ5…ÀñMÑÉ¥¹œ°=‰©•ĞøøÍ•±•ÑÑÑ…¡µ•¹ÑÌ¡5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ…É…µÌ¤ìÉ•ÑÕÉ¸ÅÕ•ÉåM•ÉÙ¥”¹…ÑÑ…¡µ•¹ÑÌ¡Á…É…µÌ¤ìô(€€€=Ù•ÉÉ¥‘”(€€€QÉ…¹Í…Ñ¥½¹…°(€€€ÁÕ‰±¥Œ¥¹Ğ¥¹Í•ÉÑÑÑ…¡µ•¹Ğ¡5…ÀñMÑÉ¥¹œ°=‰©•Ğø…ÑÑ…¡µ•¹Ğ¤ì(€€€€€€€É•ÑÕÉ¸…ÑÑ…¡µ•¹ÑM•ÉÙ¥”¹É•…Ñ”¡…ÑÑ…¡µ•¹Ğ¤ì(€€€ô(€€€=Ù•ÉÉ¥‘”(€€€QÉ…¹Í…Ñ¥½¹…°(€€€ÁÕ‰±¥Œ¥¹Ğ‘•±•Ñ•ÑÑ…¡µ•¹Ğ¡1½¹œ…ÑÑ…¡µ•¹Ñ%¤ì(€€€€€€€É•ÑÕÉ¸…ÑÑ…¡µ•¹ÑM•ÉÙ¥”¹‘•±•Ñ”¡…ÑÑ…¡µ•¹Ñ%¤ì(€€€ô(€€€=Ù•ÉÉ¥‘”ÁÕ‰±¥Œ1¥ÍĞñ5…ÀñMÑÉ¥¹œ°=‰©•ĞøøÍ•±•ÑMÑ…ÑÕÍ1½Ì¡5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ…É…µÌ¤ìÉ•ÑÕÉ¸ÅÕ•ÉåM•ÉÙ¥”¹ÍÑ…ÑÕÍ1½Ì¡Á…É…µÌ¤ìô((€€€ÁÉ¥Ù…Ñ”Ù½¥Ù…±¥‘…Ñ•••A±…¸¡5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ±…¸¤(€€€ì(€€€€€€€%¹Ñ••ÈÁ•É¥½‘9¼€ôÁ…ÉÍ•%¹Ñ••È¡É•ÅÕ¥É•‘Q•áĞ¡Á±…¸°€‰Á•É¥½‘9¼ˆ°€‹¢¾ß¢úO–—šršVÀˆ¤°€‹šršVÃ–ş¦†ï’âëšVÃ–¶\ˆ¤ì(€€€€€€€¥˜€¡Á•É¥½‘9¼€ğô€À¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹šršVÃ–ş¦†ï–’Ÿ’ê8Àˆ¤ì(€€€€€€€ô(€€€€€€€	¥•¥µ…°É••¥Ù…‰±•µ½Õ¹Ğ€ôÁ…ÉÍ••¥µ…°¡É•ÅÕ¥É•‘Q•áĞ¡Á±…¸°€‰É••¥Ù…‰±•µ½Õ¹Ğˆ°€‹¢¾ß¢úO–—–êSšRÛ¦G¦Štˆ¤°€‹–êSšRÛ¦G¦Šw–ş¦†ï’âëšVÃ–¶\ˆ¤ì(€€€€€€€¥˜€¡É••¥Ù…‰±•µ½Õ¹Ğ¹½µÁ…É•Q¼¡	¥•¥µ…°¹iI<¤€ğô€À¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–êSšRÛ¦G¦Šw–ş¦†ï–’Ÿ’ê8Àˆ¤ì(€€€€€€€ô(€€€€€€€É•ÅÕ¥É•‘Q•áĞ¡Á±…¸°€‰Á±…¹I••¥Ù•…Ñ”ˆ°€‹¢¾ß¦'š.§¢º‡–"KšRÛš²ûš^”ˆ¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”MÑÉ¥¹œÉ•ÅÕ¥É•‘Q•áĞ¡5…ÀñMÑÉ¥¹œ°=‰©•ĞøÍ½ÕÉ”°MÑÉ¥¹œ­•ä°MÑÉ¥¹œµ•ÍÍ…”¤(€€€ì(€€€€€€€=‰©•ĞÙ…±Õ”€ôÍ½ÕÉ”€ôô¹Õ±°€ü¹Õ±°€èÍ½ÕÉ”¹•Ğ¡­•ä¤ì(€€€€€€€¥˜€¡Ù…±Õ”€ôô¹Õ±°ñğMÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Ù…±Õ”¤¤ñğ€‰¹Õ±°ˆ¹•ÅÕ…±Í%¹½É•…Í”¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Ù…±Õ”¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸¡µ•ÍÍ…”¤ì(€€€€€€€ô(€€€€€€€É•ÑÕÉ¸MÑÉ¥¹œ¹Ù…±Õ•=˜¡Ù…±Õ”¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”MÑÉ¥¹œ™••½¹Ñ•¹Ğ¡MÑÉ¥¹œ…Ñ¥½¸°5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ±…¸¤(€€€ì(€€€€€€€É•ÑÕÉ¸…Ñ¥½¸€¬€ˆèƒ²°€ˆ€¬Á±…¸¹•Ğ ‰Á•É¥½‘9¼ˆ¤€¬€ˆƒšr¾ò3–êSšRØ€ˆ€¬Á±…¸¹•Ğ ‰É••¥Ù…‰±•µ½Õ¹Ğˆ¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”MÑÉ¥¹œ…ÁÁ•¹‘I•µ…É¬¡MÑÉ¥¹œ½¹Ñ•¹Ğ°MÑÉ¥¹œÉ•µ…É¬¤(€€€ì(€€€€€€€É•ÑÕÉ¸MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡É•µ…É¬¤€ü½¹Ñ•¹Ğ€è½¹Ñ•¹Ğ€¬€‹¾òo–’B–’šÎ£¾òhˆ€¬É•µ…É¬ì(€€€ô((€€€ÁÉ¥Ù…Ñ”%¹Ñ••ÈÁ…ÉÍ•%¹Ñ••È¡MÑÉ¥¹œÙ…±Õ”°MÑÉ¥¹œµ•ÍÍ…”¤(€€€ì(€€€€€€€ÑÉä(€€€€€€€ì(€€€€€€€€€€€É•ÑÕÉ¸%¹Ñ••È¹Ù…±Õ•=˜¡Ù…±Õ”¤ì(€€€€€€€ô(€€€€€€€…Ñ €¡9Õµ‰•É½Éµ…Ñá•ÁÑ¥½¸”¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸¡µ•ÍÍ…”¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”	¥•¥µ…°Á…ÉÍ••¥µ…°¡MÑÉ¥¹œÙ…±Õ”°MÑÉ¥¹œµ•ÍÍ…”¤(€€€ì(€€€€€€€ÑÉä(€€€€€€€ì(€€€€€€€€€€€É•ÑÕÉ¸¹•Ü	¥•¥µ…°¡Ù…±Õ”¤ì(€€€€€€€ô(€€€€€€€…Ñ €¡9Õµ‰•É½Éµ…Ñá•ÁÑ¥½¸”¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸¡µ•ÍÍ…”¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥Ù…±¥‘…Ñ•ÕÍÑ½µ•È¡	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ¤(€€€ì(€€€€€€€¥˜€¡½¹ÑÉ…Ğ¹•ÑÕÍÑ½µ•É% ¤€ôô¹Õ±°¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–B#–B3–ş¦†ï–Ï¢S–º‹š"Üˆ¤ì(€€€€€€€ô(€€€€€€€	¥éÕÍÑ½µ•ÈÕÍÑ½µ•È€ôÕÍÑ½µ•É5…ÁÁ•È¹Í•±•ÑÕÍÑ½µ•É	å%¡½¹ÑÉ…Ğ¹•ÑÕÍÑ½µ•É% ¤¤ì(€€€€€€€¥˜€¡ÕÍÑ½µ•È€ôô¹Õ±°ñğ€ˆÈˆ¹•ÅÕ…±Ì¡ÕÍÑ½µ•È¹•Ñ•±±…œ ¤¤ñğ€…UMQ=5I}9=I50¹•ÅÕ…±Ì¡ÕÍÑ½µ•È¹•ÑMÑ…ÑÕÌ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–º‹š"ß’â7–¶c–r£–ŞË–sR£š"[–ŞË–"ƒ¦fˆ¤ì(€€€€€€€ô(€€€€€€€¥˜€ …M•ÕÉ¥ÑåUÑ¥±Ì¹¥Í‘µ¥¸ ¤€˜˜ÕÍÑ½µ•É5…ÁÁ•È¹½Õ¹ÑÕÍÑ½µ•É%¹…Ñ…M½Á”¡½¹ÑÉ…Ğ¹•ÑÕÍÑ½µ•É% ¤°M•ÕÉ¥ÑåUÑ¥±Ì¹•ÑUÍ•É% ¤°M•ÕÉ¥ÑåUÑ¥±Ì¹•Ñ•ÁÑ% ¤°UMQ=5I}5=U1}AI5%MM%=9L¤€ôô€À¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹š^ƒšv’âë¢¾—–º‹š"ß–"o–îëš"[ò[¢úG–B#–B0ˆ¤ì(€€€€€€€ô(€€€€€€€½¹ÑÉ…Ğ¹Í•ÑÕÍÑ½µ•É9…µ”¡ÕÍÑ½µ•È¹•ÑÕÍÑ½µ•É9…µ” ¤¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥É•Í½±Ù•%µÁ½ÉÑÕÍÑ½µ•È¡	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ¤(€€€ì(€€€€€€€¥˜€¡½¹ÑÉ…Ğ¹•ÑÕÍÑ½µ•É% ¤€„ô¹Õ±°¤(€€€€€€€ì(€€€€€€€€€€€É•ÑÕÉ¸ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•ÑÕÍÑ½µ•É9…µ” ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–¾ó–—–B#–B3–ş¦†ï–†¯–g–º‹š"ß–B7Àˆ¤ì(€€€€€€€ô(€€€€€€€1¥ÍĞñ	¥éÕÍÑ½µ•ÈøÕÍÑ½µ•ÉÌ€ôÕÍÑ½µ•É5…ÁÁ•È¹Í•±•ÑÕÍÑ½µ•ÉÍ	åá…Ñ9…µ•%¹M½Á”¡½¹ÑÉ…Ğ¹•ÑÕÍÑ½µ•É9…µ” ¤°M•ÕÉ¥ÑåUÑ¥±Ì¹•ÑUÍ•É% ¤°M•ÕÉ¥ÑåUÑ¥±Ì¹•Ñ•ÁÑ% ¤°€…M•ÕÉ¥ÑåUÑ¥±Ì¹¥Í‘µ¥¸ ¤°UMQ=5I}5=U1}AI5%MM%=9L¤ì(€€€€€€€¥˜€¡ÕÍÑ½µ•ÉÌ¹¥ÍµÁÑä ¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–º‹š"ß’â7–¶c–r£š"[š^ƒšv¢ºÿ¦^»¾òhˆ€¬½¹ÑÉ…Ğ¹•ÑÕÍÑ½µ•É9…µ” ¤¤ì(€€€€€€€ô(€€€€€€€¥˜€¡ÕÍÑ½µ•ÉÌ¹Í¥é” ¤€ø€Ä¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–º‹š"ß–B7Ã¦7–’7¾ò3¢¾ß–r£¦†×¦v‹š&/–*£¦'š.§–º‹š"ß–B;šZÃ–îë–B#–B3¾òhˆ€¬½¹ÑÉ…Ğ¹•ÑÕÍÑ½µ•É9…µ” ¤¤ì(€€€€€€€ô(€€€€€€€½¹ÑÉ…Ğ¹Í•ÑÕÍÑ½µ•É%¡ÕÍÑ½µ•ÉÌ¹•Ğ À¤¹•ÑÕÍÑ½µ•É% ¤¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥¹½Éµ…±¥é•9•İ½¹ÑÉ…Ğ¡	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ¤(€€€ì(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•Ñ••QåÁ” ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•Ñ••QåÁ”¡‘¥ÑY…±Õ” ‰±…İ}½¹ÑÉ…Ñ}™••}ÑåÁ”ˆ°€‰½¹”ˆ¤¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•ÑM¥¹5•Ñ¡½ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•ÑM¥¹5•Ñ¡½¡‘¥ÑY…±Õ” ‰±…İ}½¹ÑÉ…Ñ}Í¥¹}µ•Ñ¡½ˆ°€‰½¹±¥¹”ˆ¤¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•ÑI¥Í­1•Ù•° ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•ÑI¥Í­1•Ù•°¡‘¥ÑY…±Õ” ‰±…İ}½¹ÑÉ…Ñ}É¥Í­}±•Ù•°ˆ°€ˆÄˆ¤¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•ÑÕ‘¥ÑMÑ…ÑÕÌ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•ÑÕ‘¥ÑMÑ…ÑÕÌ¡U%Q}A9%9¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…ÑMÑ…ÑÕÌ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•Ñ½¹ÑÉ…ÑMÑ…ÑÕÌ¡=9QIQ}IP¤ì(€€€€€€€ô(€€€€€€€¥˜€¡½¹ÑÉ…Ğ¹•ÑM¥¹µ½Õ¹Ğ ¤€ôô¹Õ±°¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•ÑM¥¹µ½Õ¹Ğ¡	¥•¥µ…°¹iI<¤ì(€€€€€€€ô(€€€€€€€½¹ÑÉ…Ğ¹Í•ÑM¥¹MÑ…ÑÕÌ¡M%9}U9M%9¤ì(€€€€€€€¥˜€ …U%Q}A9%9¹•ÅÕ…±Ì¡½¹ÑÉ…Ğ¹•ÑÕ‘¥ÑMÑ…ÑÕÌ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹šZÃ–îë–B#–B3–ş¦†ï’âë–ú–º‡š‚ã*Ûšˆ¤ì(€€€€€€€ô(€€€€€€€¥˜€ …=9QIQ}IP¹•ÅÕ…±Ì¡½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…ÑMÑ…ÑÕÌ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹šZÃ–îë–B#–B3–ş¦†ï’âë¢6'¢ÿ*Ûšˆ¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥¹½Éµ…±¥é•½¹ÑÉ…ÑUÁ‘…Ñ”¡	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ¤(€€€ì(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•Ñ1…İå•É9…µ” ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•Ñ1…İå•É9…µ”¡¹Õ±°¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•Ñ••QåÁ” ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•Ñ••QåÁ”¡¹Õ±°¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•ÑM¥¹5•Ñ¡½ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•ÑM¥¹5•Ñ¡½¡¹Õ±°¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•ÑI¥Í­1•Ù•° ¤¤¤(€€€€€€€ì(€€€€€€€€€€€½¹ÑÉ…Ğ¹Í•ÑI¥Í­1•Ù•°¡¹Õ±°¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥Ù…±¥‘…Ñ•9•İ½¹ÑÉ…Ğ¡	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ¤(€€€ì(€€€€€€€¥˜€¡½¹ÑÉ…Ğ€ôô¹Õ±°¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–B#–B3’â7¢÷’âë¦èˆ¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…Ñ9…µ” ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–B#–B3–B7Ã’â7¢÷’âë¦èˆ¤ì(€€€€€€€ô(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡½¹ÑÉ…Ğ¹•Ñ…Í•QåÁ” ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹š†#’îÛÆï–z/’â7¢÷’âë¦èˆ¤ì(€€€€€€€ô(€€€€€€€…ÍÍ•ÉÑ¥ÑY…±Õ” ‰±…İ}½¹ÑÉ…Ñ}…Í•}ÑåÁ”ˆ°½¹ÑÉ…Ğ¹•Ñ…Í•QåÁ” ¤°€‹š†#’îÛÆï–z/’â7–B#šÎTˆ¤ì(€€€€€€€…ÍÍ•ÉÑ¥ÑY…±Õ” ‰±…İ}½¹ÑÉ…Ñ}™••}ÑåÁ”ˆ°½¹ÑÉ…Ğ¹•Ñ••QåÁ” ¤°€‹šRÛ¢ÒçšZç–ò?’â7–B#šÎTˆ¤ì(€€€€€€€…ÍÍ•ÉÑ¥ÑY…±Õ” ‰±…İ}½¹ÑÉ…Ñ}Í¥¹}µ•Ñ¡½ˆ°½¹ÑÉ…Ğ¹•ÑM¥¹5•Ñ¡½ ¤°€‹¶û¢º‹šZç–ò?’â7–B#šÎTˆ¤ì(€€€€€€€…ÍÍ•ÉÑ¥ÑY…±Õ” ‰±…İ}½¹ÑÉ…Ñ}É¥Í­}±•Ù•°ˆ°½¹ÑÉ…Ğ¹•ÑI¥Í­1•Ù•° ¤°€‹¦;¦f§¶'êŸ’â7–B#šÎTˆ¤ì(€€€€€€€¥˜€¡½¹ÑÉ…Ğ¹•ÑM¥¹µ½Õ¹Ğ ¤€ôô¹Õ±°ñğ½¹ÑÉ…Ğ¹•ÑM¥¹µ½Õ¹Ğ ¤¹½µÁ…É•Q¼¡	¥•¥µ…°¹iI<¤€ğô€À¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹¶ûê›¦G¦Šw–ş¦†ï–’Ÿ’ê8Àˆ¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”MÑÉ¥¹œ‘¥ÑY…±Õ”¡MÑÉ¥¹œ‘¥ÑQåÁ”°MÑÉ¥¹œÁÉ•™•ÉÉ•‘Y…±Õ”¤(€€€ì(€€€€€€€1¥ÍĞñMåÍ¥Ñ…Ñ„ø½ÁÑ¥½¹Ì€ô‘¥ÑQåÁ•M•ÉÙ¥”¹Í•±•Ñ¥Ñ…Ñ…	åQåÁ”¡‘¥ÑQåÁ”¤ì(€€€€€€€¥˜€¡½ÁÑ¥½¹Ì€„ô¹Õ±°¤(€€€€€€€ì(€€€€€€€€€€€™½È€¡MåÍ¥Ñ…Ñ„¥Ñ•´€è½ÁÑ¥½¹Ì¤(€€€€€€€€€€€ì(€€€€€€€€€€€€€€€¥˜€¡ÁÉ•™•ÉÉ•‘Y…±Õ”¹•ÅÕ…±Ì¡¥Ñ•´¹•Ñ¥ÑY…±Õ” ¤¤¤(€€€€€€€€€€€€€€€ì(€€€€€€€€€€€€€€€€€€€É•ÑÕÉ¸¥Ñ•´¹•Ñ¥ÑY…±Õ” ¤ì(€€€€€€€€€€€€€€€ô(€€€€€€€€€€€ô(€€€€€€€€€€€™½È€¡MåÍ¥Ñ…Ñ„¥Ñ•´€è½ÁÑ¥½¹Ì¤(€€€€€€€€€€€ì(€€€€€€€€€€€€€€€¥˜€¡¥Ñ•´¹•Ñ•™…Õ±Ğ ¤¤(€€€€€€€€€€€€€€€ì(€€€€€€€€€€€€€€€€€€€É•ÑÕÉ¸¥Ñ•´¹•Ñ¥ÑY…±Õ” ¤ì(€€€€€€€€€€€€€€€ô(€€€€€€€€€€€ô(€€€€€€€ô(€€€€€€€É•ÑÕÉ¸ÁÉ•™•ÉÉ•‘Y…±Õ”ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥…ÍÍ•ÉÑ¥ÑY…±Õ”¡MÑÉ¥¹œ‘¥ÑQåÁ”°=‰©•ĞÙ…±Õ”°MÑÉ¥¹œµ•ÍÍ…”¤(€€€ì(€€€€€€€MÑÉ¥¹œÙ…±Õ•Q•áĞ€ôÙ…±Õ”€ôô¹Õ±°€ü¹Õ±°€èMÑÉ¥¹œ¹Ù…±Õ•=˜¡Ù…±Õ”¤¹ÑÉ¥´ ¤ì(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡Ù…±Õ•Q•áĞ¤¤(€€€€€€€ì(€€€€€€€€€€€É•ÑÕÉ¸ì(€€€€€€€ô(€€€€€€€1¥ÍĞñMåÍ¥Ñ…Ñ„ø½ÁÑ¥½¹Ì€ô‘¥ÑQåÁ•M•ÉÙ¥”¹Í•±•Ñ¥Ñ…Ñ…	åQåÁ”¡‘¥ÑQåÁ”¤ì(€€€€€€€¥˜€¡½ÁÑ¥½¹Ì€ôô¹Õ±°ñğ½ÁÑ¥½¹Ì¹¥ÍµÁÑä ¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–¶_–ãšr«–"w–/–2[¾òhˆ€¬‘¥ÑQåÁ”¤ì(€€€€€€€ô(€€€€€€€™½È€¡MåÍ¥Ñ…Ñ„¥Ñ•´€è½ÁÑ¥½¹Ì¤(€€€€€€€ì(€€€€€€€€€€€¥˜€¡Ù…±Õ•Q•áĞ¹•ÅÕ…±Ì¡¥Ñ•´¹•Ñ¥ÑY…±Õ” ¤¤¤(€€€€€€€€€€€ì(€€€€€€€€€€€€€€€É•ÑÕÉ¸ì(€€€€€€€€€€€ô(€€€€€€€ô(€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸¡µ•ÍÍ…”¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥…ÍÍ•ÉÑMÑ…Ñ•¡…¹•¡¥¹ĞÉ½İÌ¤(€€€ì(€€€€€€€…ÍÍ•ÉÑI½İÍ¡…¹•¡É½İÌ°€‹–B#–B3*Ûš–ŞË–>c–2[¾ò3¢¾ß–"ßšZÃ–B;¦7¢¾Tˆ¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥…ÍÍ•ÉÑI½İÍ¡…¹•¡¥¹ĞÉ½İÌ°MÑÉ¥¹œµ•ÍÍ…”¤(€€€ì(€€€€€€€¥˜€¡É½İÌ€ğô€À¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸¡µ•ÍÍ…”¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥¥¹Í•ÉÑMÑ…ÑÕÍ1½œ¡1½¹œ½¹ÑÉ…Ñ%°MÑÉ¥¹œ™É½µMÑ…ÑÕÌ°MÑÉ¥¹œÑ½MÑ…ÑÕÌ°MÑÉ¥¹œ…Ñ¥½¹QåÁ”°MÑÉ¥¹œ½¹Ñ•¹Ğ¤(€€€ì(€€€€€€€…ÍÍ•ÉÑ¥ÑY…±Õ” ‰±…İ}½¹ÑÉ…Ñ}ÍÑ…ÑÕÍ}…Ñ¥½¸ˆ°…Ñ¥½¹QåÁ”°€‹–B#–B3*Ûš–*£’ös’â7–B#šÎTˆ¤ì(€€€€€€€…ÍÍ•ÉÑI½İÍ¡…¹•¡½¹ÑÉ…Ñ5…ÁÁ•È¹¥¹Í•ÉÑMÑ…ÑÕÍ1½œ¡½¹ÑÉ…Ñ%°™É½µMÑ…ÑÕÌ°Ñ½MÑ…ÑÕÌ°…Ñ¥½¹QåÁ”°½¹Ñ•¹Ğ°M•ÕÉ¥ÑåUÑ¥±Ì¹•ÑUÍ•É¹…µ” ¤¤°€‰½¹ÑÉ…ĞÍÑ…ÑÕÌ±½œİ…Ì¹½ĞÉ•…Ñ•ˆ¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥…ÍÍ•ÉÑ‘¥Ñ…‰±”¡	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ¤(€€€ì(€€€€€€€¥˜€¡U%Q}IY%]%9¹•ÅÕ…±Ì¡½¹ÑÉ…Ğ¹•ÑÕ‘¥ÑMÑ…ÑÕÌ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–º‡š‚ã’â·j–B#–B3’â7–¢ºãò[¢úDˆ¤ì(€€€€€€€ô(€€€€€€€¥˜€¡U%Q}AMM¹•ÅÕ…±Ì¡½¹ÑÉ…Ğ¹•ÑÕ‘¥ÑMÑ…ÑÕÌ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–º‡š‚ã¦k¢şj–B#–B3’â7–¢ºãnÓš:—ò[¢úDˆ¤ì(€€€€€€€ô(€€€€€€€¥˜€¡=9QIQ}I!%Y¹•ÅÕ…±Ì¡½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…ÑMÑ…ÑÕÌ ¤¤ñğ=9QIQ}Y=%¹•ÅÕ…±Ì¡½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…ÑMÑ…ÑÕÌ ¤¤ñğ=9QIQ}QI5%9Q¹•ÅÕ…±Ì¡½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…ÑMÑ…ÑÕÌ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–öKš†’ös–êš"[î#š¶‹j–B#–B3’â7–¢ºãò[¢úDˆ¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”MÑÉ¥¹œÍ…™•I•…Í½¸¡MÑÉ¥¹œÉ•…Í½¸°MÑÉ¥¹œ‘•™…Õ±ÑY…±Õ”¤(€€€ì(€€€€€€€É•ÑÕÉ¸MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡É•…Í½¸¤ñğ€‰¹Õ±°ˆ¹•ÅÕ…±Í%¹½É•…Í”¡É•…Í½¸¤€ü‘•™…Õ±ÑY…±Õ”€èÉ•…Í½¸ì(€€€ô((€€€ÁÉ¥Ù…Ñ”MÑÉ¥¹œÉ•ÅÕ¥É•‘I•…Í½¸¡MÑÉ¥¹œÉ•…Í½¸°MÑÉ¥¹œµ•ÍÍ…”¤(€€€ì(€€€€€€€¥˜€¡MÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡É•…Í½¸¤ñğ€‰¹Õ±°ˆ¹•ÅÕ…±Í%¹½É•…Í”¡É•…Í½¸¹ÑÉ¥´ ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸¡µ•ÍÍ…”¤ì(€€€€€€€ô(€€€€€€€É•ÑÕÉ¸É•…Í½¸¹ÑÉ¥´ ¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥…ÁÁ±å…Ñ…M½Á”¡	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ¤(€€€ì(€€€€€€€½¹ÑÉ…Ğ¹Í•ÑÕÉÉ•¹ÑUÍ•É%¡M•ÕÉ¥ÑåUÑ¥±Ì¹•ÑUÍ•É% ¤¤ì(€€€€€€€½¹ÑÉ…Ğ¹Í•ÑÕÉÉ•¹Ñ•ÁÑ%¡M•ÕÉ¥ÑåUÑ¥±Ì¹•Ñ•ÁÑ% ¤¤ì(€€€€€€€½¹ÑÉ…Ğ¹Í•Ñ…Ñ…M½Á” …M•ÕÉ¥ÑåUÑ¥±Ì¹¥Í‘µ¥¸ ¤¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”1½¹œÑ½1½¹œ¡=‰©•ĞÙ…±Õ”¤(€€€ì(€€€€€€€¥˜€¡Ù…±Õ”€ôô¹Õ±°ñğMÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Ù…±Õ”¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹¢¾ß¦'š.§–B#–B0ˆ¤ì(€€€€€€€ô(€€€€€€€É•ÑÕÉ¸1½¹œ¹Ù…±Õ•=˜¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Ù…±Õ”¤¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”5…ÀñMÑÉ¥¹œ°=‰©•Ğø™••A±…¹%¹M½Á”¡1½¹œÁ±…¹%¤(€€€ì(€€€€€€€5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ±…¸€ô½¹ÑÉ…Ñ5…ÁÁ•È¹Í•±•Ñ••A±…¹	å%¡Á±…¹%¤ì(€€€€€€€¥˜€¡Á±…¸€ôô¹Õ±°¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹šRÛ¢Òç¢º‡–"K’â7–¶c–r ˆ¤ì(€€€€€€€ô(€€€€€€€…ÍÍ•ÉÑ½¹ÑÉ…Ñ•ÍÌ¡1½¹œ¹Ù…±Õ•=˜¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Á±…¸¹•Ğ ‰½¹ÑÉ…Ñ}¥ˆ¤¤¤¤ì(€€€€€€€É•ÑÕÉ¸Á±…¸ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥É•ÅÕ¥É•••‘¥Ñ…‰±•½¹ÑÉ…Ğ¡1½¹œ½¹ÑÉ…Ñ%¤(€€€ì(€€€€€€€	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ€ôÍ•±•Ñ½¹ÑÉ…Ñ	å%¡½¹ÑÉ…Ñ%¤ì(€€€€€€€É•ÅÕ¥É•••‘¥Ñ…‰±•½¹ÑÉ…ÑMÑ…ÑÕÌ¡½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…ÑMÑ…ÑÕÌ ¤¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥É•ÅÕ¥É•••‘¥Ñ…‰±•½¹ÑÉ…ÑMÑ…ÑÕÌ¡=‰©•Ğ½¹ÑÉ…ÑMÑ…ÑÕÌ¤(€€€ì(€€€€€€€MÑÉ¥¹œÍÑ…ÑÕÌ€ôMÑÉ¥¹œ¹Ù…±Õ•=˜¡½¹ÑÉ…ÑMÑ…ÑÕÌ¤ì(€€€€€€€¥˜€¡=9QIQ}I!%Y¹•ÅÕ…±Ì¡ÍÑ…ÑÕÌ¤ñğ=9QIQ}Y=%¹•ÅÕ…±Ì¡ÍÑ…ÑÕÌ¤ñğ=9QIQ}QI5%9Q¹•ÅÕ…±Ì¡ÍÑ…ÑÕÌ¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–öKš†’ös–êš"[î#š¶‹j–B#–B3’â7–¢ºãîÓš*“šRÛ¢Òç¢º‡–"Hˆ¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥É•ÅÕ¥É•••½±±•Ñ…‰±•½¹ÑÉ…ÑMÑ…ÑÕÌ¡=‰©•Ğ½¹ÑÉ…ÑMÑ…ÑÕÌ¤(€€€ì(€€€€€€€¥˜€ …=9QIQ}AI=I5%9¹•ÅÕ…±Ì¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡½¹ÑÉ…ÑMÑ…ÑÕÌ¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–>«šr'–Æ—ê›’â·j–B#–B3–>¿’î—†»¢º“šRÛš²ûš"[–ò– ˆ¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”	¥•¥µ…°Á…ÉÍ•µ½Õ¹Ğ¡MÑÉ¥¹œÙ…±Õ”°=‰©•Ğ‘•™…Õ±ÑY…±Õ”¤(€€€ì(€€€€€€€=‰©•ĞÍ½ÕÉ”€ôMÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡Ù…±Õ”¤ñğ€‰¹Õ±°ˆ¹•ÅÕ…±Í%¹½É•…Í”¡Ù…±Õ”¤€ü‘•™…Õ±ÑY…±Õ”€èÙ…±Õ”ì(€€€€€€€¥˜€¡Í½ÕÉ”€ôô¹Õ±°ñğMÑÉ¥¹UÑ¥±Ì¹¥ÍµÁÑä¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Í½ÕÉ”¤¤¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹¢¾ß¢úO–—–º{šRÛ¦G¦Štˆ¤ì(€€€€€€€ô(€€€€€€€É•ÑÕÉ¸Á…ÉÍ••¥µ…°¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Í½ÕÉ”¤°€‹–º{šRÛ¦G¦Šw–ş¦†ï’âëšVÃ–¶\ˆ¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥…ÍÍ•ÉÑ½¹ÑÉ…Ñ•ÍÌ¡1½¹œ½¹ÑÉ…Ñ%¤(€€€ì(€€€€€€€¥˜€¡½¹ÑÉ…Ñ%€ôô¹Õ±°¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹–B#–B3’â7–¶c–r£š"[–ŞË–"ƒ¦fˆ¤ì(€€€€€€€ô(€€€€€€€¥˜€¡M•ÕÉ¥ÑåUÑ¥±Ì¹¥Í‘µ¥¸ ¤¤(€€€€€€€ì(€€€€€€€€€€€É•ÑÕÉ¸ì(€€€€€€€ô(€€€€€€€¥˜€¡½¹ÑÉ…Ñ5…ÁÁ•È¹½Õ¹Ñ½¹ÑÉ…Ñ%¹…Ñ…M½Á”¡½¹ÑÉ…Ñ%°M•ÕÉ¥ÑåUÑ¥±Ì¹•ÑUÍ•É% ¤°M•ÕÉ¥ÑåUÑ¥±Ì¹•Ñ•ÁÑ% ¤°=9QIQ}5=U1}AI5%MM%=9L¤€ôô€À¤(€€€€€€€ì(€€€€€€€€€€€Ñ¡É½Ü¹•ÜM•ÉÙ¥•á•ÁÑ¥½¸ ‹š^ƒšv¢ºÿ¦^»¢¾—–B#–B0ˆ¤ì(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥ÁÕ‰±¥Í ¡	ÕÍ¥¹•ÍÍÙ•¹ÑQåÁ”ÑåÁ”°	¥é½¹ÑÉ…Ğ½¹ÑÉ…Ğ°5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ…å±½…¤(€€€ì(€€€€€€€•Ù•¹ÑAÕ‰±¥Í¡•È¹ÁÕ‰±¥Í ¡¹•Ü	ÕÍ¥¹•ÍÍÙ•¹Ñ½µµ…¹¡ÑåÁ”°€‰=9QIPˆ°½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…Ñ% ¤°(€€€€€€€€€€€€€€€½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…Ñ9¼ ¤°ÑåÁ”¹¹…µ” ¤€¬€ˆèˆ€¬½¹ÑÉ…Ğ¹•Ñ½¹ÑÉ…Ñ% ¤€¬€ˆèˆ€¬%‘UÑ¥±Ì¹™…ÍÑUU% ¤°Á…å±½…¤¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”Ù½¥ÁÕ‰±¥Í¡½ÉA±…¸¡	ÕÍ¥¹•ÍÍÙ•¹ÑQåÁ”ÑåÁ”°5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ±…¸°5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ…å±½…¤(€€€ì(€€€€€€€1½¹œ½¹ÑÉ…Ñ%€ô1½¹œ¹Ù…±Õ•=˜¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Á±…¸¹•Ğ ‰½¹ÑÉ…Ñ}¥ˆ¤¤¤ì(€€€€€€€MÑÉ¥¹œ½¹ÑÉ…Ñ9¼€ôÁ±…¸¹•Ğ ‰½¹ÑÉ…Ñ}¹¼ˆ¤€ôô¹Õ±°€ü¹Õ±°€èMÑÉ¥¹œ¹Ù…±Õ•=˜¡Á±…¸¹•Ğ ‰½¹ÑÉ…Ñ}¹¼ˆ¤¤ì(€€€€€€€•Ù•¹ÑAÕ‰±¥Í¡•È¹ÁÕ‰±¥Í ¡¹•Ü	ÕÍ¥¹•ÍÍÙ•¹Ñ½µµ…¹¡ÑåÁ”°€‰=9QIPˆ°½¹ÑÉ…Ñ%°½¹ÑÉ…Ñ9¼°(€€€€€€€€€€€€€€€ÑåÁ”¹¹…µ” ¤€¬€ˆèˆ€¬½¹ÑÉ…Ñ%€¬€ˆèˆ€¬%‘UÑ¥±Ì¹™…ÍÑUU% ¤°Á…å±½…¤¤ì(€€€ô((€€€ÁÉ¥Ù…Ñ”5…ÀñMÑÉ¥¹œ°=‰©•Ğø•Ù•¹ÑA…å±½…¡=‰©•Ğ¸¸¸Ù…±Õ•Ì¤(€€€ì(€€€€€€€5…ÀñMÑÉ¥¹œ°=‰©•ĞøÁ…å±½…€ô¹•Ü!…Í¡5…Àğø ¤ì(€€€€€€€™½È€¡¥¹Ğ¤€ô€Àì¤€¬€Ä€ğÙ…±Õ•Ì¹±•¹Ñ ì¤€¬ô€È¤(€€€€€€€ì(€€€€€€€€€€€¥˜€¡Ù…±Õ•Ím¤€¬€Åt€„ô¹Õ±°¤Á…å±½…¹ÁÕĞ¡MÑÉ¥¹œ¹Ù…±Õ•=˜¡Ù…±Õ•Ím¥t¤°Ù…±Õ•Ím¤€¬€Åt¤ì(€€€€€€€ô(€€€€€€€É•ÑÕÉ¸Á…å±½…ì(€€€ô)ô
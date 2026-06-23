package com.ruoyi.web.controller.contract;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/contract")
public class BizContractController extends BaseController
{
    @Autowired
    private IBizContractService contractService;

    @Autowired
    private ISysUserService userService;

    @PreAuthorize("@ss.hasAnyPermi('contract:add,contract:edit,contract:list,contract:query,contract:approval:list')")
    @GetMapping("/owner/options")
    public AjaxResult ownerOptions()
    {
        return success(userService.selectUserList(new SysUser()));
    }

    @PreAuthorize("@ss.hasAnyPermi('contract:list,contract:query')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard()
    {
        return success(contractService.selectDashboard());
    }

    @PreAuthorize("@ss.hasAnyPermi('contract:list,contract:query,contract:approval:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizContract contract)
    {
        startPage();
        return getDataTable(contractService.selectContractList(contract));
    }

    @PreAuthorize("@ss.hasPermi('contract:query')")
    @GetMapping("/{contractId}")
    public AjaxResult getInfo(@PathVariable Long contractId)
    {
        return success(contractService.selectContractById(contractId));
    }

    @Log(title = "contract", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('contract:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, BizContract contract)
    {
        ExcelUtil<BizContract> util = new ExcelUtil<>(BizContract.class);
        util.exportExcel(response, contractService.selectContractList(contract), "contract");
    }

    @Log(title = "contract", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('contract:import')")
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        ExcelUtil<BizContract> util = new ExcelUtil<>(BizContract.class);
        List<BizContract> contractList = util.importExcel(file.getInputStream());
        return success(contractService.importContract(contractList, updateSupport, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('contract:import')")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<BizContract> util = new ExcelUtil<>(BizContract.class);
        util.importTemplateExcel(response, "contract");
    }

    @Log(title = "contract", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('contract:add')")
    @PostMapping
    public AjaxResult add(@RequestBody BizContract contract)
    {
        return toAjax(contractService.insertContract(contract));
    }

    @Log(title = "contract", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('contract:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody BizContract contract)
    {
        return toAjax(contractService.updateContract(contract));
    }

    @Log(title = "contract", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('contract:remove')")
    @DeleteMapping("/{contractIds}")
    public AjaxResult remove(@PathVariable Long[] contractIds)
    {
        return toAjax(contractService.deleteContractByIds(contractIds));
    }

    @PreAuthorize("@ss.hasPermi('contract:submit')")
    @Log(title = "contract-submit", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{contractId}")
    public AjaxResult submit(@PathVariable Long contractId)
    {
        return toAjax(contractService.submitContract(contractId));
    }

    @PreAuthorize("@ss.hasPermi('contract:approval:handle')")
    @Log(title = "contract-approval", businessType = BusinessType.UPDATE)
    @PostMapping("/approval")
    public AjaxResult approval(@RequestBody Map<String, Object> body)
    {
        Long contractId = requiredLong(body, "contractId", "请选择合同");
        String action = text(body, "action");
        String opinion = text(body, "opinion");
        return toAjax(contractService.approveContract(contractId, action, opinion));
    }

    @PreAuthorize("@ss.hasPermi('contract:sign')")
    @Log(title = "contract-sign", businessType = BusinessType.UPDATE)
    @PostMapping("/sign")
    public AjaxResult sign(@RequestBody Map<String, Object> body)
    {
        Long contractId = requiredLong(body, "contractId", "请选择合同");
        String signStatus = text(body, "signStatus");
        return toAjax(contractService.signContract(contractId, signStatus));
    }

    @PreAuthorize("@ss.hasPermi('contract:archive')")
    @Log(title = "contract-archive", businessType = BusinessType.UPDATE)
    @PostMapping("/archive")
    public AjaxResult archive(@RequestBody Map<String, Object> body)
    {
        Long contractId = requiredLong(body, "contractId", "请选择合同");
        String reason = text(body, "reason");
        return toAjax(contractService.archiveContract(contractId, reason));
    }

    @PreAuthorize("@ss.hasPermi('contract:void')")
    @Log(title = "contract-void", businessType = BusinessType.UPDATE)
    @PostMapping("/void")
    public AjaxResult voidContract(@RequestBody Map<String, Object> body)
    {
        Long contractId = requiredLong(body, "contractId", "请选择合同");
        String reason = text(body, "reason");
        return toAjax(contractService.voidContract(contractId, reason));
    }

    @PreAuthorize("@ss.hasPermi('contract:terminate')")
    @Log(title = "contract-terminate", businessType = BusinessType.UPDATE)
    @PostMapping("/terminate")
    public AjaxResult terminate(@RequestBody Map<String, Object> body)
    {
        Long contractId = requiredLong(body, "contractId", "请选择合同");
        String reason = text(body, "reason");
        return toAjax(contractService.terminateContract(contractId, reason));
    }

    @PreAuthorize("@ss.hasPermi('contract:rule:list')")
    @GetMapping("/rule/list")
    public AjaxResult ruleList()
    {
        return success(contractService.selectRules());
    }

    @PreAuthorize("@ss.hasPermi('contract:rule:edit')")
    @Log(title = "contract-rule", businessType = BusinessType.UPDATE)
    @PutMapping("/rule")
    public AjaxResult editRule(@RequestBody Map<String, Object> rule)
    {
        return toAjax(contractService.updateRule(rule));
    }

    @PreAuthorize("@ss.hasPermi('contract:template:list')")
    @GetMapping("/template/list")
    public TableDataInfo templateList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(contractService.selectTemplates(params));
    }

    @PreAuthorize("@ss.hasPermi('contract:template:add')")
    @Log(title = "contract-template", businessType = BusinessType.INSERT)
    @PostMapping("/template")
    public AjaxResult addTemplate(@RequestBody Map<String, Object> template)
    {
        return toAjax(contractService.insertTemplate(template));
    }

    @PreAuthorize("@ss.hasPermi('contract:template:edit')")
    @Log(title = "contract-template", businessType = BusinessType.UPDATE)
    @PutMapping("/template")
    public AjaxResult editTemplate(@RequestBody Map<String, Object> template)
    {
        return toAjax(contractService.updateTemplate(template));
    }

    @PreAuthorize("@ss.hasPermi('contract:template:remove')")
    @Log(title = "contract-template", businessType = BusinessType.DELETE)
    @DeleteMapping("/template/{templateId}")
    public AjaxResult removeTemplate(@PathVariable Long templateId)
    {
        return toAjax(contractService.deleteTemplate(templateId));
    }

    @PreAuthorize("@ss.hasPermi('contract:approval:list')")
    @GetMapping("/approval/list")
    public TableDataInfo approvalList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(contractService.selectApprovals(params));
    }

    @PreAuthorize("@ss.hasPermi('contract:fee:list')")
    @GetMapping("/fee/list")
    public TableDataInfo feeList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(contractService.selectFeePlans(params));
    }

    @PreAuthorize("@ss.hasPermi('contract:fee:add')")
    @Log(title = "contract-fee", businessType = BusinessType.INSERT)
    @PostMapping("/fee")
    public AjaxResult addFee(@RequestBody Map<String, Object> plan)
    {
        return toAjax(contractService.insertFeePlan(plan));
    }

    @PreAuthorize("@ss.hasPermi('contract:fee:edit')")
    @Log(title = "contract-fee", businessType = BusinessType.UPDATE)
    @PutMapping("/fee")
    public AjaxResult editFee(@RequestBody Map<String, Object> plan)
    {
        return toAjax(contractService.updateFeePlan(plan));
    }

    @PreAuthorize("@ss.hasPermi('contract:fee:remove')")
    @Log(title = "contract-fee", businessType = BusinessType.DELETE)
    @DeleteMapping("/fee/{planId}")
    public AjaxResult removeFee(@PathVariable Long planId)
    {
        return toAjax(contractService.deleteFeePlan(planId));
    }

    @PreAuthorize("@ss.hasPermi('contract:fee:confirm')")
    @Log(title = "contract-fee-confirm", businessType = BusinessType.UPDATE)
    @PostMapping("/fee/confirm")
    public AjaxResult confirmFee(@RequestBody Map<String, Object> body)
    {
        Long planId = requiredLong(body, "planId", "请选择收费计划");
        String receivedAmount = text(body, "receivedAmount");
        return toAjax(contractService.confirmFeePlan(planId, receivedAmount));
    }

    @PreAuthorize("@ss.hasPermi('contract:fee:reject')")
    @Log(title = "contract-fee-reject", businessType = BusinessType.UPDATE)
    @PostMapping("/fee/reject")
    public AjaxResult rejectFee(@RequestBody Map<String, Object> body)
    {
        Long planId = requiredLong(body, "planId", "请选择收费计划");
        String reason = text(body, "reason");
        return toAjax(contractService.rejectFeePlan(planId, reason));
    }

    @PreAuthorize("@ss.hasPermi('contract:fee:invoice')")
    @Log(title = "contract-fee-invoice", businessType = BusinessType.UPDATE)
    @PostMapping("/fee/invoice")
    public AjaxResult invoiceFee(@RequestBody Map<String, Object> body)
    {
        Long planId = requiredLong(body, "planId", "请选择收费计划");
        String invoiceStatus = text(body, "invoiceStatus");
        return toAjax(contractService.invoiceFeePlan(planId, invoiceStatus));
    }

    @PreAuthorize("@ss.hasPermi('contract:attachment:list')")
    @GetMapping("/attachment/list")
    public TableDataInfo attachmentList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(contractService.selectAttachments(params));
    }

    @PreAuthorize("@ss.hasPermi('contract:attachment:add')")
    @Log(title = "contract-attachment", businessType = BusinessType.INSERT)
    @PostMapping("/attachment")
    public AjaxResult addAttachment(@RequestBody Map<String, Object> attachment)
    {
        return toAjax(contractService.insertAttachment(attachment));
    }

    @PreAuthorize("@ss.hasPermi('contract:attachment:remove')")
    @Log(title = "contract-attachment", businessType = BusinessType.DELETE)
    @DeleteMapping("/attachment/{attachmentId}")
    public AjaxResult removeAttachment(@PathVariable Long attachmentId)
    {
        return toAjax(contractService.deleteAttachment(attachmentId));
    }

    @PreAuthorize("@ss.hasPermi('contract:status:list')")
    @GetMapping("/status/list")
    public TableDataInfo statusList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(contractService.selectStatusLogs(params));
    }

    private Long requiredLong(Map<String, Object> body, String key, String message)
    {
        Object value = body == null ? null : body.get(key);
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
        {
            throw new ServiceException(message);
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String text(Map<String, Object> body, String key)
    {
        Object value = body == null ? null : body.get(key);
        return value == null || "null".equalsIgnoreCase(String.valueOf(value)) ? null : String.valueOf(value);
    }
}

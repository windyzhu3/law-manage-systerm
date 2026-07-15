package com.ruoyi.web.controller.lawcase;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.law.business.lawcase.dto.CaseAssignmentCommand;
import com.law.business.lawcase.dto.CaseBatchAssignmentCommand;
import com.law.business.lawcase.dto.CaseConfirmCommand;
import com.law.business.lawcase.dto.CaseTransferApprovalCommand;
import com.law.business.lawcase.dto.CaseTransferCommand;
import com.law.business.lawcase.dto.LawyerProfileSaveCommand;
import com.law.business.lawcase.dto.LawyerProfileStatusCommand;
import com.ruoyi.system.service.IBizCaseService;

@RestController
@RequestMapping("/case")
public class BizCaseController extends BaseController
{
    @Autowired
    private IBizCaseService caseService;

    @PreAuthorize("@ss.hasAnyPermi('case:pending:list,case:pending:query,case:pending:assign,case:lawyer:list,case:lawyer:query')")
    @GetMapping("/lawyer/options")
    public AjaxResult lawyerOptions()
    {
        return success(caseService.selectLawyerLoads(Map.of()));
    }

    @PreAuthorize("@ss.hasAnyPermi('case:pending:list,case:pending:query,case:lawyer:list,case:lawyer:query')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard()
    {
        return success(caseService.selectDashboard());
    }

    @PreAuthorize("@ss.hasAnyPermi('case:pending:list,case:pending:query,case:assign:list,case:assign:query')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(caseService.selectCaseList(params));
    }

    @PreAuthorize("@ss.hasAnyPermi('case:pending:query,case:assign:query')")
    @GetMapping("/{caseId}")
    public AjaxResult getInfo(@PathVariable Long caseId)
    {
        return success(caseService.selectCaseById(caseId));
    }

    @PreAuthorize("@ss.hasPermi('case:pending:assign')")
    @Log(title = "case-assign", businessType = BusinessType.UPDATE)
    @PostMapping("/assign")
    public AjaxResult assign(@Valid @RequestBody CaseAssignmentCommand command)
    {
        return toAjax(caseService.assignCase(command));
    }

    @PreAuthorize("@ss.hasPermi('case:pending:batchAssign')")
    @Log(title = "case-batch-assign", businessType = BusinessType.UPDATE)
    @PostMapping("/assign/batch")
    public AjaxResult batchAssign(@Valid @RequestBody CaseBatchAssignmentCommand command)
    {
        return toAjax(caseService.batchAssignCases(command));
    }

    @PreAuthorize("@ss.hasPermi('case:assign:list')")
    @GetMapping("/assign/list")
    public TableDataInfo assignList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(caseService.selectAssignments(params));
    }

    @PreAuthorize("@ss.hasPermi('case:lawyer:list')")
    @GetMapping("/lawyer/load")
    public TableDataInfo lawyerLoad(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(caseService.selectLawyerLoads(params));
    }

    @PreAuthorize("@ss.hasPermi('case:lawyer:list')")
    @GetMapping("/lawyer/specialty")
    public AjaxResult lawyerSpecialty(@RequestParam Map<String, Object> params)
    {
        return success(caseService.selectLawyerSpecialtyStats(params));
    }

    @PreAuthorize("@ss.hasPermi('case:lawyer:config')")
    @GetMapping("/lawyer/profile/list")
    public TableDataInfo lawyerProfileList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(caseService.selectLawyerProfiles(params));
    }

    @PreAuthorize("@ss.hasPermi('case:lawyer:config')")
    @GetMapping("/lawyer/profile/{userId}")
    public AjaxResult lawyerProfile(@PathVariable Long userId)
    {
        return success(caseService.selectLawyerProfileByUserId(userId));
    }

    @PreAuthorize("@ss.hasPermi('case:lawyer:config')")
    @Log(title = "case-lawyer-profile", businessType = BusinessType.INSERT)
    @PostMapping("/lawyer/profile")
    public AjaxResult addLawyerProfile(@Valid @RequestBody LawyerProfileSaveCommand body)
    {
        return toAjax(caseService.saveLawyerProfile(body));
    }

    @PreAuthorize("@ss.hasPermi('case:lawyer:config')")
    @Log(title = "case-lawyer-profile", businessType = BusinessType.UPDATE)
    @PutMapping("/lawyer/profile")
    public AjaxResult editLawyerProfile(@Valid @RequestBody LawyerProfileSaveCommand body)
    {
        return toAjax(caseService.saveLawyerProfile(body));
    }

    @PreAuthorize("@ss.hasPermi('case:lawyer:config')")
    @Log(title = "case-lawyer-profile-status", businessType = BusinessType.UPDATE)
    @PutMapping("/lawyer/profile/status")
    public AjaxResult updateLawyerProfileStatus(@Valid @RequestBody LawyerProfileStatusCommand body)
    {
        return toAjax(caseService.updateLawyerProfileStatus(body));
    }

    @PreAuthorize("@ss.hasPermi('case:transfer:list')")
    @GetMapping("/transfer/list")
    public TableDataInfo transferList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(caseService.selectTransfers(params));
    }

    @PreAuthorize("@ss.hasPermi('case:transfer:add')")
    @Log(title = "case-transfer", businessType = BusinessType.INSERT)
    @PostMapping("/transfer")
    public AjaxResult requestTransfer(@Valid @RequestBody CaseTransferCommand command)
    {
        return toAjax(caseService.requestTransfer(command));
    }

    @PreAuthorize("@ss.hasPermi('case:transfer:approve')")
    @Log(title = "case-transfer-approve", businessType = BusinessType.UPDATE)
    @PostMapping("/transfer/approve")
    public AjaxResult approveTransfer(@Valid @RequestBody CaseTransferApprovalCommand command)
    {
        return toAjax(caseService.approveTransfer(command));
    }

    @PreAuthorize("@ss.hasPermi('case:confirm:list')")
    @GetMapping("/confirm/list")
    public TableDataInfo confirmList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(caseService.selectConfirms(params));
    }

    @PreAuthorize("@ss.hasPermi('case:confirm:handle')")
    @Log(title = "case-confirm", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm")
    public AjaxResult handleConfirm(@Valid @RequestBody CaseConfirmCommand command)
    {
        return toAjax(caseService.handleConfirm(command));
    }

    @PreAuthorize("@ss.hasPermi('case:status:list')")
    @GetMapping("/status/list")
    public TableDataInfo statusList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(caseService.selectStatusLogs(params));
    }
}

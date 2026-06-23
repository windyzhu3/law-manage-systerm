package com.ruoyi.web.controller.matter;

import java.util.List;
import java.util.Map;
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
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.service.IBizMatterService;

@RestController
@RequestMapping("/matter")
public class BizMatterController extends BaseController
{
    @Autowired
    private IBizMatterService matterService;

    @PreAuthorize("@ss.hasAnyPermi('matter:list,matter:query')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard()
    {
        return success(matterService.selectDashboard());
    }

    @PreAuthorize("@ss.hasAnyPermi('matter:list,matter:query')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(matterService.selectMatterList(params));
    }

    @PreAuthorize("@ss.hasPermi('matter:query')")
    @GetMapping("/{caseId}")
    public AjaxResult getInfo(@PathVariable Long caseId)
    {
        return success(matterService.selectMatterDetail(caseId));
    }

    @PreAuthorize("@ss.hasAnyPermi('matter:list,matter:query,matter:add,matter:edit')")
    @GetMapping("/field/{caseType}")
    public AjaxResult fieldConfigs(@PathVariable String caseType)
    {
        return success(matterService.selectFieldConfigs(caseType));
    }

    @PreAuthorize("@ss.hasPermi('matter:add')")
    @Log(title = "matter", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.insertMatter(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:edit')")
    @Log(title = "matter", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.updateMatter(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:progress:list')")
    @GetMapping("/progress/list")
    public TableDataInfo progressList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(matterService.selectProgressList(params));
    }

    @PreAuthorize("@ss.hasPermi('matter:progress:add')")
    @Log(title = "matter-progress", businessType = BusinessType.INSERT)
    @PostMapping("/progress")
    public AjaxResult addProgress(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.insertProgress(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:progress:edit')")
    @Log(title = "matter-progress", businessType = BusinessType.UPDATE)
    @PutMapping("/progress")
    public AjaxResult editProgress(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.updateProgress(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:progress:remove')")
    @Log(title = "matter-progress", businessType = BusinessType.DELETE)
    @DeleteMapping("/progress/{progressId}")
    public AjaxResult removeProgress(@PathVariable Long progressId)
    {
        return toAjax(matterService.deleteProgress(progressId));
    }

    @PreAuthorize("@ss.hasPermi('matter:node:list')")
    @GetMapping("/node/list")
    public TableDataInfo nodeList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(matterService.selectNodeList(params));
    }

    @PreAuthorize("@ss.hasPermi('matter:node:add')")
    @Log(title = "matter-node", businessType = BusinessType.INSERT)
    @PostMapping("/node")
    public AjaxResult addNode(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.insertNode(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:node:edit')")
    @Log(title = "matter-node", businessType = BusinessType.UPDATE)
    @PutMapping("/node")
    public AjaxResult editNode(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.updateNode(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:node:remove')")
    @Log(title = "matter-node", businessType = BusinessType.DELETE)
    @DeleteMapping("/node/{nodeId}")
    public AjaxResult removeNode(@PathVariable Long nodeId)
    {
        return toAjax(matterService.deleteNode(nodeId));
    }

    @PreAuthorize("@ss.hasPermi('matter:node:edit')")
    @Log(title = "matter-node-material", businessType = BusinessType.UPDATE)
    @PutMapping("/node/{nodeId}/materials")
    public AjaxResult saveNodeMaterials(@PathVariable Long nodeId, @RequestBody List<Map<String, Object>> materials)
    {
        return toAjax(matterService.saveNodeMaterials(nodeId, materials));
    }

    @PreAuthorize("@ss.hasPermi('matter:expense:list')")
    @GetMapping("/expense/list")
    public TableDataInfo expenseList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(matterService.selectExpenseList(params));
    }

    @PreAuthorize("@ss.hasPermi('matter:expense:add')")
    @Log(title = "matter-expense", businessType = BusinessType.INSERT)
    @PostMapping("/expense")
    public AjaxResult addExpense(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.insertExpense(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:expense:edit')")
    @Log(title = "matter-expense", businessType = BusinessType.UPDATE)
    @PutMapping("/expense")
    public AjaxResult editExpense(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.updateExpense(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:expense:remove')")
    @Log(title = "matter-expense", businessType = BusinessType.DELETE)
    @DeleteMapping("/expense/{expenseId}")
    public AjaxResult removeExpense(@PathVariable Long expenseId)
    {
        return toAjax(matterService.deleteExpense(expenseId));
    }

    @PreAuthorize("@ss.hasPermi('matter:document:list')")
    @GetMapping("/document/list")
    public TableDataInfo documentList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(matterService.selectDocumentList(params));
    }

    @PreAuthorize("@ss.hasPermi('matter:document:add')")
    @Log(title = "matter-document", businessType = BusinessType.INSERT)
    @PostMapping("/document")
    public AjaxResult addDocument(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.insertDocument(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:document:remove')")
    @Log(title = "matter-document", businessType = BusinessType.DELETE)
    @DeleteMapping("/document/{documentId}")
    public AjaxResult removeDocument(@PathVariable Long documentId)
    {
        return toAjax(matterService.deleteDocument(documentId));
    }

    @PreAuthorize("@ss.hasPermi('matter:archive:list')")
    @GetMapping("/archive/{caseId}")
    public AjaxResult archive(@PathVariable Long caseId)
    {
        return success(matterService.selectArchive(caseId));
    }

    @PreAuthorize("@ss.hasPermi('matter:archive:apply')")
    @Log(title = "matter-archive", businessType = BusinessType.INSERT)
    @PostMapping("/archive/apply")
    public AjaxResult applyArchive(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.applyArchive(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:archive:confirm')")
    @Log(title = "matter-close", businessType = BusinessType.UPDATE)
    @PostMapping("/archive/close")
    public AjaxResult confirmClose(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.confirmClose(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:archive:confirm')")
    @Log(title = "matter-archive", businessType = BusinessType.UPDATE)
    @PostMapping("/archive/confirm")
    public AjaxResult confirmArchive(@RequestBody Map<String, Object> body)
    {
        return toAjax(matterService.confirmArchive(body));
    }

    @PreAuthorize("@ss.hasPermi('matter:status:list')")
    @GetMapping("/status/list")
    public TableDataInfo statusList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(matterService.selectStatusLogs(params));
    }
}

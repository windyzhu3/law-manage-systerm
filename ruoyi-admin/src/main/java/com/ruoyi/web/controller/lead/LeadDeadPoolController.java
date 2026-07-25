package com.ruoyi.web.controller.lead;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.law.business.lead.dto.LeadDeadPoolRestoreCommand;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.system.service.IBizLeadService;

@RestController
@RequestMapping("/lead/dead-pool")
public class LeadDeadPoolController extends BaseController
{
    private final IBizLeadService leads;
    public LeadDeadPoolController(IBizLeadService leads) { this.leads=leads; }

    @PreAuthorize("@ss.hasPermi('lead:dead-pool:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required=false) String reasonCode,
            @RequestParam(required=false) String keyword)
    {
        startPage();
        return getDataTable(leads.selectDeadPoolQueue(reasonCode,keyword));
    }

    @PreAuthorize("@ss.hasPermi('lead:dead-pool:restore')")
    @PostMapping("/{leadId}/restore")
    public AjaxResult restore(@PathVariable Long leadId,
            @Valid @RequestBody LeadDeadPoolRestoreCommand command)
    {
        return success(leads.restoreDeadPool(leadId,command));
    }
}

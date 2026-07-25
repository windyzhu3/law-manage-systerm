package com.ruoyi.web.controller.lead;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.system.service.IBizLeadService;

@RestController
@RequestMapping("/lead")
public class LeadRetryController extends BaseController
{
    private final IBizLeadService leads;
    public LeadRetryController(IBizLeadService leads) { this.leads=leads; }

    @PreAuthorize("@ss.hasPermi('lead:retry:list')")
    @GetMapping("/retry/list")
    public TableDataInfo list(@RequestParam(required=false) String status,
            @RequestParam(required=false) String keyword)
    {
        startPage();
        return getDataTable(leads.selectRetryQueue(status,keyword));
    }

    @PreAuthorize("@ss.hasPermi('lead:retry:list')")
    @GetMapping("/{leadId}/retry-timeline")
    public AjaxResult timeline(@PathVariable Long leadId)
    {
        return success(leads.selectRetryTimeline(leadId));
    }
}

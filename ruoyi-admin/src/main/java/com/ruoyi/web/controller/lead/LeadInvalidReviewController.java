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
import com.law.business.lead.dto.LeadInvalidReviewCompleteCommand;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.system.service.IBizLeadService;

@RestController
@RequestMapping("/lead/invalid-review")
public class LeadInvalidReviewController extends BaseController
{
    private final IBizLeadService leads;
    public LeadInvalidReviewController(IBizLeadService leads) { this.leads=leads; }

    @PreAuthorize("@ss.hasPermi('lead:invalid-review:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required=false) String status,
            @RequestParam(required=false) String keyword)
    {
        startPage();
        return getDataTable(leads.selectInvalidReviewQueue(status,keyword));
    }

    @PreAuthorize("@ss.hasPermi('lead:invalid-review:handle')")
    @PostMapping("/{todoId}/complete")
    public AjaxResult complete(@PathVariable Long todoId,
            @Valid @RequestBody LeadInvalidReviewCompleteCommand command)
    {
        return success(leads.completeInvalidReview(todoId,command));
    }
}

package com.ruoyi.web.controller.lead;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.law.business.lead.dto.LeadAssignmentPolicyCommand;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.service.IBizLeadService;

@RestController
@RequestMapping("/lead/assignment-policy")
public class LeadAssignmentPolicyController extends BaseController
{
    private final IBizLeadService leads;
    public LeadAssignmentPolicyController(IBizLeadService leads) { this.leads=leads; }

    @PreAuthorize("@ss.hasPermi('lead:assignment-policy:list')")
    @GetMapping
    public AjaxResult list() { return success(leads.selectAssignmentPolicies()); }

    @PreAuthorize("@ss.hasPermi('lead:assignment-policy:edit')")
    @PutMapping
    public AjaxResult save(@Valid @RequestBody LeadAssignmentPolicyCommand command)
    {
        return success(leads.saveAssignmentPolicy(command));
    }
}

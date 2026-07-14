package com.ruoyi.web.controller.lead;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.law.business.lead.dto.LeadAssignCommand;
import com.law.business.lead.dto.LeadFollowupCommand;
import com.law.business.lead.dto.LeadPoolCommand;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.service.IBizLeadService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/lead")
public class BizLeadController extends BaseController
{
    @Autowired
    private IBizLeadService leadService;

    @Autowired
    private ISysUserService userService;

    @PreAuthorize("@ss.hasPermi('lead:dashboard:view')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard() { return success(leadService.selectDashboard()); }

    @PreAuthorize("@ss.hasAnyPermi('lead:all:list,lead:mine:list,lead:pool:list,lead:recycle:list,lead:query')")
    @GetMapping("/list")
    public TableDataInfo list(BizLead lead)
    {
        startPage();
        return getDataTable(leadService.selectLeadList(lead));
    }

    @PreAuthorize("@ss.hasAnyPermi('lead:query,lead:mine:query,lead:pool:query,lead:recycle:query')")
    @GetMapping("/{leadId}")
    public AjaxResult getInfo(@PathVariable Long leadId) { return success(leadService.selectLeadById(leadId)); }

    @PreAuthorize("@ss.hasAnyPermi('lead:add,lead:edit,lead:assign')")
    @GetMapping("/owner/options")
    public AjaxResult ownerOptions() { return success(userService.selectUserList(new SysUser())); }

    @Log(title = "线索管理", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('lead:add')")
    @PostMapping
    public AjaxResult add(@RequestBody BizLead lead) { return toAjax(leadService.insertLead(lead)); }

    @Log(title = "线索管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('lead:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody BizLead lead) { return toAjax(leadService.updateLead(lead)); }

    @Log(title = "线索管理", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('lead:remove')")
    @DeleteMapping("/{leadIds}")
    public AjaxResult remove(@PathVariable Long[] leadIds) { return toAjax(leadService.softDeleteLead(leadIds)); }

    @PreAuthorize("@ss.hasPermi('lead:recycle:restore')")
    @PostMapping("/restore/{leadIds}")
    public AjaxResult restore(@PathVariable Long[] leadIds) { return toAjax(leadService.restoreLead(leadIds)); }

    @PreAuthorize("@ss.hasPermi('lead:recycle:purge')")
    @DeleteMapping("/purge/{leadIds}")
    public AjaxResult purge(@PathVariable Long[] leadIds) { return toAjax(leadService.purgeLead(leadIds)); }

    @PreAuthorize("@ss.hasPermi('lead:assign')")
    @PostMapping("/assign")
    public AjaxResult assign(@Valid @RequestBody LeadAssignCommand command)
    {
        return toAjax(leadService.assignLead(command.getLeadId(), command.getOwnerId(), command.getReason()));
    }

    @PreAuthorize("@ss.hasAnyPermi('lead:pool:move,lead:mine:pool:move')")
    @PostMapping("/pool")
    public AjaxResult pool(@Valid @RequestBody LeadPoolCommand command)
    {
        return toAjax(leadService.moveToPool(command.getLeadId(), command.getReason()));
    }

    @PreAuthorize("@ss.hasPermi('lead:pool:claim')")
    @PostMapping("/claim/{leadId}")
    public AjaxResult claim(@PathVariable Long leadId) { return toAjax(leadService.claimLead(leadId)); }

    @PreAuthorize("@ss.hasAnyPermi('lead:convert,lead:mine:convert')")
    @PostMapping("/convert/{leadId}")
    public AjaxResult convert(@PathVariable Long leadId) { return toAjax(leadService.convertLead(leadId)); }

    @PreAuthorize("@ss.hasPermi('lead:followup:list')")
    @GetMapping("/followup/list")
    public TableDataInfo followupList(BizLeadFollowup followup)
    {
        startPage();
        return getDataTable(leadService.selectFollowupList(followup));
    }

    @PreAuthorize("@ss.hasAnyPermi('lead:followup:add,lead:mine:followup')")
    @PostMapping("/followup")
    public AjaxResult addFollowup(@Valid @RequestBody LeadFollowupCommand followup) { return toAjax(leadService.insertFollowup(followup)); }

    @PreAuthorize("@ss.hasPermi('lead:followup:edit')")
    @PutMapping("/followup")
    public AjaxResult editFollowup(@Valid @RequestBody LeadFollowupCommand followup) { return toAjax(leadService.updateFollowup(followup)); }

    @PreAuthorize("@ss.hasPermi('lead:followup:remove')")
    @DeleteMapping("/followup/{followupId}")
    public AjaxResult removeFollowup(@PathVariable Long followupId) { return toAjax(leadService.deleteFollowup(followupId)); }

    @PreAuthorize("@ss.hasAnyPermi('lead:query,lead:settings:list,lead:setting:options,lead:mine:query,lead:pool:query,lead:recycle:query,customer:query')")
    @GetMapping("/setting/list")
    public AjaxResult settingList(BizLeadSetting setting)
    {
        boolean hasLeadSettingPerm = SecurityUtils.hasPermi("lead:query")
                || SecurityUtils.hasPermi("lead:settings:list")
                || SecurityUtils.hasPermi("lead:setting:options")
                || SecurityUtils.hasPermi("lead:mine:query")
                || SecurityUtils.hasPermi("lead:pool:query")
                || SecurityUtils.hasPermi("lead:recycle:query");
        if (!hasLeadSettingPerm && !"source".equals(setting.getSettingType()))
        {
            throw new ServiceException("Customer permission can only access lead source options");
        }
        return success(leadService.selectSettingList(setting));
    }

    @PreAuthorize("@ss.hasPermi('lead:settings:add')")
    @PostMapping("/setting")
    public AjaxResult addSetting(@RequestBody BizLeadSetting setting) { return toAjax(leadService.insertSetting(setting)); }

    @PreAuthorize("@ss.hasPermi('lead:settings:edit')")
    @PutMapping("/setting")
    public AjaxResult editSetting(@RequestBody BizLeadSetting setting) { return toAjax(leadService.updateSetting(setting)); }

    @PreAuthorize("@ss.hasPermi('lead:settings:remove')")
    @DeleteMapping("/setting/{settingId}")
    public AjaxResult removeSetting(@PathVariable Long settingId) { return toAjax(leadService.deleteSetting(settingId)); }
}

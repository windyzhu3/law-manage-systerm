package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.service.IBizLeadService;
import com.law.business.security.LeadPermissions;
import com.ruoyi.system.service.lead.LeadCommandService;
import com.ruoyi.system.service.lead.LeadQueryService;
import com.ruoyi.system.service.lead.LeadAssignmentService;
import com.ruoyi.system.service.lead.LeadPoolService;
import com.ruoyi.system.service.lead.LeadConversionService;
import com.ruoyi.system.service.lead.LeadFollowupService;
import com.law.business.lead.dto.LeadFollowupCommand;
import com.law.business.lead.dto.LeadAssignmentPolicyCommand;
import com.law.business.lead.dto.LeadDeadPoolRestoreCommand;
import com.law.business.lead.dto.LeadInvalidReviewCompleteCommand;
import com.law.business.lead.dto.LeadManualCallRecordCommand;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.domain.LeadTodoWorkItemView;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService.PolicyView;
import com.ruoyi.system.service.lead.LeadCallRecordService.CallRecordOutcome;
import com.ruoyi.system.service.lead.LeadDeadPoolService.DeadPoolOutcome;
import com.ruoyi.system.service.lead.LeadTodoApiService;

@Service
public class BizLeadServiceImpl implements IBizLeadService
{
    @Autowired
    private LeadQueryService leadQueryService;

    @Autowired
    private LeadCommandService leadCommandService;

    @Autowired
    private LeadAssignmentService leadAssignmentService;

    @Autowired
    private LeadPoolService leadPoolService;

    @Autowired
    private LeadConversionService leadConversionService;

    @Autowired
    private LeadFollowupService leadFollowupService;

    @Autowired
    private LeadTodoApiService leadTodoApiService;

    @Override public List<BizLead> selectLeadList(BizLead lead) { return leadQueryService.list(lead); }
    @Override public BizLead selectLeadById(Long leadId) { return leadQueryService.detail(leadId); }
    @Override @Transactional public int insertLead(BizLead lead) { return leadCommandService.create(lead); }
    @Override public int updateLead(BizLead lead) { return leadCommandService.update(lead); }
    @Override public int softDeleteLead(Long[] leadIds) { return leadCommandService.softDelete(leadIds); }
    @Override public int restoreLead(Long[] leadIds) { return leadCommandService.restore(leadIds); }
    @Override @Transactional public int purgeLead(Long[] leadIds) { return leadCommandService.purge(leadIds); }
    @Override @Transactional public int assignLead(Long leadId, Long ownerId, String reason) { return leadAssignmentService.assign(leadId, ownerId, reason); }
    @Override @Transactional public int moveToPool(Long leadId, String reason) { return leadPoolService.moveToPool(leadId, reason); }
    @Override @Transactional public int claimLead(Long leadId) { return leadPoolService.claim(leadId); }

    @Override
    @Transactional
    public int convertLead(Long leadId)
    {
        boolean ownerOnly = !SecurityUtils.hasPermi(LeadPermissions.CONVERT)
                && SecurityUtils.hasPermi(LeadPermissions.CONVERT_MINE);
        leadConversionService.convert(leadId, ownerOnly);
        return 1;
    }

    @Override public List<BizLeadFollowup> selectFollowupList(BizLeadFollowup followup) { return leadFollowupService.list(followup); }

    @Override
    @Transactional
    public int insertFollowup(LeadFollowupCommand followup)
    {
        boolean ownerOnly = !SecurityUtils.hasPermi(LeadPermissions.FOLLOW)
                && SecurityUtils.hasPermi(LeadPermissions.FOLLOW_MINE);
        return leadFollowupService.add(followup, ownerOnly);
    }

    @Override public int updateFollowup(LeadFollowupCommand followup) { return leadFollowupService.update(followup, false); }
    @Override public int deleteFollowup(Long followupId) { return leadFollowupService.remove(followupId, false); }
    @Override public List<BizLeadSetting> selectSettingList(BizLeadSetting setting) { return leadQueryService.settings(setting); }
    @Override public int insertSetting(BizLeadSetting setting) { return leadCommandService.createSetting(setting); }
    @Override public int updateSetting(BizLeadSetting setting) { return leadCommandService.updateSetting(setting); }
    @Override public int deleteSetting(Long settingId) { return leadCommandService.deleteSetting(settingId); }
    @Override public Map<String, Object> selectDashboard() { return leadQueryService.dashboard(); }

    @Override public void confirmTag(Long id) { leadTodoApiService.confirmTag(id); }
    @Override public List<LeadTodoWorkItemView> selectCallTimeline(Long id) { return leadTodoApiService.callTimeline(id); }
    @Override public CallRecordOutcome addManualCallRecord(Long id,LeadManualCallRecordCommand command) { return leadTodoApiService.addManualCallRecord(id,command); }
    @Override public List<LeadTodoWorkItemView> selectInvalidReviewQueue(String status,String keyword) { return leadTodoApiService.invalidReviewQueue(status,keyword); }
    @Override public TodoInstance completeInvalidReview(Long id,LeadInvalidReviewCompleteCommand command) { return leadTodoApiService.completeInvalidReview(id,command); }
    @Override public List<LeadTodoWorkItemView> selectRetryQueue(String status,String keyword) { return leadTodoApiService.retryQueue(status,keyword); }
    @Override public List<LeadTodoWorkItemView> selectRetryTimeline(Long id) { return leadTodoApiService.retryTimeline(id); }
    @Override public List<LeadTodoWorkItemView> selectDeadPoolQueue(String reason,String keyword) { return leadTodoApiService.deadPoolQueue(reason,keyword); }
    @Override public DeadPoolOutcome restoreDeadPool(Long id,LeadDeadPoolRestoreCommand command) { return leadTodoApiService.restoreDeadPool(id,command); }
    @Override public List<PolicyView> selectAssignmentPolicies() { return leadTodoApiService.assignmentPolicies(); }
    @Override public PolicyView saveAssignmentPolicy(LeadAssignmentPolicyCommand command) { return leadTodoApiService.saveAssignmentPolicy(command); }
}

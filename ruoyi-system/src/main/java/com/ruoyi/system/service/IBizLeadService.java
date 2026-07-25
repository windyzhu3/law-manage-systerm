package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.law.business.lead.dto.LeadFollowupCommand;
import com.law.business.lead.dto.LeadAssignmentPolicyCommand;
import com.law.business.lead.dto.LeadDeadPoolRestoreCommand;
import com.law.business.lead.dto.LeadInvalidReviewCompleteCommand;
import com.law.business.lead.dto.LeadManualCallRecordCommand;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.domain.LeadTodoWorkItemView;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService.PolicyView;
import com.ruoyi.system.service.lead.LeadCallRecordService.CallRecordOutcome;
import com.ruoyi.system.service.lead.LeadDeadPoolService.DeadPoolOutcome;
import com.law.todo.domain.model.TodoInstance;

public interface IBizLeadService
{
    public List<BizLead> selectLeadList(BizLead lead);
    public BizLead selectLeadById(Long leadId);
    public int insertLead(BizLead lead);
    public int updateLead(BizLead lead);
    public int softDeleteLead(Long[] leadIds);
    public int restoreLead(Long[] leadIds);
    public int purgeLead(Long[] leadIds);
    public int assignLead(Long leadId, Long ownerId, String reason);
    public int moveToPool(Long leadId, String reason);
    public int claimLead(Long leadId);
    public int convertLead(Long leadId);
    public List<BizLeadFollowup> selectFollowupList(BizLeadFollowup followup);
    public int insertFollowup(LeadFollowupCommand followup);
    public int updateFollowup(LeadFollowupCommand followup);
    public int deleteFollowup(Long followupId);
    public List<BizLeadSetting> selectSettingList(BizLeadSetting setting);
    public int insertSetting(BizLeadSetting setting);
    public int updateSetting(BizLeadSetting setting);
    public int deleteSetting(Long settingId);
    public Map<String, Object> selectDashboard();
    public void confirmTag(Long tagRelationId);
    public List<LeadTodoWorkItemView> selectCallTimeline(Long leadId);
    public CallRecordOutcome addManualCallRecord(Long leadId, LeadManualCallRecordCommand command);
    public List<LeadTodoWorkItemView> selectInvalidReviewQueue(String status, String keyword);
    public TodoInstance completeInvalidReview(Long todoId, LeadInvalidReviewCompleteCommand command);
    public List<LeadTodoWorkItemView> selectRetryQueue(String status, String keyword);
    public List<LeadTodoWorkItemView> selectRetryTimeline(Long leadId);
    public List<LeadTodoWorkItemView> selectDeadPoolQueue(String reasonCode, String keyword);
    public DeadPoolOutcome restoreDeadPool(Long leadId, LeadDeadPoolRestoreCommand command);
    public List<PolicyView> selectAssignmentPolicies();
    public PolicyView saveAssignmentPolicy(LeadAssignmentPolicyCommand command);
}


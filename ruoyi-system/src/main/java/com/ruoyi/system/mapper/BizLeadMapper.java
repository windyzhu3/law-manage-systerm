package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.domain.LeadTodoWorkItemView;

public interface BizLeadMapper
{
    public List<BizLead> selectLeadList(BizLead lead);
    public BizLead selectLeadById(Long leadId);
    public BizLead selectLeadForProgressCycleForUpdate(Long leadId);
    public int insertLead(BizLead lead);
    public int insertSourceBusinessTagIfAbsent(@Param("sourceCode") String sourceCode,
            @Param("createBy") String createBy);
    public int insertLeadSourceTagRelationIfAbsent(@Param("leadId") Long leadId,
            @Param("sourceCode") String sourceCode, @Param("createBy") String createBy);
    public int updateLead(BizLead lead);
    public int softDeleteLead(@Param("leadIds") Long[] leadIds, @Param("updateBy") String updateBy);
    public int restoreLead(@Param("leadIds") Long[] leadIds, @Param("updateBy") String updateBy);
    public int purgeLeadCallRecords(@Param("leadIds") Long[] leadIds);
    public int purgeLeadInvalidReviews(@Param("leadIds") Long[] leadIds);
    public int purgeLeadRetryRecords(@Param("leadIds") Long[] leadIds);
    public int purgeLeadQualityRecords(@Param("leadIds") Long[] leadIds);
    public int purgeLeadDeadPoolLogs(@Param("leadIds") Long[] leadIds);
    public int purgeLeadTagRelations(@Param("leadIds") Long[] leadIds);
    public int purgeLeadFollowups(@Param("leadIds") Long[] leadIds);
    public int purgeLeadAssignmentLogs(@Param("leadIds") Long[] leadIds);
    public int purgeLead(@Param("leadIds") Long[] leadIds);
    public int assignLead(@Param("leadId") Long leadId, @Param("ownerId") Long ownerId,
            @Param("updateBy") String updateBy, @Param("expectedStatus") String expectedStatus,
            @Param("expectedRowVersion") Integer expectedRowVersion);
    public int moveToPool(@Param("leadId") Long leadId, @Param("reason") String reason,
            @Param("updateBy") String updateBy, @Param("expectedStatus") String expectedStatus,
            @Param("expectedRowVersion") Integer expectedRowVersion);
    public int claimLead(@Param("leadId") Long leadId, @Param("ownerId") Long ownerId,
            @Param("deptId") Long deptId, @Param("updateBy") String updateBy,
            @Param("expectedStatus") String expectedStatus, @Param("expectedRowVersion") Integer expectedRowVersion);
    public int bindCustomerConditionally(@Param("leadId") Long leadId, @Param("customerId") Long customerId,
            @Param("updateBy") String updateBy, @Param("expectedStatus") String expectedStatus,
            @Param("expectedRowVersion") Integer expectedRowVersion);
    public int insertAssignmentLog(Map<String, Object> assignmentLog);
    public List<BizLeadFollowup> selectFollowupList(BizLeadFollowup followup);
    public BizLeadFollowup selectFollowupById(Long followupId);
    public int insertFollowup(BizLeadFollowup followup);
    public int insertProgressFollowupIfAbsent(BizLeadFollowup followup);
    public BizLeadFollowup selectProgressFollowupByIdempotencyKey(String idempotencyKey);
    public BizLeadFollowup selectProgressFollowupByIdempotencyKeyForUpdate(String idempotencyKey);
    public int linkProgressFollowupSchedule(@Param("followupId") Long followupId,
            @Param("schedulePlanId") Long schedulePlanId,@Param("operator") String operator);
    public int updateFollowup(BizLeadFollowup followup);
    public int deleteFollowup(@Param("followupId") Long followupId, @Param("leadId") Long leadId);
    public int touchLeadFollowTime(@Param("leadId") Long leadId,
            @Param("nextFollowTime") java.util.Date nextFollowTime, @Param("updateBy") String updateBy,
            @Param("expectedRowVersion") Integer expectedRowVersion);
    public int touchLeadFollowTimeConditionally(@Param("leadId") Long leadId,
            @Param("nextFollowTime") java.util.Date nextFollowTime, @Param("updateBy") String updateBy,
            @Param("expectedStatus") String expectedStatus, @Param("expectedRowVersion") Integer expectedRowVersion);
    public int confirmLeadTags(@Param("leadId") Long leadId, @Param("expectedStatus") String expectedStatus,
            @Param("confirmBy") Long confirmBy, @Param("rowVersion") Integer rowVersion,
            @Param("updateBy") String updateBy);
    public int completeFirstContact(@Param("leadId") Long leadId,
            @Param("expectedStatus") String expectedStatus, @Param("result") String result,
            @Param("contactName") String contactName, @Param("city") String city,
            @Param("legalDemand") String legalDemand, @Param("visited") String visited,
            @Param("invalidReasonCode") String invalidReasonCode, @Param("invalidSourceNode") String invalidSourceNode,
            @Param("rowVersion") Integer rowVersion, @Param("updateBy") String updateBy);
    public int markInvalidReviewed(@Param("leadId") Long leadId,
            @Param("expectedReviewStatus") String expectedReviewStatus, @Param("reviewResult") String reviewResult,
            @Param("rowVersion") Integer rowVersion, @Param("updateBy") String updateBy);
    public int advanceRetryStage(@Param("leadId") Long leadId, @Param("expectedStage") String expectedStage,
            @Param("nextStage") String nextStage, @Param("attemptCount") Integer attemptCount,
            @Param("nextRetryTime") Date nextRetryTime, @Param("rowVersion") Integer rowVersion,
            @Param("updateBy") String updateBy);
    public int moveToDeadPool(@Param("leadId") Long leadId, @Param("reason") String reason,
            @Param("rowVersion") Integer rowVersion, @Param("updateBy") String updateBy);
    public int restoreFromDeadPool(@Param("leadId") Long leadId, @Param("reason") String reason,
            @Param("rowVersion") Integer rowVersion, @Param("updateBy") String updateBy);
    public int reopenFirstContact(@Param("leadId") Long leadId, @Param("rowVersion") Integer rowVersion,
            @Param("updateBy") String updateBy);
    public int completeRetryConnected(@Param("leadId") Long leadId, @Param("expectedStage") String expectedStage,
            @Param("contactName") String contactName, @Param("city") String city,
            @Param("legalDemand") String legalDemand, @Param("visited") String visited,
            @Param("rowVersion") Integer rowVersion, @Param("updateBy") String updateBy);
    public int countLeadInDataScope(@Param("leadId") Long leadId, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("includeDeleted") Boolean includeDeleted);
    public List<BizLeadSetting> selectSettingList(BizLeadSetting setting);
    public int insertSetting(BizLeadSetting setting);
    public int updateSetting(BizLeadSetting setting);
    public int deleteSetting(Long settingId);
    public List<Map<String, Object>> selectDashboardCards(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
    public List<Map<String, Object>> selectSourceStats(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
    public List<Map<String, Object>> selectStatusStats(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
    public List<LeadTodoWorkItemView> selectLeadCallTimeline(Long leadId);
    public List<LeadTodoWorkItemView> selectLeadInvalidReviewQueue(@Param("status") String status,
            @Param("keyword") String keyword, @Param("currentUserId") Long currentUserId,
            @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
    public List<LeadTodoWorkItemView> selectLeadRetryQueue(@Param("status") String status,
            @Param("keyword") String keyword, @Param("currentUserId") Long currentUserId,
            @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
    public List<LeadTodoWorkItemView> selectLeadRetryTimeline(Long leadId);
    public List<LeadTodoWorkItemView> selectLeadDeadPoolQueue(@Param("reasonCode") String reasonCode,
            @Param("keyword") String keyword, @Param("currentUserId") Long currentUserId,
            @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
    public BizLead selectDeadPoolOriginLeadForUpdate(Long leadId);
    public int countDeadPoolInDataScope(@Param("leadId") Long leadId,
            @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId);
    public int countDepartmentInDataScope(@Param("deptId") Long deptId,
            @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId);
}

package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BizLeadAssignmentPolicy;
import com.ruoyi.system.domain.BizLeadCallRecord;
import com.ruoyi.system.domain.BizLeadDeadPoolLog;
import com.ruoyi.system.domain.BizLeadInvalidReview;
import com.ruoyi.system.domain.BizLeadQualityRecord;
import com.ruoyi.system.domain.BizLeadRetryRecord;
import com.ruoyi.system.domain.LeadAssignmentPolicyCandidateView;

public interface LeadFlowMapper
{
    int insertCallRecordIfAbsent(BizLeadCallRecord record);
    BizLeadCallRecord selectCallRecordByIdempotencyKey(String idempotencyKey);
    int countCallRecordsForLeadTodo(@Param("leadId") Long leadId,@Param("todoId") Long todoId);

    int insertInvalidReviewIfAbsent(BizLeadInvalidReview review);
    BizLeadInvalidReview selectInvalidReviewById(Long reviewId);
    BizLeadInvalidReview selectInvalidReviewBySourceTodo(@Param("leadId") Long leadId,
            @Param("todoId") Long todoId);
    BizLeadInvalidReview selectInvalidReviewByIdempotencyKey(String idempotencyKey);
    int completeInvalidReview(@Param("reviewId") Long reviewId, @Param("result") String result,
            @Param("comment") String comment, @Param("reviewerId") Long reviewerId,
            @Param("systemDefault") String systemDefault, @Param("expectedRowVersion") Integer expectedRowVersion,
            @Param("updateBy") String updateBy);

    int insertRetryRecordIfAbsent(BizLeadRetryRecord record);
    BizLeadRetryRecord selectRetryRecordByIdempotencyKey(String idempotencyKey);

    int insertQualityRecordIfAbsent(BizLeadQualityRecord record);
    BizLeadQualityRecord selectQualityRecordByIdempotencyKey(String idempotencyKey);

    int insertDeadPoolLogIfAbsent(BizLeadDeadPoolLog log);
    BizLeadDeadPoolLog selectDeadPoolLogByIdempotencyKey(String idempotencyKey);
    BizLeadDeadPoolLog selectDeadPoolLogByIdempotencyKeyForUpdate(String idempotencyKey);

    int confirmTagRelation(@Param("tagRelationId") Long tagRelationId, @Param("leadId") Long leadId,
            @Param("confirmStatus") String confirmStatus, @Param("confirmedBy") Long confirmedBy,
            @Param("updateBy") String updateBy);
    Long selectLeadIdByTagRelation(Long tagRelationId);

    Long selectActiveRetryPlanId(Long leadId);
    BizLeadAssignmentPolicy selectActiveAssignmentPolicy(@Param("salesDeptId") Long salesDeptId,
            @Param("sourceCode") String sourceCode);
    List<Long> selectActivePolicyCandidates(Long policyId);
    List<BizLeadAssignmentPolicy> selectAssignmentPolicies(@Param("currentUserId") Long currentUserId,
            @Param("currentDeptId") Long currentDeptId, @Param("dataScope") boolean dataScope);
    List<LeadAssignmentPolicyCandidateView> selectAssignmentPolicyCandidateViews(
            @Param("policyIds") List<Long> policyIds);
    BizLeadAssignmentPolicy selectAssignmentPolicyByIdForUpdate(Long policyId);
    int insertAssignmentPolicy(BizLeadAssignmentPolicy policy);
    int updateAssignmentPolicyConditionally(@Param("policyId") Long policyId,
            @Param("policyName") String policyName, @Param("salesDeptId") Long salesDeptId,
            @Param("sourceCode") String sourceCode, @Param("retryRuleJson") String retryRuleJson,
            @Param("expectedVersion") Integer expectedVersion, @Param("updateBy") String updateBy);
    int deleteAssignmentPolicyCandidates(Long policyId);
    int insertAssignmentPolicyCandidate(@Param("policyId") Long policyId, @Param("userId") Long userId,
            @Param("sortOrder") Integer sortOrder, @Param("createBy") String createBy);
    List<Long> selectActiveCandidateUsersInDepartment(@Param("salesDeptId") Long salesDeptId,
            @Param("userIds") List<Long> userIds);
    int countActiveLeadSource(String sourceCode);
}

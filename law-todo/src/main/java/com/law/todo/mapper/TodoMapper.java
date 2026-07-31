package com.law.todo.mapper;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.domain.model.TodoInstance;

public interface TodoMapper
{
    int insertInstance(TodoInstance value);
    TodoInstance selectById(Long todoId);
    TodoInstance selectByTriggerKey(String key);
    TodoInstance selectByNextKey(String key);
    int updateStatusConditionally(@Param("todoId") Long todoId,@Param("fromStatus") String fromStatus,@Param("toStatus") String toStatus,@Param("ownerId") Long ownerId,@Param("operator") String operator);
    int insertActionIfAbsent(Map<String,Object> action);
    Map<String,Object> selectActionById(String actionId);
    int insertCandidate(Map<String,Object> candidate);
    List<Map<String,Object>> selectCandidates(Long todoId);
    int insertRelation(Map<String,Object> relation);
    Map<String,Object> selectTemplateVersion(@Param("templateId") Long templateId,@Param("versionNo") int versionNo);
    int insertTemplateVersion(Map<String,Object> version);
    int updateDefinitionDocument(Map<String,Object> version);
    int updateDefinitionCompilation(Map<String,Object> version);
    Map<String,Object> selectEventCatalog(@Param("eventType") String eventType,@Param("payloadVersion") int payloadVersion);
    List<Map<String,Object>> selectEventCatalogs();
    Map<String,Object> selectDecisionByCode(String decisionCode);
    Map<String,Object> selectDecisionById(Long decisionId);
    List<Map<String,Object>> selectDecisions();
    Map<String,Object> selectDecisionGovernanceUser(Long userId);
    List<Map<String,Object>> selectDecisionGovernanceUsers();
    List<Map<String,Object>> selectDecisionGovernanceRoles();
    int insertDecision(Map<String,Object> decision);
    int updateDecisionConditionally(Map<String,Object> decision);
    int updateTemplateCurrentVersion(@Param("templateId") Long templateId,@Param("versionNo") int versionNo,@Param("operator") String operator);
    List<Map<String,Object>> selectTriggerRules(@Param("eventType") String eventType,@Param("businessType") String businessType);
    List<Map<String,Object>> selectSlaScanItems(@Param("now") LocalDateTime now);
    int markSlaThreshold(@Param("todoId") Long todoId,@Param("threshold") String threshold,@Param("plannedDueAt") LocalDateTime plannedDueAt,@Param("expectedDueAt") LocalDateTime expectedDueAt,@Param("expectedVersion") Integer expectedVersion,@Param("now") LocalDateTime now);
    int pauseSla(@Param("todoId") Long todoId,@Param("now") LocalDateTime now);
    int resumeSla(@Param("todoId") Long todoId,@Param("now") LocalDateTime now);
    Map<String,Object> selectDashboard(@Param("userId") Long userId,@Param("deptId") Long deptId);
    List<Map<String,Object>> selectTodoList(Map<String,Object> query);
    Map<String,Object> selectTemplateVersionById(Long versionId);
    Map<String,Object> selectTriggerTemplateBinding(Long versionId);
    Map<String,Object> selectTemplateVersionForUpdate(Long versionId);
    List<String> selectAttachmentTypes(Long todoId);
    int insertAttachment(Map<String,Object> attachment);
    int insertCc(@Param("todoId") Long todoId,@Param("userId") Long userId,@Param("ccType") String ccType);
    List<Map<String,Object>> selectCalendars();
    int insertCalendar(Map<String,Object> calendar);
    int updateCalendar(Map<String,Object> calendar);
    Map<String,Object> selectCalendarByCode(String code);
    int insertSlaRecord(Map<String,Object> record);
    int insertSlaNotification(@Param("todoId") Long todoId,@Param("notificationType") String notificationType,@Param("now") LocalDateTime now);
    int insertSupervisorEscalationNotification(@Param("todoId") Long todoId,@Param("now") LocalDateTime now);
    int countCandidateAccess(@Param("todoId") Long todoId,@Param("userId") Long userId,@Param("deptId") Long deptId);
    int countCcAccess(@Param("todoId") Long todoId,@Param("userId") Long userId);
    int countSupervisorAccess(@Param("todoId") Long todoId,@Param("userId") Long userId);
    List<Map<String,Object>> selectAllowedActionFacts(@Param("todoIds") List<Long> todoIds,
            @Param("userId") Long userId,@Param("deptId") Long deptId);
    List<Long> selectGovernedSupervisors(@Param("ownerId") Long ownerId,@Param("ownerDeptId") Long ownerDeptId);
    Long selectUserDeptId(Long userId);
    List<Long> selectActiveUserIdsForRole(Long roleId);
    List<Long> selectActiveUserIdsForDepartment(Long departmentId);
    List<Long> selectActiveUserIdsForPost(Long postId);
    int countActiveUser(Long userId);
    Long selectBusinessOwner(@Param("businessType") String businessType,@Param("businessId") Long businessId);
    Long selectDepartmentSupervisor(@Param("userId") Long userId,@Param("levels") int levels);
    int countAvailableUser(@Param("userId") Long userId,@Param("effectiveAt") LocalDateTime effectiveAt);
    Long selectActiveDelegate(@Param("userId") Long userId,@Param("effectiveAt") LocalDateTime effectiveAt);
    int insertRoundRobinCursorIfAbsent(@Param("strategyKey") String strategyKey);
    Map<String,Object> selectRoundRobinCursorForUpdate(@Param("strategyKey") String strategyKey);
    int advanceRoundRobinCursorConditionally(@Param("strategyKey") String strategyKey,
            @Param("selectedUserId") Long selectedUserId,@Param("expectedLastUserId") Long expectedLastUserId,
            @Param("expectedVersion") int expectedVersion);

    @Transactional
    default Long selectAndAdvanceRoundRobin(String strategyKey,List<Long> candidates)
    {
        if(strategyKey==null||strategyKey.isBlank()||candidates==null||candidates.isEmpty())return null;
        List<Long> stable=candidates.stream().filter(value->value!=null&&value>0).distinct().toList();
        if(stable.isEmpty())return null;
        insertRoundRobinCursorIfAbsent(strategyKey);
        Map<String,Object> cursor=selectRoundRobinCursorForUpdate(strategyKey);
        if(cursor==null)throw new IllegalStateException("Round-robin cursor could not be locked");
        Long previous=cursorLong(cursor,"lastUserId","last_user_id");
        int version=cursorLong(cursor,"version","version").intValue();
        Long selected=stable.get(0);
        if(previous!=null)
        {
            int current=stable.indexOf(previous);
            if(current>=0)selected=stable.get((current+1)%stable.size());
        }
        if(!stable.contains(selected))throw new IllegalStateException("Round-robin selected outside candidate pool");
        if(advanceRoundRobinCursorConditionally(strategyKey,selected,previous,version)!=1)
            throw new IllegalStateException("Round-robin cursor changed concurrently");
        return selected;
    }

    private static Long cursorLong(Map<String,Object> cursor,String camel,String snake)
    {
        Object value=cursor.containsKey(camel)?cursor.get(camel):cursor.get(snake);
        if(value==null)return camel.equals("version")?0L:null;
        return value instanceof Number number?number.longValue():Long.valueOf(String.valueOf(value));
    }
    Long selectRoleIdByKey(String roleKey);
    Long selectDepartmentIdByCode(String departmentCode);
    List<Map<String,Object>> selectTemplates();
    List<Map<String,Object>> selectPublishedTemplateVersionCatalog(Long templateId);
    List<Map<String,Object>> selectRoutingTargetCatalog();
    int insertTemplate(Map<String,Object> value);
    int updateTemplate(Map<String,Object> value);
    int updateTemplateMetadataConditionally(Map<String,Object> value);
    Map<String,Object> selectTemplateForUpdate(Long templateId);
    int selectNextTemplateVersionNo(Long templateId);
    int updateTemplateStatusConditionally(Map<String,Object> value);
    List<Map<String,Object>> selectAllTriggerRules(@Param("keyword") String keyword);
    int countTriggerRulesByCode(@Param("ruleCode") String ruleCode,@Param("excludeTriggerRuleId") Long excludeTriggerRuleId);
    int insertTriggerRule(Map<String,Object> value);
    int updateTriggerRule(Map<String,Object> value);
    int updateTriggerRuleSortConditionally(Map<String,Object> value);
    int updateTriggerRuleEnabledConditionally(Map<String,Object> value);
    Map<String,Object> selectTriggerBindingForUpdate(Long triggerRuleId);
    List<Map<String,Object>> selectEntrySlotBindingsForUpdate(String entrySlotCode);
    int disableEntrySlotBindings(@Param("entrySlotCode") String entrySlotCode,
            @Param("exceptTriggerRuleId") Long exceptTriggerRuleId,@Param("operator") String operator);
    int enableEntrySlotBinding(@Param("triggerRuleId") Long triggerRuleId,
            @Param("expectedVersion") int expectedVersion,@Param("operator") String operator);
    List<Map<String,Object>> selectActionTimeline(Long todoId);
    List<Map<String,Object>> selectAttachments(Long todoId);
    List<Map<String,Object>> selectCc(Long todoId);
    List<Map<String,Object>> selectRelations(Long todoId);
    List<Map<String,Object>> selectNotifications(@Param("userId") Long userId,@Param("status") String status);
    int markNotificationRead(@Param("notificationId") Long notificationId,@Param("userId") Long userId);
    int insertStationNotification(Map<String,Object> notification);
    Map<String,Object> selectExtensionContext(Long todoId);
    Map<String,Object> selectExtensionById(Long extensionId);
    Map<String,Object> selectExtensionByIdForUpdate(Long extensionId);
    Map<String,Object> selectExtensionByActionId(String actionId);
    int countApprovedExtensions(@Param("todoId") Long todoId,@Param("policyVersionId") Long policyVersionId);
    int insertExtensionRequest(Map<String,Object> extension);
    int decideExtensionConditionally(Map<String,Object> decision);
    int applyApprovedExtension(Map<String,Object> extension);
    int insertExtensionActionIfAbsent(Map<String,Object> action);
    Map<String,Object> selectExtensionActionById(String actionId);
    Map<String,Object> selectExtensionActionForUpdate(String actionId);
    int completeExtensionAction(Map<String,Object> action);
    Map<String,Object> selectPendingExtensionByTodoId(Long todoId);
    Map<String,Object> selectTemplateById(Long templateId);
    List<Map<String,Object>> selectTemplateVersions(Long templateId);
    int updateTemplateVersionDraft(Map<String,Object> version);
    int publishTemplateVersionConditionally(@Param("versionId") Long versionId,@Param("definitionHash") String definitionHash,@Param("operator") String operator);
    int insertDefinitionActionIfAbsent(Map<String,Object> action);
    Map<String,Object> selectDefinitionActionById(String actionId);
    int updateDefinitionActionEntity(@Param("actionId") String actionId,@Param("entityId") Long entityId);
    int insertDefinitionActionClaim(Map<String,Object> action);
    Map<String,Object> selectDefinitionActionForUpdate(String actionId);
    int completeDefinitionAction(@Param("actionId") String actionId,@Param("requestFingerprint") String requestFingerprint,@Param("entityId") Long entityId);
    Map<String,Object> selectBusinessTodoSummary(Map<String,Object> query);
    List<Long> selectBusinessTodoOwners(Map<String,Object> query);
    Map<String,Object> selectBusinessRecentAction(Map<String,Object> query);
    List<Map<String,Object>> selectBusinessTodos(Map<String,Object> query);
    Map<String,Object> selectRootTodo(Long rootTodoId);
    List<Map<String,Object>> selectTodoChain(Map<String,Object> query);
    int insertExceptionLogIfAbsent(Map<String,Object> value);
    Map<String,Object> selectExceptionLogByActionId(String actionId);
    int forceTerminalConditionally(@Param("todoId") Long todoId,@Param("fromStatus") String fromStatus,@Param("toStatus") String toStatus,@Param("operator") String operator);
    int transferOwnerConditionally(@Param("todoId") Long todoId,@Param("fromStatus") String fromStatus,@Param("ownerId") Long ownerId,@Param("ownerDeptId") Long ownerDeptId,@Param("operator") String operator);
    Map<String,Object> selectSlaRecord(Long todoId);
    int insertSlaWaiverIfAbsent(Map<String,Object> value);
    Map<String,Object> selectSlaWaiverByActionId(String actionId);
    int extendSlaConditionally(@Param("todoId") Long todoId,@Param("originalDueAt") LocalDateTime originalDueAt,@Param("newDueAt") LocalDateTime newDueAt);
    List<Map<String,Object>> selectOperationsDashboard();
    int insertRegeneratedTodo(Map<String,Object> value);
    int cancelActiveByBusiness(@Param("businessType") String businessType,@Param("businessId") Long businessId,@Param("exceptTodoId") Long exceptTodoId,@Param("operator") String operator);
    int insertRouteTokenIfAbsent(Map<String,Object> token);
    int insertRouteJoinIfAbsent(Map<String,Object> join);
    Map<String,Object> selectRouteJoinForUpdate(@Param("rootTodoId") Long rootTodoId,@Param("nodeKey") String nodeKey,@Param("occurrence") int occurrence);
    List<String> selectRouteTokenArrivalsForUpdate(@Param("rootTodoId") Long rootTodoId,@Param("nodeKey") String nodeKey,@Param("occurrence") int occurrence);
    int advanceRouteJoinConditionally(@Param("rootTodoId") Long rootTodoId,@Param("nodeKey") String nodeKey,@Param("occurrence") int occurrence);
    int updateInitialRouteSnapshot(@Param("todoId") Long todoId,@Param("rootTodoId") Long rootTodoId,@Param("routeToken") String routeToken,@Param("occurrenceKey") String occurrenceKey);
    List<Map<String,Object>> selectAutoActionScanItems(@Param("now") LocalDateTime now);
    int insertAutoActionExecutionIfAbsent(Map<String,Object> execution);
    Map<String,Object> selectAutoActionExecution(String executionKey);
    Map<String,Object> selectAutoActionExecutionForUpdate(String executionKey);
    int claimAutoActionRetry(@Param("executionKey") String executionKey,@Param("expectedAttempt") int expectedAttempt,@Param("now") LocalDateTime now);
    int claimStaleAutoActionExecution(@Param("executionKey") String executionKey,@Param("expectedAttempt") int expectedAttempt,@Param("staleBefore") LocalDateTime staleBefore,@Param("now") LocalDateTime now);
    int finalizeStaleAutoActionDead(@Param("executionKey") String executionKey,@Param("expectedAttempt") int expectedAttempt,@Param("finalAttempt") int finalAttempt,@Param("staleBefore") LocalDateTime staleBefore,@Param("now") LocalDateTime now,@Param("errorCode") String errorCode,@Param("errorMessage") String errorMessage);
    int completeAutoActionExecution(Map<String,Object> outcome);
    int insertAutoActionAudit(Map<String,Object> audit);
    int returnToPoolConditionally(@Param("todoId") Long todoId,@Param("fromStatus") String fromStatus,@Param("operator") String operator);
    Map<String,Object> selectSchedulePlanByIdempotencyKey(String idempotencyKey);
    List<Map<String,Object>> selectScheduleWindowsByPlanId(Long planId);
    int insertSchedulePlan(Map<String,Object> plan);
    int insertScheduleWindow(Map<String,Object> window);
    List<Map<String,Object>> selectDueScheduleWindows(@Param("now") LocalDateTime now,@Param("staleBefore") LocalDateTime staleBefore,@Param("limit") int limit);
    int claimScheduleWindow(@Param("windowId") Long windowId,@Param("expectedVersion") int expectedVersion,
            @Param("now") LocalDateTime now,@Param("staleBefore") LocalDateTime staleBefore);
    int retryScheduleWindow(@Param("windowId") Long windowId,@Param("expectedVersion") int expectedVersion,@Param("errorCode") String errorCode,@Param("now") LocalDateTime now);
    int completeScheduleWindow(@Param("windowId") Long windowId,@Param("expectedVersion") int expectedVersion,@Param("now") LocalDateTime now);
    int insertScheduleOccurrenceIfAbsent(Map<String,Object> occurrence);
    Map<String,Object> selectScheduleOccurrenceByKey(String occurrenceKey);
    Map<String,Object> selectScheduleOccurrenceById(Long occurrenceId);
    Map<String,Object> selectScheduleOccurrenceIdentity(Long occurrenceId);
    Map<String,Object> selectScheduleOccurrenceContextForUpdate(Long occurrenceId);
    Map<String,Object> selectNextScheduleWindow(@Param("planId") Long planId,
            @Param("currentWindowId") Long currentWindowId);
    Map<String,Object> selectScheduleOccurrenceIdentityByKey(String occurrenceKey);
    Map<String,Object> selectScheduleOccurrenceWindowForUpdate(@Param("occurrenceKey") String occurrenceKey,
            @Param("planId") Long planId);
    int claimScheduleOccurrence(@Param("occurrenceId") Long occurrenceId,@Param("expectedVersion") int expectedVersion,
            @Param("now") LocalDateTime now,@Param("staleBefore") LocalDateTime staleBefore);
    int linkScheduleOccurrenceByKey(@Param("occurrenceKey") String occurrenceKey,@Param("todoId") Long todoId,
            @Param("expectedVersion") int expectedVersion,@Param("now") LocalDateTime now);
    int completeScheduleOccurrence(@Param("occurrenceId") Long occurrenceId,@Param("todoId") Long todoId,@Param("now") LocalDateTime now);
    int retryScheduleOccurrence(@Param("occurrenceId") Long occurrenceId,@Param("expectedVersion") int expectedVersion,
            @Param("errorCode") String errorCode,@Param("errorMessage") String errorMessage,@Param("now") LocalDateTime now);
    int recordScheduleOccurrenceResult(@Param("occurrenceId") Long occurrenceId,@Param("result") String result,@Param("now") LocalDateTime now);
    int updateSchedulePlanWindow(@Param("planId") Long planId,@Param("windowCode") String windowCode,@Param("now") LocalDateTime now);
    Map<String,Object> selectSchedulePlanForUpdate(Long planId);
    List<Map<String,Object>> selectActiveLinkedScheduleTodosForUpdate(@Param("planId") Long planId,
            @Param("exceptOccurrenceId") Long exceptOccurrenceId);
    int cancelFutureScheduleWindows(@Param("planId") Long planId,@Param("exceptOccurrenceId") Long exceptOccurrenceId,
            @Param("reason") String reason,@Param("now") LocalDateTime now);
    int cancelFutureScheduleOccurrences(@Param("planId") Long planId,@Param("exceptOccurrenceId") Long exceptOccurrenceId,
            @Param("reason") String reason,@Param("now") LocalDateTime now);
    int completeSchedulePlan(@Param("planId") Long planId,@Param("reason") String reason,@Param("now") LocalDateTime now);
    int insertScheduledSlaRecord(Map<String,Object> record);
}

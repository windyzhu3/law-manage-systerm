package com.ruoyi.system.service.lead;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.lead.dto.LeadAssignmentPolicyCommand;
import com.law.business.lead.dto.LeadCallRecordCommand;
import com.law.business.lead.dto.LeadDeadPoolRestoreCommand;
import com.law.business.lead.dto.LeadInvalidReviewCompleteCommand;
import com.law.business.lead.dto.LeadManualCallRecordCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.application.TodoCommandService;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.domain.LeadTodoWorkItemView;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService.PolicyView;
import com.ruoyi.system.service.lead.LeadCallRecordService.CallRecordOutcome;
import com.ruoyi.system.service.lead.LeadDeadPoolService.DeadPoolOutcome;

/**
 * Application boundary for Lead/Todo HTTP operations. The compatibility
 * facade delegates here so persisted provenance and server-owned actors are
 * assembled in one place.
 */
@Service
public class LeadTodoApiService
{
    private final LeadQueryService queries;
    private final LeadTagConfirmationService tags;
    private final LeadCallRecordService calls;
    private final LeadDeadPoolService deadPool;
    private final LeadAssignmentPolicyService policies;
    private final TodoCommandService todoCommands;
    private final BusinessActorProvider actors;

    public LeadTodoApiService(LeadQueryService queries,LeadTagConfirmationService tags,
            LeadCallRecordService calls,LeadDeadPoolService deadPool,
            LeadAssignmentPolicyService policies,TodoCommandService todoCommands,
            BusinessActorProvider actors)
    {
        this.queries=queries;this.tags=tags;this.calls=calls;this.deadPool=deadPool;
        this.policies=policies;this.todoCommands=todoCommands;this.actors=actors;
    }

    @Transactional
    public void confirmTag(Long tagRelationId)
    {
        tags.confirmPersistedRelation(tagRelationId);
    }

    public List<LeadTodoWorkItemView> callTimeline(Long leadId)
    {
        return queries.callTimeline(leadId);
    }

    @Transactional
    public CallRecordOutcome addManualCallRecord(Long leadId,LeadManualCallRecordCommand request)
    {
        LeadCallRecordCommand command=new LeadCallRecordCommand();
        command.setLeadId(leadId);
        command.setCallChannel("MANUAL");
        command.setBusinessOccurrenceKey(request.getActionId());
        command.setStartedAt(request.getStartedAt());
        command.setEndedAt(request.getEndedAt());
        command.setDurationSeconds(request.getDurationSeconds());
        command.setCallResult(request.getCallResult());
        command.setRecordingFileObjectId(request.getRecordingFileObjectId());
        command.setManualNotes(request.getManualNotes());
        return calls.record(command);
    }

    public List<LeadTodoWorkItemView> invalidReviewQueue(String status,String keyword)
    {
        return queries.invalidReviewQueue(status,keyword);
    }

    @Transactional
    public TodoInstance completeInvalidReview(Long todoId,LeadInvalidReviewCompleteCommand request)
    {
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("reviewResult",request.getReviewResult());
        fields.put("reviewComment",request.getReviewComment());
        BusinessActor actor=actors.current();
        return todoCommands.complete(todoId,new ActionCommand(request.getActionId(),
                request.getReviewComment(),fields,request.getFileObjectIds()),
                new Actor(actor.userId(),actor.userName(),actor.deptId()));
    }

    public List<LeadTodoWorkItemView> retryQueue(String status,String keyword)
    {
        return queries.retryQueue(status,keyword);
    }

    public List<LeadTodoWorkItemView> retryTimeline(Long leadId)
    {
        return queries.retryTimeline(leadId);
    }

    public List<LeadTodoWorkItemView> deadPoolQueue(String reasonCode,String keyword)
    {
        return queries.deadPoolQueue(reasonCode,keyword);
    }

    @Transactional
    public DeadPoolOutcome restoreDeadPool(Long leadId,LeadDeadPoolRestoreCommand command)
    {
        return deadPool.restoreToPublicPool(leadId,command.getActionId(),command.getReason());
    }

    public List<PolicyView> assignmentPolicies()
    {
        return policies.list();
    }

    @Transactional
    public PolicyView saveAssignmentPolicy(LeadAssignmentPolicyCommand command)
    {
        return policies.save(command);
    }
}

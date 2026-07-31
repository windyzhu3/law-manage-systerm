package com.ruoyi.system.service.lead;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.business.lead.dto.LeadProgressCompleteCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.schedule.TodoScheduleService.CreateSchedulePlanCommand;
import com.law.todo.schedule.TodoScheduleService.SchedulePurpose;
import com.law.todo.schedule.TodoScheduleService.ScheduleWindowRule;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadProgressCycleService
{
    private static final String TEMPLATE_CODE="TD-004";
    private static final String BUSINESS_TYPE="LEAD";
    private static final String PROOF_TYPE="FOLLOWUP_PROOF";
    private static final int MAX_FUTURE_DRIFT_MINUTES=5;
    private static final ScheduleWindowRule FIVE_DAY_WINDOW=
            new ScheduleWindowRule("P5D",0,0,null,null,0,7200,1,1);

    private final BizLeadMapper leads;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;
    private final TodoMapper todos;
    private final TodoScheduleService schedules;
    private final LeadAssignmentPolicyService policies;
    private final Clock clock;

    @Autowired
    public LeadProgressCycleService(BizLeadMapper leads,BusinessActorProvider actors,
            ISysDictTypeService dictionaries,TodoMapper todos,TodoScheduleService schedules,
            LeadAssignmentPolicyService policies)
    {
        this(leads,actors,dictionaries,todos,schedules,policies,Clock.systemDefaultZone());
    }

    LeadProgressCycleService(BizLeadMapper leads,BusinessActorProvider actors,
            ISysDictTypeService dictionaries,TodoMapper todos,TodoScheduleService schedules,
            LeadAssignmentPolicyService policies,Clock clock)
    {
        this.leads=leads;this.actors=actors;this.dictionaries=dictionaries;this.todos=todos;
        this.schedules=schedules;this.policies=policies;this.clock=clock;
    }

    @Transactional
    public ProgressCycleOutcome complete(LeadProgressCompleteCommand command,TodoInstance todo)
    {
        validateIdentity(command,todo);
        BizLead lead=leads.selectLeadById(command.getLeadId());
        require(lead!=null,BusinessErrorCode.DATA_NOT_FOUND,"Lead does not exist");
        BusinessActor actor=actors.current();
        require(actor.administrator()||actor.userId().equals(lead.getOwnerId()),
                BusinessErrorCode.ACCESS_DENIED,"Only the current lead owner may record progress");
        require(Objects.equals(lead.getOwnerId(),todo.getOwnerId()),BusinessErrorCode.STATE_CONFLICT,
                "Todo owner no longer matches the current lead owner");
        require("0".equals(lead.getDelFlag())&&"0".equals(lead.getPoolStatus())
                &&"ACTIVE".equals(lead.getDisposition())&&lead.getOwnerId()!=null,
                BusinessErrorCode.STATE_CONFLICT,"Lead is no longer active");

        String progressType=trim(command.getProgressType());
        requireDict(progressType);
        LocalDateTime progressAt=command.getProgressAt().withNano(0);
        require(!progressAt.isAfter(LocalDateTime.now(clock).plusMinutes(MAX_FUTURE_DRIFT_MINUTES)),
                BusinessErrorCode.VALIDATION_FAILED,"Progress time is materially in the future");
        List<String> attachmentTypes=todos.selectAttachmentTypes(todo.getTodoId());
        require(attachmentTypes!=null&&attachmentTypes.contains(PROOF_TYPE),
                BusinessErrorCode.PRECONDITION_FAILED,"FOLLOWUP_PROOF attachment is required");

        String factKey="LEAD_PROGRESS:"+todo.getTodoId();
        String remark=normalizeRemark(command.getRemark());
        BizLeadFollowup existing=leads.selectProgressFollowupByIdempotencyKey(factKey);
        if(existing!=null)
            return replayOrRecover(existing,lead,todo,actor,progressType,progressAt,remark,true);

        BizLeadFollowup candidate=followup(lead,todo,actor,progressType,progressAt,remark,factKey);
        int inserted=leads.insertProgressFollowupIfAbsent(candidate);
        require(inserted==0||inserted==1,BusinessErrorCode.CONCURRENT_MODIFICATION,
                "Progress fact write returned an invalid result");
        BizLeadFollowup locked=leads.selectProgressFollowupByIdempotencyKeyForUpdate(factKey);
        require(locked!=null&&locked.getFollowupId()!=null,BusinessErrorCode.CONCURRENT_MODIFICATION,
                "Progress fact could not be locked after insert");
        return replayOrRecover(locked,lead,todo,actor,progressType,progressAt,remark,inserted==0);
    }

    private ProgressCycleOutcome replayOrRecover(BizLeadFollowup fact,BizLead lead,TodoInstance todo,
            BusinessActor actor,String progressType,LocalDateTime progressAt,String remark,boolean replayed)
    {
        requireMatchingFact(fact,lead,todo,progressType,progressAt,remark);
        if(fact.getSchedulePlanId()!=null)
        {
            requireMatchingPlan(fact,todo,progressAt);
            return outcome(fact,true);
        }
        BizLeadFollowup locked=leads.selectProgressFollowupByIdempotencyKeyForUpdate(
                "LEAD_PROGRESS:"+todo.getTodoId());
        require(locked!=null&&locked.getFollowupId()!=null,BusinessErrorCode.CONCURRENT_MODIFICATION,
                "Progress fact disappeared while recovering its schedule");
        requireMatchingFact(locked,lead,todo,progressType,progressAt,remark);
        if(locked.getSchedulePlanId()!=null)
        {
            requireMatchingPlan(locked,todo,progressAt);
            return outcome(locked,true);
        }

        LeadAssignmentPolicyService.ProgressSchedulePolicy policy=policies.resolveProgressSchedule(lead);
        long planId=schedules.createPlan(new CreateSchedulePlanCommand(
                todo.getTodoId(),todo.getTemplateVersionId(),BUSINESS_TYPE,lead.getLeadId(),progressAt,
                policy.timezone(),policy.ruleVersionId(),policy.policyId(),policy.policyVersion(),
                List.of(FIVE_DAY_WINDOW),SchedulePurpose.LEAD_PROGRESS_5D,
                scheduleKey(lead.getLeadId(),todo.getTodoId())));
        require(planId>0,BusinessErrorCode.CONCURRENT_MODIFICATION,
                "Five-day schedule identity was not generated");
        require(leads.linkProgressFollowupSchedule(locked.getFollowupId(),planId,actor.userName())==1,
                BusinessErrorCode.CONCURRENT_MODIFICATION,"Progress fact schedule link failed");
        locked.setSchedulePlanId(planId);
        return outcome(locked,replayed);
    }

    private BizLeadFollowup followup(BizLead lead,TodoInstance todo,BusinessActor actor,
            String progressType,LocalDateTime progressAt,String remark,String factKey)
    {
        BizLeadFollowup value=new BizLeadFollowup();value.setLeadId(lead.getLeadId());
        value.setFollowType(progressType);value.setFollowResult("SUBSTANTIVE_PROGRESS");
        value.setProgressAt(progressAt);value.setContent(remark);
        value.setNextFollowTime(Date.from(progressAt.plusDays(5).atZone(clock.getZone()).toInstant()));
        value.setFollowUserId(actor.userId());value.setFollowUserName(actor.displayName());
        value.setSourceTodoId(todo.getTodoId());value.setIdempotencyKey(factKey);
        value.setTaskStatus("1");value.setCreateBy(actor.userName());return value;
    }

    private void requireMatchingFact(BizLeadFollowup fact,BizLead lead,TodoInstance todo,
            String progressType,LocalDateTime progressAt,String remark)
    {
        boolean matches=Objects.equals(fact.getLeadId(),lead.getLeadId())
                &&Objects.equals(fact.getSourceTodoId(),todo.getTodoId())
                &&progressType.equals(fact.getFollowType())
                &&"SUBSTANTIVE_PROGRESS".equals(fact.getFollowResult())
                &&Objects.equals(progressAt,normalize(fact.getProgressAt()))
                &&remark.equals(normalizeRemark(fact.getContent()))
                &&Objects.equals("LEAD_PROGRESS:"+todo.getTodoId(),fact.getIdempotencyKey())
                &&Objects.equals(progressAt.plusDays(5),nextFollowAt(fact));
        require(matches,BusinessErrorCode.DUPLICATE_OPERATION,
                "Source Todo already owns another immutable progress fact");
    }

    private void requireMatchingPlan(BizLeadFollowup fact,TodoInstance todo,LocalDateTime progressAt)
    {
        Map<String,Object> plan=todos.selectSchedulePlanForUpdate(fact.getSchedulePlanId());
        List<Map<String,Object>> windows=todos.selectScheduleWindowsByPlanIdForUpdate(
                fact.getSchedulePlanId());
        boolean matches=plan!=null
                &&Objects.equals(fact.getSchedulePlanId(),longValue(plan,"planId","plan_id"))
                &&Objects.equals(todo.getTodoId(),longValue(plan,"previousTodoId","previous_todo_id"))
                &&Objects.equals(todo.getTemplateVersionId(),longValue(plan,"templateVersionId","template_version_id"))
                &&BUSINESS_TYPE.equals(text(plan,"businessType","business_type"))
                &&Objects.equals(todo.getBusinessId(),longValue(plan,"businessId","business_id"))
                &&SchedulePurpose.LEAD_PROGRESS_5D.name().equals(text(plan,"schedulePurpose","schedule_purpose"))
                &&scheduleKey(todo.getBusinessId(),todo.getTodoId()).equals(
                        text(plan,"idempotencyKey","idempotency_key"))
                &&Objects.equals(progressAt,dateTime(plan,"firstContactAt","first_contact_at"))
                &&longValue(plan,"assignmentPolicyId","assignment_policy_id")!=null
                &&integerValue(plan,"assignmentPolicyVersion","assignment_policy_version")!=null
                &&longValue(plan,"ruleVersionId","rule_version_id")!=null
                &&"RESOLVED_POLICY".equals(text(plan,"assignmentPolicySnapshotSource",
                        "assignment_policy_snapshot_source"))
                &&matchingWindow(windows,progressAt);
        require(matches,BusinessErrorCode.DUPLICATE_OPERATION,
                "Progress fact is linked to another immutable five-day plan");
    }

    private boolean matchingWindow(List<Map<String,Object>> windows,LocalDateTime progressAt)
    {
        if(windows==null||windows.size()!=1)return false;
        Map<String,Object> window=windows.get(0);
        return "P5D".equals(text(window,"windowCode","window_code"))
                &&Integer.valueOf(0).equals(integerValue(window,"windowOrder","window_order"))
                &&Integer.valueOf(0).equals(integerValue(window,"dayOffset","day_offset"))
                &&Objects.equals(progressAt,dateTime(window,"materializeAt","materialize_at"))
                &&Objects.equals(progressAt.plusDays(5),dateTime(window,"dueAt","due_at"))
                &&Integer.valueOf(1).equals(integerValue(window,"maxAttempts","max_attempts"))
                &&Integer.valueOf(1).equals(integerValue(window,"occurrenceNo","occurrence_no"));
    }

    private ProgressCycleOutcome outcome(BizLeadFollowup value,boolean replayed)
    {
        require(value.getSchedulePlanId()!=null,BusinessErrorCode.CONCURRENT_MODIFICATION,
                "Progress cycle cannot complete without a linked schedule");
        return new ProgressCycleOutcome(value.getFollowupId(),value.getSchedulePlanId(),
                normalize(value.getProgressAt()).plusDays(5),replayed);
    }

    private void validateIdentity(LeadProgressCompleteCommand command,TodoInstance todo)
    {
        require(command!=null&&command.getLeadId()!=null&&command.getLeadId()>0
                &&command.getTodoId()!=null&&command.getTodoId()>0
                &&command.getProgressType()!=null&&!command.getProgressType().isBlank()
                &&command.getProgressAt()!=null,BusinessErrorCode.VALIDATION_FAILED,
                "Progress completion command is incomplete");
        require(todo!=null&&TEMPLATE_CODE.equals(todo.getTemplateCode())
                &&BUSINESS_TYPE.equals(todo.getBusinessType())&&todo.getTodoId()!=null
                &&todo.getBusinessId()!=null&&todo.getTemplateVersionId()!=null
                &&todo.getTemplateVersionId()>0,BusinessErrorCode.PRECONDITION_FAILED,
                "TD-004 LEAD Todo context is required");
        require(command.getTodoId().equals(todo.getTodoId())
                &&command.getLeadId().equals(todo.getBusinessId()),
                BusinessErrorCode.PRECONDITION_FAILED,"Command identity does not match the source Todo");
    }

    private void requireDict(String progressType)
    {
        List<SysDictData> values=dictionaries.selectDictDataByType("law_lead_progress_type");
        require(values!=null&&values.stream().anyMatch(item->
                        progressType!=null&&progressType.equals(item.getDictValue())),
                BusinessErrorCode.VALIDATION_FAILED,
                "Controlled dictionary value is invalid: law_lead_progress_type");
    }

    private LocalDateTime nextFollowAt(BizLeadFollowup fact)
    {
        return fact.getNextFollowTime()==null?null:fact.getNextFollowTime().toInstant()
                .atZone(clock.getZone()).toLocalDateTime().withNano(0);
    }

    private LocalDateTime normalize(LocalDateTime value){return value==null?null:value.withNano(0);}
    private String normalizeRemark(String value){return value==null?"":value.trim();}
    private String trim(String value){return value==null?null:value.trim();}
    private String scheduleKey(Long leadId,Long todoId){return "LEAD_PROGRESS_5D:"+leadId+":"+todoId;}

    private String text(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        return value==null?null:String.valueOf(value);
    }
    private Long longValue(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        return value==null?null:value instanceof Number number?number.longValue()
                :Long.valueOf(String.valueOf(value));
    }
    private Integer integerValue(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        return value==null?null:value instanceof Number number?number.intValue()
                :Integer.valueOf(String.valueOf(value));
    }
    private LocalDateTime dateTime(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        if(value==null)return null;
        if(value instanceof LocalDateTime dateTime)return dateTime.withNano(0);
        if(value instanceof java.sql.Timestamp timestamp)return timestamp.toLocalDateTime().withNano(0);
        if(value instanceof Date date)return date.toInstant().atZone(ZoneId.systemDefault())
                .toLocalDateTime().withNano(0);
        return LocalDateTime.parse(String.valueOf(value).replace(' ','T')).withNano(0);
    }

    private void require(boolean condition,BusinessErrorCode code,String message)
    {if(!condition)throw new ServiceException(message,code.name());}

    public record ProgressCycleOutcome(long followupId,long schedulePlanId,
            LocalDateTime nextDueAt,boolean replayed) { }
}

package com.ruoyi.system.service.lead;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.lead.dto.LeadAssignmentPolicyCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.LeadPermissions;
import com.law.todo.schedule.TodoScheduleService.ScheduleWindowRule;
import com.law.todo.mapper.TodoMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadAssignmentPolicy;
import com.ruoyi.system.domain.LeadAssignmentPolicyCandidateView;
import com.ruoyi.system.mapper.LeadFlowMapper;

@Service
public class LeadAssignmentPolicyService
{
    private final LeadFlowMapper mapper;
    private final BusinessActorProvider actors;
    private final LeadPermissionPolicy permissions;
    private final LeadAccessPolicy access;
    private final TodoMapper todos;
    private final CanonicalRetryWindowValidator windows;

    public LeadAssignmentPolicyService(LeadFlowMapper mapper)
    {
        this(mapper,null,null,null,null,new CanonicalRetryWindowValidator());
    }

    @Autowired
    public LeadAssignmentPolicyService(LeadFlowMapper mapper,BusinessActorProvider actors,
            LeadPermissionPolicy permissions,LeadAccessPolicy access,TodoMapper todos)
    {
        this(mapper,actors,permissions,access,todos,new CanonicalRetryWindowValidator());
    }

    LeadAssignmentPolicyService(LeadFlowMapper mapper,BusinessActorProvider actors,
            LeadPermissionPolicy permissions,LeadAccessPolicy access,TodoMapper todos,
            CanonicalRetryWindowValidator windows)
    {
        this.mapper=mapper;this.actors=actors;this.permissions=permissions;this.access=access;
        this.todos=todos;this.windows=windows;
    }

    public ResolvedPolicy resolve(Long salesDeptId, String sourceCode)
    {
        if (salesDeptId == null) throw new ServiceException("Sales department is required",
                BusinessErrorCode.VALIDATION_FAILED.name());
        String source = sourceCode == null || sourceCode.isBlank() ? "*" : sourceCode.trim();
        BizLeadAssignmentPolicy policy = mapper.selectActiveAssignmentPolicy(salesDeptId, source);
        if (policy == null) throw new ServiceException("No active assignment policy",
                BusinessErrorCode.DATA_NOT_FOUND.name());
        List<Long> candidates = mapper.selectActivePolicyCandidates(policy.getPolicyId());
        return new ResolvedPolicy(policy.getPolicyId(), policy.getRetryRuleJson(),
                candidates == null ? List.of() : List.copyOf(candidates));
    }

    public RetrySchedulePolicy resolveRetrySchedule(BizLead lead)
    {
        if(lead==null||lead.getDeptId()==null)throw error("Lead owner department is required");
        BizLeadAssignmentPolicy policy=mapper.selectActiveAssignmentPolicy(lead.getDeptId(),
                lead.getSourceCode()==null||lead.getSourceCode().isBlank()?"*":lead.getSourceCode().trim());
        if(policy==null)throw new ServiceException("No active assignment policy",
                BusinessErrorCode.DATA_NOT_FOUND.name());
        try
        {
            JSONObject json=JSON.parseObject(policy.getRetryRuleJson());
            Long templateVersionId=json.getLong("templateVersionId");
            Long ruleVersionId=json.getLong("ruleVersionId");
            String timezone=json.getString("timezone");
            if(templateVersionId==null||templateVersionId<=0)
                throw error("Retry template version is invalid");
            if(ruleVersionId==null||ruleVersionId<=0)
                throw error("Retry rule version is invalid");
            if(timezone==null||timezone.isBlank())timezone="Asia/Shanghai";
            ZoneId.of(timezone);
            JSONArray rows=json.getJSONArray("windows");
            if(rows==null||rows.isEmpty())throw error("Retry policy windows are required");
            List<ScheduleWindowRule> windows=new ArrayList<>();
            for(int index=0;index<rows.size();index++)
            {
                JSONObject row=rows.getJSONObject(index);
                windows.add(new ScheduleWindowRule(row.getString("windowCode"),
                        requiredInt(row,"windowOrder"),requiredInt(row,"dayOffset"),
                        time(row.getString("startTime")),time(row.getString("endTime")),
                        row.getInteger("startOffsetMinutes"),row.getInteger("durationMinutes"),
                        requiredInt(row,"maxAttempts"),row.getInteger("occurrenceNo")==null
                                ?1:row.getIntValue("occurrenceNo")));
            }
            return new RetrySchedulePolicy(policy.getPolicyId(),policy.getRowVersion(),templateVersionId,
                    ruleVersionId,timezone,this.windows.validate(windows));
        }
        catch(ServiceException known){throw known;}
        catch(RuntimeException invalid){throw error("Retry policy JSON is invalid");}
    }

    public List<PolicyView> list()
    {
        requireApiDependencies();
        permissions.require(LeadPermissions.ASSIGNMENT_POLICY_LIST);
        BusinessActor actor=actors.current();
        List<BizLeadAssignmentPolicy> policies=mapper.selectAssignmentPolicies(actor.userId(),
                actor.deptId(),!actor.administrator());
        if(policies==null||policies.isEmpty())return List.of();
        List<Long> ids=policies.stream().map(BizLeadAssignmentPolicy::getPolicyId).toList();
        List<LeadAssignmentPolicyCandidateView> candidateRows=
                mapper.selectAssignmentPolicyCandidateViews(ids);
        Map<Long,List<LeadAssignmentPolicyCandidateView>> candidates=new LinkedHashMap<>();
        if(candidateRows!=null)for(LeadAssignmentPolicyCandidateView candidate:candidateRows)
            candidates.computeIfAbsent(candidate.getPolicyId(),ignored->new ArrayList<>()).add(candidate);
        return policies.stream().map(policy->new PolicyView(policy.getPolicyId(),policy.getPolicyCode(),
                policy.getPolicyName(),policy.getSalesDeptId(),policy.getSourceCode(),
                policy.getRetryRuleJson(),policy.getStatus(),policy.getRowVersion(),
                List.copyOf(candidates.getOrDefault(policy.getPolicyId(),List.of())))).toList();
    }

    @Transactional
    public PolicyView save(LeadAssignmentPolicyCommand command)
    {
        requireApiDependencies();
        permissions.require(LeadPermissions.ASSIGNMENT_POLICY_EDIT);
        validateCommand(command);
        access.requireDepartmentAdministerable(command.getSalesDeptId());
        validatePublishedTd003(command.getTemplateVersionId());
        validateCandidates(command.getSalesDeptId(),command.getCandidateUserIds());
        String source=command.getSourceCode().trim();
        String ruleJson=ruleJson(command);
        BusinessActor actor=actors.current();
        BizLeadAssignmentPolicy policy;
        if(command.getPolicyId()==null)
        {
            require(command.getExpectedVersion()==0,"New policy expectedVersion must be zero");
            policy=new BizLeadAssignmentPolicy();
            policy.setPolicyCode(policyCode(command.getSalesDeptId(),source));
            policy.setPolicyName(command.getPolicyName().trim());
            policy.setSalesDeptId(command.getSalesDeptId());
            policy.setSourceCode(source);
            policy.setBusinessType("LEAD");
            policy.setRetryRuleJson(ruleJson);
            policy.setStatus("ACTIVE");
            policy.setRowVersion(0);
            policy.setCreateBy(actor.userName());
            changed(mapper.insertAssignmentPolicy(policy),"Assignment policy could not be created");
        }
        else
        {
            policy=mapper.selectAssignmentPolicyByIdForUpdate(command.getPolicyId());
            require(policy!=null&&"LEAD".equals(policy.getBusinessType())
                    &&"ACTIVE".equals(policy.getStatus()),"Assignment policy does not exist");
            access.requireDepartmentAdministerable(policy.getSalesDeptId());
            require(policy.getRowVersion().equals(command.getExpectedVersion()),
                    "Assignment policy version changed");
            changed(mapper.updateAssignmentPolicyConditionally(command.getPolicyId(),
                    command.getPolicyName().trim(),command.getSalesDeptId(),source,ruleJson,
                    command.getExpectedVersion(),actor.userName()),"Assignment policy version changed");
            policy.setPolicyName(command.getPolicyName().trim());
            policy.setSalesDeptId(command.getSalesDeptId());
            policy.setSourceCode(source);
            policy.setRetryRuleJson(ruleJson);
            policy.setRowVersion(command.getExpectedVersion()+1);
        }
        mapper.deleteAssignmentPolicyCandidates(policy.getPolicyId());
        List<Long> stableCandidates=List.copyOf(command.getCandidateUserIds());
        for(int index=0;index<stableCandidates.size();index++)
            changed(mapper.insertAssignmentPolicyCandidate(policy.getPolicyId(),
                    stableCandidates.get(index),index,actor.userName()),
                    "Assignment policy candidate could not be saved");
        List<LeadAssignmentPolicyCandidateView> rows=
                mapper.selectAssignmentPolicyCandidateViews(List.of(policy.getPolicyId()));
        return new PolicyView(policy.getPolicyId(),policy.getPolicyCode(),policy.getPolicyName(),
                policy.getSalesDeptId(),policy.getSourceCode(),policy.getRetryRuleJson(),
                "ACTIVE",policy.getRowVersion(),rows==null?List.of():List.copyOf(rows));
    }

    private void validateCommand(LeadAssignmentPolicyCommand command)
    {
        require(command!=null&&command.getPolicyName()!=null&&command.getSalesDeptId()!=null
                &&command.getSourceCode()!=null&&command.getExpectedVersion()!=null
                &&command.getTemplateVersionId()!=null&&command.getRuleVersionId()!=null
                &&command.getTimezone()!=null&&command.getCandidateUserIds()!=null
                &&command.getWindows()!=null,"Assignment policy command is incomplete");
        try{ZoneId.of(command.getTimezone().trim());}
        catch(RuntimeException invalid){throw error("Assignment policy timezone is invalid");}
        require(!command.getCandidateUserIds().isEmpty()
                &&command.getCandidateUserIds().stream().allMatch(value->value!=null&&value>0)
                &&command.getCandidateUserIds().stream().distinct().count()
                    ==command.getCandidateUserIds().size(),"Assignment policy candidates are invalid");
        require(!command.getWindows().isEmpty(),"Retry windows are required");
        List<ScheduleWindowRule> rules=new ArrayList<>();
        for(LeadAssignmentPolicyCommand.RetryWindow window:command.getWindows())
        {
            require(window!=null&&window.getWindowCode()!=null&&window.getWindowOrder()!=null
                    &&window.getDayOffset()!=null&&window.getMaxAttempts()!=null
                    &&window.getMaxAttempts()>0,"Retry window is incomplete");
            rules.add(new ScheduleWindowRule(window.getWindowCode().trim(),
                    window.getWindowOrder(),window.getDayOffset(),
                    time(window.getStartTime()),time(window.getEndTime()),
                    window.getStartOffsetMinutes(),window.getDurationMinutes(),
                    window.getMaxAttempts(),window.getOccurrenceNo()==null
                            ?1:window.getOccurrenceNo()));
        }
        windows.validate(rules);
    }

    private void validatePublishedTd003(Long versionId)
    {
        Map<String,Object> version=todos.selectTemplateVersionById(versionId);
        String code=text(version,"template_code","templateCode");
        String status=text(version,"status","status");
        String businessType=text(version,"business_type","businessType");
        require("TD-003".equals(code)&&"PUBLISHED".equals(status)&&"LEAD".equals(businessType),
                "Retry template must be the published TD-003 LEAD version");
    }

    private void validateCandidates(Long deptId,List<Long> candidates)
    {
        List<Long> active=mapper.selectActiveCandidateUsersInDepartment(deptId,candidates);
        require(active!=null&&new HashSet<>(active).equals(new HashSet<>(candidates)),
                "Every policy candidate must be an active user in the sales department");
    }

    private String ruleJson(LeadAssignmentPolicyCommand command)
    {
        JSONObject root=new JSONObject();
        root.put("templateVersionId",command.getTemplateVersionId());
        root.put("ruleVersionId",command.getRuleVersionId());
        root.put("timezone",command.getTimezone().trim());
        JSONArray windows=new JSONArray();
        command.getWindows().stream()
                .sorted(Comparator.comparing(LeadAssignmentPolicyCommand.RetryWindow::getWindowOrder))
                .forEach(window->{
                    JSONObject value=new JSONObject();
                    value.put("windowCode",window.getWindowCode().trim());
                    value.put("windowOrder",window.getWindowOrder());
                    value.put("dayOffset",window.getDayOffset());
                    if(window.getStartTime()!=null)value.put("startTime",window.getStartTime().trim());
                    if(window.getEndTime()!=null)value.put("endTime",window.getEndTime().trim());
                    if(window.getStartOffsetMinutes()!=null)
                        value.put("startOffsetMinutes",window.getStartOffsetMinutes());
                    if(window.getDurationMinutes()!=null)
                        value.put("durationMinutes",window.getDurationMinutes());
                    value.put("maxAttempts",window.getMaxAttempts());
                    value.put("occurrenceNo",window.getOccurrenceNo()==null?1:window.getOccurrenceNo());
                    windows.add(value);
                });
        root.put("windows",windows);
        return root.toJSONString();
    }

    private String policyCode(Long deptId,String source)
    {
        return ("LEAD-"+deptId+"-"+source).toUpperCase()
                .replaceAll("[^A-Z0-9*_-]","_");
    }

    private String text(Map<String,Object> map,String snake,String camel)
    {
        if(map==null)return null;
        Object value=map.containsKey(snake)?map.get(snake):map.get(camel);
        return value==null?null:String.valueOf(value);
    }

    private void requireApiDependencies()
    {
        if(actors==null||permissions==null||access==null||todos==null)
            throw new IllegalStateException("Assignment policy API dependencies are unavailable");
    }
    private void changed(int rows,String message){require(rows==1,message);}
    private void require(boolean condition,String message)
    {if(!condition)throw error(message);}

    private int requiredInt(JSONObject value,String key)
    {
        Integer number=value.getInteger(key);
        if(number==null)throw error("Retry policy field is missing: "+key);
        return number;
    }
    private LocalTime time(String value){return value==null||value.isBlank()?null:LocalTime.parse(value);}
    private ServiceException error(String message)
    {
        return new ServiceException(message,BusinessErrorCode.PRECONDITION_FAILED.name());
    }

    public record ResolvedPolicy(Long policyId, String retryRuleJson, List<Long> candidateUserIds) { }
    public record RetrySchedulePolicy(Long policyId,Integer policyVersion,Long templateVersionId,
            Long ruleVersionId,String timezone,List<ScheduleWindowRule> windows) { }
    public record PolicyView(Long policyId,String policyCode,String policyName,Long salesDeptId,
            String sourceCode,String retryRuleJson,String status,Integer rowVersion,
            List<LeadAssignmentPolicyCandidateView> candidates) { }
}

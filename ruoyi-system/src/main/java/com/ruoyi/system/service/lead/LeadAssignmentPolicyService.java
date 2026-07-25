package com.ruoyi.system.service.lead;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.todo.schedule.TodoScheduleService.ScheduleWindowRule;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadAssignmentPolicy;
import com.ruoyi.system.mapper.LeadFlowMapper;

@Service
public class LeadAssignmentPolicyService
{
    private final LeadFlowMapper mapper;

    public LeadAssignmentPolicyService(LeadFlowMapper mapper) { this.mapper = mapper; }

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
                    ruleVersionId,timezone,List.copyOf(windows));
        }
        catch(ServiceException known){throw known;}
        catch(RuntimeException invalid){throw error("Retry policy JSON is invalid");}
    }

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
}

package com.ruoyi.system.service.lead;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.domain.LeadTodoWorkItemView;
import com.ruoyi.system.mapper.BizLeadMapper;

@Service
public class LeadQueryService
{
    private final BizLeadMapper mapper;
    private final BusinessActorProvider actors;
    private final LeadAccessPolicy access;

    public LeadQueryService(BizLeadMapper mapper, BusinessActorProvider actors, LeadAccessPolicy access)
    {
        this.mapper = mapper;
        this.actors = actors;
        this.access = access;
    }

    public List<BizLead> list(BizLead query)
    {
        BusinessActor actor = actors.current();
        query.setCurrentUserId(actor.userId());
        query.setCurrentDeptId(actor.deptId());
        query.setDataScope(!actor.administrator() && !"pool".equals(query.getListMode()));
        return mapper.selectLeadList(query);
    }

    public BizLead detail(Long leadId)
    {
        return access.requireReadable(leadId, false, false);
    }

    public List<BizLeadSetting> settings(BizLeadSetting query)
    {
        return mapper.selectSettingList(query);
    }

    public Map<String, Object> dashboard()
    {
        BusinessActor actor = actors.current();
        Boolean dataScope = !actor.administrator();
        Map<String, Object> data = new HashMap<>();
        data.put("cards", mapper.selectDashboardCards(actor.userId(), actor.deptId(), dataScope));
        data.put("sources", mapper.selectSourceStats(actor.userId(), actor.deptId(), dataScope));
        data.put("statuses", mapper.selectStatusStats(actor.userId(), actor.deptId(), dataScope));
        return data;
    }

    public List<LeadTodoWorkItemView> callTimeline(Long leadId)
    {
        access.requireReadable(leadId, false, false);
        return mapper.selectLeadCallTimeline(leadId);
    }

    public List<LeadTodoWorkItemView> invalidReviewQueue(String status, String keyword)
    {
        BusinessActor actor = actors.current();
        return mapper.selectLeadInvalidReviewQueue(trim(status), trim(keyword), actor.userId(),
                actor.deptId(), !actor.administrator());
    }

    public List<LeadTodoWorkItemView> retryQueue(String status, String keyword)
    {
        BusinessActor actor = actors.current();
        return mapper.selectLeadRetryQueue(trim(status), trim(keyword), actor.userId(),
                actor.deptId(), !actor.administrator());
    }

    public List<LeadTodoWorkItemView> retryTimeline(Long leadId)
    {
        access.requireReadable(leadId, false, false);
        return mapper.selectLeadRetryTimeline(leadId);
    }

    public List<LeadTodoWorkItemView> deadPoolQueue(String reasonCode, String keyword)
    {
        BusinessActor actor = actors.current();
        return mapper.selectLeadDeadPoolQueue(trim(reasonCode), trim(keyword), actor.userId(),
                actor.deptId(), !actor.administrator());
    }

    private String trim(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

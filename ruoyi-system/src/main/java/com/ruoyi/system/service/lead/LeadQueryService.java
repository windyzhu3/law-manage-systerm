package com.ruoyi.system.service.lead;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.law.todo.application.TodoBusinessViewService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.model.TodoInstance;
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
    private final TodoBusinessViewService todoViews;

    public LeadQueryService(BizLeadMapper mapper, BusinessActorProvider actors, LeadAccessPolicy access)
    {
        this(mapper, actors, access, null);
    }

    @Autowired
    public LeadQueryService(BizLeadMapper mapper, BusinessActorProvider actors, LeadAccessPolicy access,
            TodoBusinessViewService todoViews)
    {
        this.mapper = mapper;
        this.actors = actors;
        this.access = access;
        this.todoViews = todoViews;
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
        return withAllowedActions(mapper.selectLeadInvalidReviewQueue(trim(status), trim(keyword),
                actor.userId(), actor.deptId(), !actor.administrator()), actor);
    }

    public List<LeadTodoWorkItemView> retryQueue(String status, String keyword)
    {
        BusinessActor actor = actors.current();
        return withAllowedActions(mapper.selectLeadRetryQueue(trim(status), trim(keyword),
                actor.userId(), actor.deptId(), !actor.administrator()), actor);
    }

    public List<LeadTodoWorkItemView> retryTimeline(Long leadId)
    {
        access.requireReadable(leadId, false, false);
        return mapper.selectLeadRetryTimeline(leadId);
    }

    public List<LeadTodoWorkItemView> deadPoolQueue(String reasonCode, String keyword)
    {
        BusinessActor actor = actors.current();
        return withAllowedActions(mapper.selectLeadDeadPoolQueue(trim(reasonCode), trim(keyword),
                actor.userId(), actor.deptId(), !actor.administrator()), actor);
    }

    private List<LeadTodoWorkItemView> withAllowedActions(List<LeadTodoWorkItemView> rows,
            BusinessActor actor)
    {
        if(rows==null||rows.isEmpty())return rows==null?List.of():rows;
        if(todoViews==null)return rows;
        Actor todoActor=new Actor(actor.userId(),actor.userName(),actor.deptId());
        List<TodoInstance> todos=rows.stream().map(row->{
            TodoInstance todo=new TodoInstance();
            todo.setTodoId(row.getTodoId());todo.setStatus(row.getTodoStatus());
            todo.setOwnerId(row.getTodoOwnerId());
            todo.setOwnerDeptId(row.getBusinessDeptId());
            return todo;
        }).toList();
        Map<Long,List<String>> actions=todoViews.allowedActions(todos,todoActor);
        for(LeadTodoWorkItemView row:rows)
        {
            row.setAllowedActions(actions.getOrDefault(row.getTodoId(),List.of()));
        }
        return rows;
    }

    private String trim(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

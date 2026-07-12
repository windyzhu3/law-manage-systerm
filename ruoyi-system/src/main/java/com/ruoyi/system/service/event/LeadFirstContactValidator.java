package com.ruoyi.system.service.event;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoBusinessValidator;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;

@Component
public class LeadFirstContactValidator implements TodoBusinessValidator
{
    private static final Set<String> CONTACTABLE = Set.of("1", "2");
    private final BizLeadMapper mapper;

    public LeadFirstContactValidator(BizLeadMapper mapper){this.mapper=mapper;}
    public boolean supports(String businessType){return "LEAD".equals(businessType);}

    public void validate(TodoInstance todo, Map<String,Object> payload)
    {
        if(!"线索首联".equals(todo.getTitle()))return;
        BizLead lead=mapper.selectLeadById(todo.getBusinessId());
        if(lead==null||"2".equals(lead.getDelFlag()))throw new TodoException("LEAD_NOT_FOUND","关联线索不存在或已删除");
        if(!CONTACTABLE.contains(lead.getStatus()))throw new TodoException("LEAD_STATE_INVALID","线索当前状态不允许完成首联");
        if(todo.getOwnerId()==null||!todo.getOwnerId().equals(lead.getOwnerId()))throw new TodoException("LEAD_OWNER_CHANGED","线索负责人已变化，请取消旧待办并重新生成");
    }
}

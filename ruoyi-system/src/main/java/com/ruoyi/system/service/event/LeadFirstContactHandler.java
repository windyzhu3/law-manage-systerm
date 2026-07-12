package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.mapper.BizLeadMapper;

@Component
public class LeadFirstContactHandler implements TodoCompletionHandler
{
    private final BizLeadMapper mapper;public LeadFirstContactHandler(BizLeadMapper mapper){this.mapper=mapper;}
    public boolean supports(TodoInstance todo){return "LEAD".equals(todo.getBusinessType())&&"线索首联".equals(todo.getTitle());}
    public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName)
    {
        BizLeadFollowup followup=new BizLeadFollowup();followup.setLeadId(todo.getBusinessId());followup.setFollowType(text(payload.getOrDefault("followType","phone")));followup.setFollowResult(text(payload.get("contactResult")));followup.setContent(text(payload.getOrDefault("content","完成首次联系")));followup.setFollowUserId(operatorId);followup.setFollowUserName(operatorName);followup.setTaskStatus("completed");followup.setCreateBy(operatorName);
        if(mapper.insertFollowup(followup)<=0)throw new TodoException("LEAD_FOLLOWUP_CREATE_FAILED","首联跟进记录创建失败");mapper.touchLeadFollowTime(todo.getBusinessId(),null,operatorName);
    }
    private String text(Object value){return value==null?null:String.valueOf(value);}
}

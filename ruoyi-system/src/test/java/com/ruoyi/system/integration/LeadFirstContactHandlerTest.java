package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.event.LeadFirstContactHandler;

@ExtendWith(MockitoExtension.class)
class LeadFirstContactHandlerTest
{
    @Mock BizLeadMapper mapper;

    @Test void completionIsKeyedByTemplateCodeAndWritesLeadFollowup()
    {
        TodoInstance todo=new TodoInstance();todo.setTemplateCode("LEAD_FIRST_CONTACT");todo.setTitle("custom title");
        todo.setBusinessType("LEAD");todo.setBusinessId(7L);
        LeadFirstContactHandler handler=new LeadFirstContactHandler(mapper);assertTrue(handler.supports(todo));
        when(mapper.insertFollowup(any())).thenReturn(1);
        handler.complete(todo,Map.of("contactResult","connected","content","客户同意进一步沟通"),8L,"alice");
        verify(mapper).insertFollowup(argThat(f->f.getLeadId().equals(7L)&&"connected".equals(f.getFollowResult())&&f.getFollowUserId().equals(8L)));
    }
}

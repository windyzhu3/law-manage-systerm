package com.ruoyi.system.integration;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
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
    @Test void completionWritesLeadFollowup()
    {
        TodoInstance todo=new TodoInstance();todo.setTitle("线索首联");todo.setBusinessType("LEAD");todo.setBusinessId(7L);
        when(mapper.insertFollowup(any())).thenReturn(1);
        new LeadFirstContactHandler(mapper).complete(todo,Map.of("contactResult","connected","content","客户同意进一步沟通"),8L,"alice");
        verify(mapper).insertFollowup(argThat(f->f.getLeadId().equals(7L)&&"connected".equals(f.getFollowResult())&&f.getFollowUserId().equals(8L)));
    }
}

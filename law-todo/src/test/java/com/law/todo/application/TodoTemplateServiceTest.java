package com.law.todo.application;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoTemplateServiceTest
{
    @Mock TodoMapper mapper;
    @Test void createsTemplate(){when(mapper.insertTemplate(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTemplate(new java.util.HashMap<>(Map.of("templateCode","T1","templateName","测试","businessType","LEAD")));verify(mapper).insertTemplate(anyMap());}
    @Test void savesTriggerRule(){when(mapper.insertTriggerRule(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTrigger(new java.util.HashMap<>(Map.of("eventType","LEAD_ASSIGNED","templateId",1L,"templateVersionId",2L,"businessType","LEAD")));verify(mapper).insertTriggerRule(anyMap());}
}

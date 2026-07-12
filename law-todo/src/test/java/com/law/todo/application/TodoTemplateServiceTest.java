package com.law.todo.application;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.TodoException;

@ExtendWith(MockitoExtension.class)
class TodoTemplateServiceTest
{
    @Mock TodoMapper mapper;
    @Test void createsTemplate(){when(mapper.insertTemplate(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTemplate(new java.util.HashMap<>(Map.of("templateCode","T1","templateName","测试","businessType","LEAD")));verify(mapper).insertTemplate(anyMap());}
    @Test void savesTriggerRule(){when(mapper.insertTriggerRule(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTrigger(new java.util.HashMap<>(Map.of("eventType","LEAD_ASSIGNED","templateId",1L,"templateVersionId",2L,"businessType","LEAD")));verify(mapper).insertTriggerRule(anyMap());}
    @Test void rejectsInvalidTemplateJson(){TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).publish(1L,1,"OWNER","{}","{}",null,"{}","admin"));assertEquals("TODO_TEMPLATE_JSON_INVALID",error.getBusinessCode());}
    @Test void publishesUiSchemaInImmutableVersion(){when(mapper.selectTemplateVersion(1L,1)).thenReturn(null);doAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",8L);return 1;}).when(mapper).insertTemplateVersion(anyMap());new TodoTemplateService(mapper).publish(1L,1,"\"OWNER\"","{}","{}",null,"{\"type\":\"form\"}","admin");verify(mapper).insertTemplateVersion(org.mockito.ArgumentMatchers.argThat(value->"{\"type\":\"form\"}".equals(value.get("uiSchemaJson"))));}
}

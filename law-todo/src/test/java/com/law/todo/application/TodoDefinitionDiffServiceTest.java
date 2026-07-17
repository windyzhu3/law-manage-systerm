package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoDefinitionDiffServiceTest
{
    @Mock TodoMapper mapper;

    @Test void semantic_diff_ignores_json_key_order_and_sorts_changes()
    {
        when(mapper.selectTemplateVersionById(1L)).thenReturn(version("""
          {"schemaVersion":1,"templateCode":"T","event":{"eventType":"E","payloadVersion":1,"condition":{}},
           "owner":{"config":{"type":"USER","userId":7}},"dod":{"config":{}},"sla":{"config":{"minutes":60,"calendarCode":"D"}},
           "ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
          """));
        when(mapper.selectTemplateVersionById(2L)).thenReturn(version("""
          {"templateCode":"T","schemaVersion":1,"event":{"condition":{},"payloadVersion":1,"eventType":"E"},
           "owner":{"config":{"userId":8,"type":"USER"}},"dod":{"config":{}},"sla":{"config":{"calendarCode":"D","minutes":120}},
           "ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
          """));

        var result=new TodoDefinitionDiffService(mapper).diff(1L,2L);

        assertEquals(2,result.changes().size());
        assertEquals("owner",result.changes().get(0).section());
        assertEquals("sla",result.changes().get(1).section());
        assertTrue(result.changes().stream().allMatch(change -> change.risk()!=null));
    }

    private Map<String,Object> version(String definition)
    {
        Map<String,Object> row=new HashMap<>();row.put("definition_json",definition);row.put("status","PUBLISHED");return row;
    }
}

package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.TodoException;

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
        assertEquals(List.of("$.owner.config.userId","$.sla.config.minutes"),result.changes().stream().map(change->change.path()).toList());
    }

    @Test void semantic_diff_normalizes_nodes_edges_rules_and_references_by_business_key()
    {
        when(mapper.selectTemplateVersionById(1L)).thenReturn(version(definition(
                "[{\"key\":\"a\",\"type\":\"TASK\",\"templateVersionId\":1},{\"key\":\"b\",\"type\":\"END\"}]",
                "[{\"key\":\"ab\",\"from\":\"a\",\"to\":\"b\"}]",
                "[{\"config\":{\"ruleKey\":\"r1\",\"actionType\":\"ESCALATE\",\"triggerAt\":\"DUE\"}},{\"config\":{\"ruleKey\":\"r2\",\"actionType\":\"RETURN_TO_POOL\",\"triggerAt\":\"SLA_150\"}}]",
                "[\"D1\",\"D2\"]","[\"A1\",\"A2\"]")));
        when(mapper.selectTemplateVersionById(2L)).thenReturn(version(definition(
                "[{\"type\":\"END\",\"key\":\"b\"},{\"templateVersionId\":1,\"type\":\"TASK\",\"key\":\"a\"}]",
                "[{\"to\":\"b\",\"key\":\"ab\",\"from\":\"a\"}]",
                "[{\"config\":{\"triggerAt\":\"SLA_150\",\"actionType\":\"RETURN_TO_POOL\",\"ruleKey\":\"r2\"}},{\"config\":{\"triggerAt\":\"DUE\",\"ruleKey\":\"r1\",\"actionType\":\"ESCALATE\"}}]",
                "[\"D2\",\"D1\"]","[\"A2\",\"A1\"]")));

        assertTrue(new TodoDefinitionDiffService(mapper).diff(1L,2L).changes().isEmpty());
    }

    @Test void semantic_diff_covers_every_definition_contract_with_stable_leaf_paths()
    {
        when(mapper.selectTemplateVersionById(1L)).thenReturn(version("""
          {"schemaVersion":1,"templateCode":"T1","event":{"eventType":"E1","payloadVersion":1,"condition":{}},
           "owner":{"config":{"type":"USER","userId":7}},"dod":{"config":{"requiredFields":["summary"]}},
           "sla":{"config":{"minutes":60}},"ui":{"config":{"title":"A"}},
           "routing":{"config":{"start":"a","nodes":[{"key":"a","type":"TASK","templateVersionId":1}],"edges":[]}},
           "autoActions":[{"config":{"ruleKey":"r1","actionType":"ESCALATE","triggerAt":"DUE"}}],
           "decisionRefs":["D1"],"acceptanceRefs":["A1"]}
          """));
        when(mapper.selectTemplateVersionById(2L)).thenReturn(version("""
          {"schemaVersion":2,"templateCode":"T2","event":{"eventType":"E2","payloadVersion":2,"condition":{}},
           "owner":{"config":{"type":"USER","userId":8}},"dod":{"config":{"requiredFields":["result"]}},
           "sla":{"config":{"minutes":120}},"ui":{"config":{"title":"B"}},
           "routing":{"config":{"start":"a","nodes":[{"key":"a","type":"TASK","templateVersionId":2}],"edges":[]}},
           "autoActions":[{"config":{"ruleKey":"r1","actionType":"RETURN_TO_POOL","triggerAt":"DUE"}}],
           "decisionRefs":["D2"],"acceptanceRefs":["A2"]}
          """));

        var result=new TodoDefinitionDiffService(mapper).diff(1L,2L);
        Set<String> sections=result.changes().stream().map(change->change.section()).collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of("schemaVersion","templateCode","event","owner","dod","sla","ui","routing","autoActions","decisionRefs","acceptanceRefs"),sections);
        assertTrue(result.changes().stream().anyMatch(change->change.path().equals("$.routing.config.nodes[a].templateVersionId")));
        assertTrue(result.changes().stream().anyMatch(change->change.path().equals("$.autoActions[r1].config.actionType")));
        assertEquals(result.changes().stream().map(change->change.path()).sorted().toList(),result.changes().stream().map(change->change.path()).toList());
    }

    @Test void semantic_diff_rejects_versions_from_different_templates()
    {
        Map<String,Object> left=version("{\"schemaVersion\":1}");left.put("template_id",10L);
        Map<String,Object> right=version("{\"schemaVersion\":1}");right.put("template_id",11L);
        when(mapper.selectTemplateVersionById(1L)).thenReturn(left);
        when(mapper.selectTemplateVersionById(2L)).thenReturn(right);

        TodoException failure=assertThrows(TodoException.class,()->new TodoDefinitionDiffService(mapper).diff(1L,2L));
        assertEquals("TODO_TEMPLATE_VERSION_DIFF_TEMPLATE_MISMATCH",failure.getBusinessCode());
    }

    @Test void release_diff_rejects_a_mutable_endpoint()
    {
        Map<String,Object> left=version("{\"schemaVersion\":1}");left.put("status","DRAFT");
        Map<String,Object> right=version("{\"schemaVersion\":1}");
        when(mapper.selectTemplateVersionById(1L)).thenReturn(left);
        when(mapper.selectTemplateVersionById(2L)).thenReturn(right);

        TodoException failure=assertThrows(TodoException.class,()->new TodoDefinitionDiffService(mapper).diffImmutable(1L,2L));

        assertEquals("TODO_RELEASE_DIFF_IMMUTABLE_REQUIRED",failure.getBusinessCode());
    }

    private String definition(String nodes,String edges,String actions,String decisions,String acceptances)
    {
        return "{\"schemaVersion\":1,\"templateCode\":\"T\",\"event\":{\"eventType\":\"E\",\"payloadVersion\":1,\"condition\":{}},"
                +"\"owner\":{\"config\":{}},\"dod\":{\"config\":{}},\"sla\":{\"config\":{}},\"ui\":{\"config\":{}},"
                +"\"routing\":{\"config\":{\"start\":\"a\",\"nodes\":"+nodes+",\"edges\":"+edges+"}},"
                +"\"autoActions\":"+actions+",\"decisionRefs\":"+decisions+",\"acceptanceRefs\":"+acceptances+"}";
    }

    private Map<String,Object> version(String definition)
    {
        Map<String,Object> row=new HashMap<>();row.put("definition_json",definition);row.put("status","PUBLISHED");row.put("template_id",99L);return row;
    }
}

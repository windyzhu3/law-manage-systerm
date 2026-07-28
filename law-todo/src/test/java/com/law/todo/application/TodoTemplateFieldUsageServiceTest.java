package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TodoTemplateFieldUsageServiceTest
{
    private final TodoTemplateFieldUsageService service=new TodoTemplateFieldUsageService();

    @Test void scopesFieldsToTheCurrentTemplateAndTheirJourneyStages()
    {
        Map<String,java.util.List<TodoTemplateFieldUsageService.FieldUsage>> result=service.usages("""
                {
                  "schemaVersion":1,
                  "templateCode":"TD-001",
                  "event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                  "owner":{"config":{"type":"PAYLOAD","field":"ownerId"}},
                  "dod":{"config":{
                    "requiredFields":["contactResult","contactedAt"],
                    "conditionalRequired":[
                      {"field":"name","when":{"field":"contactResult","equals":"VALID"}},
                      {"field":"city","when":{"field":"contactResult","equals":"VALID"}},
                      {"field":"demand","when":{"field":"contactResult","equals":"VALID"}},
                      {"field":"visited","when":{"field":"contactResult","equals":"VALID"}}
                    ]}},
                  "sla":{"config":{"calendarCode":"DEFAULT","minutes":30}},
                  "ui":{"config":{"fields":[
                    {"key":"contactResult","type":"dict"},
                    {"key":"contactedAt","type":"datetime"},
                    {"key":"name","type":"text"},
                    {"key":"city","type":"text"},
                    {"key":"demand","type":"textarea"},
                    {"key":"visited","type":"dict"}]}},
                  "routing":{"config":{"nodes":[{"key":"result","field":"contactResult"}]}},
                  "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]
                }
                """);

        assertThat(result.get("ownerId"))
                .extracting(TodoTemplateFieldUsageService.FieldUsage::stage)
                .containsExactly("OWNER_INPUT");
        assertThat(result.get("contactResult"))
                .extracting(TodoTemplateFieldUsageService.FieldUsage::stage)
                .contains("COMPLETION_INPUT","ROUTING_INPUT");
        assertThat(result).containsKeys("contactedAt","name","city","demand","visited");
        assertThat(result).doesNotContainKeys("reviewResult","reviewOpinion","attemptStage","attemptCount");
    }

    @Test void marksConditionalCompletionFieldsWithoutMakingThemUnconditionallyRequired()
    {
        Map<String,java.util.List<TodoTemplateFieldUsageService.FieldUsage>> result=service.usages("""
                {"schemaVersion":1,"templateCode":"TD-001",
                 "event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "dod":{"config":{"requiredFields":["contactResult"],
                    "conditionalRequired":[{"field":"name","when":{"field":"contactResult","equals":"VALID"}}]}},
                 "ui":{"config":{"fields":[{"key":"contactResult"},{"key":"name"}]}},
                 "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """);

        assertThat(result.get("contactResult")).anyMatch(usage->usage.required()&&!usage.conditional());
        assertThat(result.get("name")).anyMatch(usage->!usage.required()&&usage.conditional()
                &&"contactResult".equals(usage.conditionField())&&"VALID".equals(usage.conditionValue()));
    }
}

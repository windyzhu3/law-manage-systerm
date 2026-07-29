package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;

class TodoJourneyDependencyServiceTest
{
    private final TodoJourneyDependencyService service=new TodoJourneyDependencyService();

    @Test
    void completionResultChangesAffectDodRoutingAndSimulationEvidence()
    {
        TodoDefinitionDocument before=definition(
                Map.of("completionFields",Map.of("contactResult",Map.of("required",false))),
                Map.of("businessOutcomes",List.of(Map.of("value","VALID","targetTemplateCode","TD-004"))),
                Map.of("title","首联待办"));
        TodoDefinitionDocument after=definition(
                Map.of("completionFields",Map.of("contactResult",Map.of("required",true))),
                Map.of("businessOutcomes",List.of(Map.of("value","VALID","targetTemplateCode","TD-004"))),
                Map.of("title","首联待办"));

        var impact=service.analyze(before,after);

        assertThat(impact.changedPaths()).anyMatch(path->path.contains("contactResult"));
        assertThat(impact.affectedSteps()).containsExactly("DOD","ROUTING","SIMULATION_PUBLISH");
        assertThat(impact.invalidatedEvidence()).containsExactly("SIMULATION_SCENARIOS");
        assertThat(impact.message()).contains("完成结果","后续路由","重新验证");
    }

    @Test
    void routeOnlyChangesAffectRoutingAndSimulationEvidence()
    {
        TodoDefinitionDocument before=definition(Map.of(),
                Map.of("businessOutcomes",List.of(Map.of("value","VALID","targetTemplateCode","TD-004"))),
                Map.of("title","首联待办"));
        TodoDefinitionDocument after=definition(Map.of(),
                Map.of("businessOutcomes",List.of(Map.of("value","VALID","targetTemplateCode","TD-002"))),
                Map.of("title","首联待办"));

        var impact=service.analyze(before,after);

        assertThat(impact.affectedSteps()).containsExactly("ROUTING","SIMULATION_PUBLISH");
        assertThat(impact.invalidatedEvidence()).containsExactly("SIMULATION_SCENARIOS");
    }

    @Test
    void presentationOnlyTitleChangesDoNotInvalidateSimulationEvidence()
    {
        TodoDefinitionDocument before=definition(Map.of(),Map.of(),Map.of("title","首联待办"));
        TodoDefinitionDocument after=definition(Map.of(),Map.of(),Map.of("title","首联联系"));

        var impact=service.analyze(before,after);

        assertThat(impact.changedPaths()).containsExactly("ui.config.title");
        assertThat(impact.affectedSteps()).containsExactly("SIMULATION_PUBLISH");
        assertThat(impact.invalidatedEvidence()).isEmpty();
        assertThat(impact.message()).contains("展示信息").doesNotContain("重新验证");
    }

    private TodoDefinitionDocument definition(Map<String,Object> dod,Map<String,Object> routing,Map<String,Object> ui)
    {
        return new TodoDefinitionDocument(1,"TD-001",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","PAYLOAD","field","ownerId")),new DodRule(dod),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),new UiSchema(ui),
                new RoutingGraph(routing),List.of(),List.of(),List.of());
    }
}

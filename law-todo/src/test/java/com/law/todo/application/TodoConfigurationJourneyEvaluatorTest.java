package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.application.view.TodoConfigurationViews.TemplateVersionDetail;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationJourneyEvaluatorTest
{
    @Mock TodoConfigurationResourceCatalogService resources;
    @Mock TodoTemplateService templates;
    private TodoConfigurationJourneyEvaluator evaluator;

    @BeforeEach void setUp()
    {
        evaluator=new TodoConfigurationJourneyEvaluator(resources,templates);
        when(resources.fields("LEAD")).thenReturn(List.of(new FieldResource("leadId","Lead", "integer",true,
                List.of(),List.of("LEAD_ASSIGNED"))));
        when(templates.listTemplateCalendarCatalog()).thenReturn(List.of(Map.of("calendarCode","DEFAULT")));
    }

    @Test void blocksEventStepWhenActiveEventHasNoUsableSchema()
    {
        when(resources.fields("LEAD")).thenReturn(List.of());

        var result=evaluator.evaluate(detail(),definition("LEAD_ASSIGNED",Map.of("simulationStatus","SUCCESS")));

        assertThat(result.step("EVENT").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code)
                .contains("TODO_JOURNEY_EVENT_SCHEMA_REQUIRED");
    }

    @Test void appliesBlockerBeforeWarningAndKeepsSevenStepOrder()
    {
        var result=evaluator.evaluate(detail(),definition("LEAD_ASSIGNED",Map.of("simulationStatus","FAILED")));

        assertThat(result.steps()).extracting(step->step.code()).containsExactly(
                "EVENT","TRIGGER","OWNER","DOD","SLA","ROUTING","SIMULATION_PUBLISH");
        assertThat(result.step("TRIGGER").state()).isEqualTo("WARNING");
        assertThat(result.step("SIMULATION_PUBLISH").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::severity)
                .containsSubsequence("WARNING","BLOCKER");
    }

    @Test void projectsExactDeepSafeValuesForAllSevenJourneySteps()
    {
        Map<String,Object> condition=Map.of("all",List.of(
                Map.of("field","lead.owner.id","operator","EQ","value",7),
                Map.of("field","lead.tags","operator","CONTAINS","value","VIP")));
        Map<String,Object> owner=Map.of("type","USER","value",7,"fallback",Map.of("type","SUPERVISOR"));
        Map<String,Object> dod=Map.of("requiredFields",List.of("leadId","contactedAt"),
                "evidence",Map.of("types",List.of("NOTE","FILE")));
        Map<String,Object> sla=Map.of("calendarCode","DEFAULT","minutes",60,"reminders",List.of(15,30));
        Map<String,Object> routing=Map.of("start","review","nodes",List.of(Map.of("key","review","label","复核")),
                "edges",List.of(Map.of("from","review","to","done","condition",Map.of("present",true))));
        Map<String,Object> ui=Map.of("businessStage","QUALIFY","simulationStatus","SUCCESS",
                "simulationDefinitionHash","hash-42","panels",List.of(Map.of("code","summary")));
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",2,condition),
                new OwnerRule(owner),new DodRule(dod),new SlaRule(sla),new UiSchema(ui),new RoutingGraph(routing),
                List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition);

        assertThat(result.steps()).extracting(step->step.code()).containsExactly(
                "EVENT","TRIGGER","OWNER","DOD","SLA","ROUTING","SIMULATION_PUBLISH");
        assertThat(result.step("EVENT").value()).isEqualTo(Map.of("eventType","LEAD_ASSIGNED","payloadVersion",2));
        assertThat(result.step("TRIGGER").value()).isEqualTo(Map.of("condition",condition));
        assertThat(result.step("OWNER").value()).isEqualTo(Map.of("config",owner));
        assertThat(result.step("DOD").value()).isEqualTo(Map.of("config",dod));
        assertThat(result.step("SLA").value()).isEqualTo(Map.of("config",sla));
        assertThat(result.step("ROUTING").value()).isEqualTo(Map.of("config",routing));
        assertThat(result.step("SIMULATION_PUBLISH").value()).isEqualTo(Map.of("config",ui));
        assertThatThrownBy(()->result.step("TRIGGER").value().put("condition",Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked") List<Object> clauses=(List<Object>)((Map<String,Object>)result.step("TRIGGER").value()
                .get("condition")).get("all");
        assertThatThrownBy(()->clauses.add(Map.of())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void blocksUnresolvedOwnerWithoutFallback()
    {
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of()),new DodRule(Map.of("requiredFields",List.of("leadId"))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),new UiSchema(Map.of("simulationStatus","SUCCESS")),
                new RoutingGraph(Map.of()),List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition);

        assertThat(result.step("OWNER").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code).contains("TODO_JOURNEY_OWNER_FALLBACK_REQUIRED");
    }

    @Test void blocksSuccessfulSimulationWithoutTheCurrentDefinitionHash()
    {
        var result=evaluator.evaluate(detail(),definition("LEAD_ASSIGNED",Map.of("simulationStatus","SUCCESS")));

        assertThat(result.step("SIMULATION_PUBLISH").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code).contains("TODO_JOURNEY_SIMULATION_REQUIRED");
    }

    @Test void blocksOwnerWithBlankOperandAndNoFallback()
    {
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","USER","value","")),new DodRule(Map.of("requiredFields",List.of("leadId"))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),new UiSchema(Map.of("simulationStatus","SUCCESS")),
                new RoutingGraph(Map.of()),List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition);

        assertThat(result.step("OWNER").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code).contains("TODO_JOURNEY_OWNER_FALLBACK_REQUIRED");
    }

    @Test void blocksUnavailableCalendarAndInvalidRouting()
    {
        when(templates.listTemplateCalendarCatalog()).thenReturn(List.of());
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","USER","value",7)),new DodRule(Map.of("requiredFields",List.of("leadId"))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),new UiSchema(Map.of("simulationStatus","SUCCESS")),
                new RoutingGraph(Map.of("start","missing","nodes",List.of(),"edges",List.of())),List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition);

        assertThat(result.step("SLA").state()).isEqualTo("BLOCKED");
        assertThat(result.step("ROUTING").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code).contains(
                "TODO_JOURNEY_CALENDAR_REQUIRED","TODO_JOURNEY_ROUTING_INVALID");
    }

    private TemplateConfigurationDetail detail()
    {
        return new TemplateConfigurationDetail(42L,"TODO-42","Lead follow-up","LEAD","0",4,4,101L,"DRAFT",91L,3,
                new TemplateVersionDetail(101L,4,"DRAFT",91L,1,"{}","{}","{}","{}","{}","{}","hash-42","{}",
                        null,null,null,null,null,LocalDateTime.of(2026,7,23,9,0)),List.of());
    }

    private TodoDefinitionDocument definition(String eventType,Map<String,Object> ui)
    {
        return new TodoDefinitionDocument(1,"TODO-42",new EventRule(eventType,1,Map.of()),
                new OwnerRule(Map.of("type","USER","value",7)),new DodRule(Map.of("requiredFields",List.of("leadId"))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),new UiSchema(ui),new RoutingGraph(Map.of()),
                List.of(),List.of(),List.of());
    }
}

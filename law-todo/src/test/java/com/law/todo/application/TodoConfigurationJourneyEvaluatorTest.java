package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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
import com.law.todo.application.view.TodoSimulationReadinessView;
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
    @Mock TodoBusinessOutcomeCatalogService outcomes;
    private TodoConfigurationJourneyEvaluator evaluator;

    @BeforeEach void setUp()
    {
        evaluator=new TodoConfigurationJourneyEvaluator(resources,templates,outcomes);
        List<FieldResource> fields=List.of(
                new FieldResource("leadId","Lead","integer",true,List.of(),List.of("LEAD_ASSIGNED")),
                new FieldResource("assignmentId","分配记录ID","integer",false,List.of("EQ","NE","NOT_EMPTY"),List.of("LEAD_ASSIGNED")));
        when(resources.fields("LEAD")).thenReturn(fields);
        lenient().when(resources.fields("LEAD","LEAD_ASSIGNED")).thenReturn(fields);
        when(templates.listTemplateCalendarCatalog()).thenReturn(List.of(Map.of("calendarCode","DEFAULT")));
    }

    @Test void blocksEventStepWhenActiveEventHasNoUsableSchema()
    {
        when(resources.fields("LEAD")).thenReturn(List.of());

        var result=evaluator.evaluate(detail(),definition("LEAD_ASSIGNED",Map.of("simulationStatus","SUCCESS")),ready());

        assertThat(result.step("EVENT").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code)
                .contains("TODO_JOURNEY_EVENT_SCHEMA_REQUIRED");
    }

    @Test void appliesBlockerBeforeWarningAndKeepsSevenStepOrder()
    {
        var result=evaluator.evaluate(detail(),definition("LEAD_ASSIGNED",Map.of("simulationStatus","FAILED")),blocked());

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

        var result=evaluator.evaluate(detail(),definition,ready());

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

        var result=evaluator.evaluate(detail(),definition,ready());

        assertThat(result.step("OWNER").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code).contains("TODO_JOURNEY_OWNER_FALLBACK_REQUIRED");
    }

    @Test void ignoresDefinitionEmbeddedSimulationStatusAndUsesTheReadinessProjection()
    {
        var result=evaluator.evaluate(detail(),definition("LEAD_ASSIGNED",
                Map.of("simulationStatus","SUCCESS","simulationDefinitionHash","hash-42")),blocked());

        assertThat(result.step("SIMULATION_PUBLISH").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code).contains("TODO_FULL_SIMULATION_REQUIRED");
    }

    @Test void mapsMissingConditionValuesToTheTriggerStepAndExactField()
    {
        Map<String,Object> predicate=new java.util.LinkedHashMap<>();
        predicate.put("field","assignmentId");predicate.put("operator","NE");predicate.put("value",null);
        Map<String,Object> condition=Map.of("$expression",Map.of(
                "version",1,"root",Map.of("type","AND","conditions",List.of(predicate))));
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",
                new EventRule("LEAD_ASSIGNED",1,condition),
                new OwnerRule(Map.of("type","USER","value",7)),
                new DodRule(Map.of("requiredFields",List.of("leadId"))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),
                new UiSchema(Map.of("simulationStatus","SUCCESS")),
                new RoutingGraph(Map.of()),List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition,ready());

        assertThat(result.step("TRIGGER").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).anySatisfy(issue->{
            assertThat(issue.code()).isEqualTo("TODO_CONDITION_VALUE_REQUIRED");
            assertThat(issue.stepCode()).isEqualTo("TRIGGER");
            assertThat(issue.fieldPath()).isEqualTo("event.condition.assignmentId");
        });
    }

    @Test void canonicalMaterialAndConditionalEvidenceCompletesDodStep()
    {
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",
                new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","USER","value",7)),
                new DodRule(Map.of(
                        "materials",List.of(Map.of("type","CALL_NOTE","minCount",2)),
                        "conditionalRequired",List.of(Map.of("field","leadId",
                                "when",Map.of("field","connected","equals",true))))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),
                new UiSchema(Map.of("simulationStatus","SUCCESS")),
                new RoutingGraph(Map.of()),List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition,ready());

        assertThat(result.step("DOD").state()).isEqualTo("COMPLETED");
        assertThat(result.issues()).extracting(JourneyIssue::code)
                .doesNotContain("TODO_JOURNEY_DOD_RECOMMENDATION");
    }

    @Test void blocksOwnerWithBlankOperandAndNoFallback()
    {
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","USER","value","")),new DodRule(Map.of("requiredFields",List.of("leadId"))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),new UiSchema(Map.of("simulationStatus","SUCCESS")),
                new RoutingGraph(Map.of()),List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition,ready());

        assertThat(result.step("OWNER").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code).contains("TODO_JOURNEY_OWNER_FALLBACK_REQUIRED");
    }

    @Test void acceptsStableRoleKeyOwnerReference()
    {
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","ROLE","roleKey","case_manager")),
                new DodRule(Map.of("requiredFields",List.of("leadId"))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),new UiSchema(Map.of("simulationStatus","SUCCESS")),
                new RoutingGraph(Map.of()),List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition,ready());

        assertThat(result.step("OWNER").state()).isNotEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code)
                .doesNotContain("TODO_JOURNEY_OWNER_FALLBACK_REQUIRED");
    }

    @Test void blocksUnavailableCalendarAndInvalidRouting()
    {
        when(templates.listTemplateCalendarCatalog()).thenReturn(List.of());
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","USER","value",7)),new DodRule(Map.of("requiredFields",List.of("leadId"))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),new UiSchema(Map.of("simulationStatus","SUCCESS")),
                new RoutingGraph(Map.of("start","missing","nodes",List.of(),"edges",List.of())),List.of(),List.of(),List.of());

        var result=evaluator.evaluate(detail(),definition,ready());

        assertThat(result.step("SLA").state()).isEqualTo("BLOCKED");
        assertThat(result.step("ROUTING").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code).contains(
                "TODO_JOURNEY_CALENDAR_REQUIRED","TODO_JOURNEY_ROUTING_INVALID");
    }

    @Test void acceptsWindowScheduledSlaWithoutScalarDuration()
    {
        TodoDefinitionDocument base=definition("LEAD_ASSIGNED",Map.of());
        TodoDefinitionDocument scheduled=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),
                base.event(),base.owner(),base.dod(),new SlaRule(Map.of(
                        "calendarCode","DEFAULT","schedule",Map.of("windows",List.of(
                                Map.of("windowCode","T0","dayOffset",0,"startOffsetMinutes",0,
                                        "durationMinutes",120,"maxAttempts",3),
                                Map.of("windowCode","T1_AM","dayOffset",1,"startTime","09:00:00",
                                        "endTime","11:00:00","maxAttempts",1))))),
                base.ui(),base.routing(),base.autoActions(),base.decisionRefs(),base.acceptanceRefs());

        var result=evaluator.evaluate(detail(),scheduled,ready());

        assertThat(result.step("SLA").state()).isEqualTo("COMPLETED");
        assertThat(result.issues()).extracting(JourneyIssue::code)
                .doesNotContain("TODO_JOURNEY_SLA_DURATION_REQUIRED");
    }

    @Test void acceptsScalarOnlySla()
    {
        var result=evaluator.evaluate(detail(),definition("LEAD_ASSIGNED",Map.of()),ready());

        assertThat(result.step("SLA").state()).isEqualTo("COMPLETED");
        assertThat(result.issues()).extracting(JourneyIssue::code)
                .doesNotContain("TODO_JOURNEY_SLA_DURATION_REQUIRED",
                        "TODO_JOURNEY_SCHEDULE_WINDOWS_INVALID");
    }

    @Test void blocksStructurallyInvalidScheduleWindows()
    {
        TodoDefinitionDocument base=definition("LEAD_ASSIGNED",Map.of());
        TodoDefinitionDocument malformed=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),
                base.event(),base.owner(),base.dod(),new SlaRule(Map.of(
                        "calendarCode","DEFAULT","schedule",Map.of("windows",List.of(Map.of())))),
                base.ui(),base.routing(),base.autoActions(),base.decisionRefs(),base.acceptanceRefs());

        var result=evaluator.evaluate(detail(),malformed,ready());

        assertThat(result.step("SLA").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(JourneyIssue::code)
                .contains("TODO_JOURNEY_SCHEDULE_WINDOWS_INVALID");
    }

    @Test void blocksEveryPresentInvalidOrMixedScheduleInsteadOfUsingScalarFallback()
    {
        TodoDefinitionDocument base=definition("LEAD_ASSIGNED",Map.of());
        List<Map<String,Object>> invalid=List.of(
                Map.of("calendarCode","DEFAULT","minutes",60,"schedule",Map.of(
                        "windows",List.of(Map.of()))),
                Map.of("calendarCode","DEFAULT","schedule",Map.of("windows","T0")),
                Map.of("calendarCode","DEFAULT","schedule",Map.of("windows",List.of())),
                Map.of("calendarCode","DEFAULT","schedule",List.of("T0")));

        for(Map<String,Object> sla:invalid)
        {
            TodoDefinitionDocument configured=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),
                    base.event(),base.owner(),base.dod(),new SlaRule(sla),base.ui(),base.routing(),
                    base.autoActions(),base.decisionRefs(),base.acceptanceRefs());
            var result=evaluator.evaluate(detail(),configured,ready());

            assertThat(result.step("SLA").state()).isEqualTo("BLOCKED");
            assertThat(result.issues()).extracting(JourneyIssue::code)
                    .contains("TODO_JOURNEY_SCHEDULE_WINDOWS_INVALID");
        }
    }

    @Test void mapsTypedOutcomeCompletenessIssuesToTheRoutingStep()
    {
        TodoDefinitionDocument definition=definition("LEAD_ASSIGNED",Map.of("simulationStatus","SUCCESS"));
        when(outcomes.validate("TODO-42","LEAD",definition)).thenReturn(List.of(
                new TodoBusinessOutcomeCatalogService.OutcomeIssue(
                        "TODO_ROUTING_OUTCOME_INCOMPLETE","routing.businessOutcomes",
                        "请为每个首联结果配置唯一的后续待办")));

        var result=evaluator.evaluate(detail(),definition,ready());

        assertThat(result.step("ROUTING").state()).isEqualTo("BLOCKED");
        assertThat(result.issues()).anySatisfy(issue->{
            assertThat(issue.code()).isEqualTo("TODO_ROUTING_OUTCOME_INCOMPLETE");
            assertThat(issue.stepCode()).isEqualTo("ROUTING");
            assertThat(issue.fieldPath()).isEqualTo("routing.businessOutcomes");
        });
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

    private TodoSimulationReadinessView ready()
    {
        return new TodoSimulationReadinessView(42L,101L,"hash-42",
                3,3,List.of(),true,true,List.of());
    }

    private TodoSimulationReadinessView blocked()
    {
        JourneyIssue issue=new JourneyIssue("TODO_FULL_SIMULATION_REQUIRED","BLOCKER",
                "SIMULATION_PUBLISH","simulation.full","完整试运行尚未通过","运行完整试运行");
        return new TodoSimulationReadinessView(42L,101L,"hash-42",
                3,3,List.of(),false,false,List.of(issue));
    }
}

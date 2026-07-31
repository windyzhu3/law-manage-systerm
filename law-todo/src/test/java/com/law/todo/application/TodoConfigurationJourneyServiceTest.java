package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateWorkbenchPage;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.application.view.TodoConfigurationViews.TemplateVersionDetail;
import com.law.todo.application.view.TodoSimulationReadinessView;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationJourneyServiceTest
{
    @Mock TodoConfigurationQueryService query;
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoConfigurationResourceCatalogService resources;
    @Mock TodoTemplateService templates;
    @Mock TodoEventResourceService eventResources;
    @Mock TodoBusinessOutcomeCatalogService outcomes;
    @Mock TodoSimulationReadinessService readiness;
    private TodoConfigurationJourneyService service;
    private final Actor actor=new Actor(7L,"configuration-manager",3L);

    @BeforeEach void setUp()
    {
        service=new TodoConfigurationJourneyService(query,new TodoDefinitionCodec(),mapper,resources,
                new TodoConfigurationJourneyEvaluator(resources,templates),new TodoEmployeeTodoPreviewProjector(),
                templates,eventResources,outcomes,readiness);
        lenient().when(eventResources.list(anyMap())).thenReturn(
                new com.law.todo.application.view.TodoResourceViews.EventResourcePage(List.of(),0));
        lenient().when(readiness.readiness(42L,101L,"hash-42","TODO-42","LEAD"))
                .thenReturn(ready(42L,101L,"hash-42"));
        lenient().when(readiness.readinessBatch(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation->{
                    List<TodoSimulationReadinessService.BatchRequest> requests=invocation.getArgument(0);
                    Map<Long,TodoSimulationReadinessView> result=new java.util.LinkedHashMap<>();
                    requests.forEach(request->result.put(request.versionId(),
                            ready(request.templateId(),request.versionId(),request.definitionHash())));
                    return result;
                });
    }

    @Test void loadsSevenOrderedStepsAndKeepsOptimisticLockVersion()
    {
        when(query.template(42L)).thenReturn(fixtureTemplate());
        when(eventResources.list(anyMap())).thenReturn(new com.law.todo.application.view.TodoResourceViews.EventResourcePage(
                List.of(new com.law.todo.application.view.TodoResourceViews.EventResourceListItem(
                        71L,"LEAD_CREATED","线索已创建","在线索录入完成后触发",1,"LEAD",
                        "线索中心","INCOMPLETE","DRAFT",3,2L,LocalDateTime.of(2026,7,24,9,0))),1));
        when(templates.listTemplateCalendarCatalog()).thenReturn(List.of(
                Map.of("calendarCode","DEFAULT","calendarName","默认工作日历","timezone","Asia/Shanghai")));
        when(mapper.selectPublishedRoutingTargetCatalog("LEAD")).thenReturn(List.of(Map.ofEntries(
                Map.entry("template_id",9L),Map.entry("template_code","TODO-NEXT"),
                Map.entry("template_name","下一步办理"),Map.entry("business_type","LEAD"),
                Map.entry("version_id",91L),Map.entry("version_no",2),Map.entry("status","PUBLISHED"))));

        TodoConfigurationJourneyView view=service.load(42L,actor);

        assertThat(view.template().templateId()).isEqualTo(42L);
        assertThat(view.template().templateCode()).isEqualTo("TODO-42");
        assertThat(view.template().lockVersion()).isEqualTo(4);
        assertThat(Arrays.stream(TodoConfigurationJourneyView.TemplateSummary.class.getRecordComponents())
                .map(component->component.getName())).doesNotContain("definitionJson");
        assertThat(view.steps()).extracting(TodoConfigurationJourneyView.JourneyStep::code)
                .containsExactly("EVENT","TRIGGER","OWNER","DOD","SLA","ROUTING","SIMULATION_PUBLISH");
        assertThat(view.steps()).filteredOn(step->"SIMULATION_PUBLISH".equals(step.code()))
                .extracting(TodoConfigurationJourneyView.JourneyStep::state)
                .containsExactly("COMPLETED");
        assertThat(view.issues()).extracting(TodoConfigurationJourneyView.JourneyIssue::code)
                .doesNotContain("TODO_JOURNEY_SIMULATION_REQUIRED","TODO_FULL_SIMULATION_REQUIRED");
        assertThat(view.steps()).extracting(TodoConfigurationJourneyView.JourneyStep::value).containsExactly(
                Map.of("eventType","LEAD_CREATED","payloadVersion",1),
                Map.of("condition",Map.of("all",List.of(Map.of("field","lead.source","operator","EQ","value","WEB")))),
                Map.of("config",Map.of("type","BUSINESS_OWNER","fallback",Map.of("type","SUPERVISOR"))),
                Map.of("config",Map.of("requiredFields",List.of("contactedAt"),"evidence",Map.of("types",List.of("NOTE","FILE")))),
                Map.of("config",Map.of("calendarCode","DEFAULT","minutes",60,"reminders",List.of(15,30))),
                Map.of("config",Map.of("start","review","nodes",List.of(Map.of("key","review")),"edges",List.of())),
                Map.of("config",Map.of("businessStage","QUALIFY","panels",List.of(Map.of("code","summary")))));
        assertThat(view.permissions().canEdit()).isTrue();
        assertThat(view.resources().events()).extracting(
                com.law.todo.application.view.TodoResourceViews.EventResourceListItem::eventType)
                .containsExactly("LEAD_CREATED");
        var event=view.resources().events().get(0);
        assertThat(event.eventCatalogId()).isEqualTo(71L);
        assertThat(event.eventName()).isEqualTo("线索已创建");
        assertThat(event.description()).isEqualTo("在线索录入完成后触发");
        assertThat(event.sourceModule()).isEqualTo("线索中心");
        assertThat(view.resources().calendars()).extracting(row->row.get("calendarCode")).containsExactly("DEFAULT");
        assertThat(view.resources().routingTargets())
                .extracting(com.law.todo.application.view.TodoConfigurationViews.RoutingTargetCatalogEntry::templateName)
                .containsExactly("下一步办理");
        verify(readiness).readiness(42L,101L,"hash-42","TODO-42","LEAD");
        verify(mapper).selectPublishedRoutingTargetCatalog("LEAD");
    }

    @Test void leadJourneyDoesNotExposeCrossBusinessOrInactiveRoutingTargets()
    {
        when(query.template(42L)).thenReturn(fixtureTemplate());
        when(mapper.selectPublishedRoutingTargetCatalog("LEAD")).thenReturn(List.of(
                routingTarget(4L,"TD-004","5天实质进展","LEAD",104L,"PUBLISHED"),
                routingTarget(8L,"CASE_ACCEPT","律师接案确认","CASE",108L,"PUBLISHED"),
                routingTarget(2L,"TD-002","疑似无效复核","LEAD",102L,"RETIRED")));

        TodoConfigurationJourneyView view=service.load(42L,actor);

        assertThat(view.resources().routingTargets())
                .extracting(com.law.todo.application.view.TodoConfigurationViews.RoutingTargetCatalogEntry::templateCode)
                .containsExactly("TD-004");
    }

    @Test void returnsTruthfulProgressHealthStateAndTemplateCodeWithoutPerRowQueries()
    {
        when(mapper.selectTemplateJourneySummaries(anyMap())).thenReturn(fixtureWorkbenchRows());

        TemplateWorkbenchPage page=service.workbench(Map.of("offset",0,"limit",20),actor);

        assertThat(page.rows()).extracting(TodoConfigurationJourneyView.TemplateWorkbenchItem::templateCode)
                .containsExactly("TODO-BLOCK","TODO-WARN","TODO-READY","TODO-PROGRESS","TODO-PUBLISHED");
        assertThat(page.rows()).extracting(TodoConfigurationJourneyView.TemplateWorkbenchItem::journeyState)
                .containsExactly("BLOCKED","WARNING","READY","IN_PROGRESS","PUBLISHED");
        assertThat(page.rows()).extracting(TodoConfigurationJourneyView.TemplateWorkbenchItem::completedSteps)
                .containsExactly(6,7,7,5,7);
        assertThat(page.rows()).extracting(TodoConfigurationJourneyView.TemplateWorkbenchItem::blockerCount)
                .containsExactly(1,0,0,0,0);
        assertThat(page.rows()).extracting(TodoConfigurationJourneyView.TemplateWorkbenchItem::warningCount)
                .containsExactly(0,2,0,0,0);
        assertThat(page.rows()).extracting(TodoConfigurationJourneyView.TemplateWorkbenchItem::primaryAction)
                .containsExactly("CONTINUE_CONFIGURATION","CONTINUE_CONFIGURATION","CONTINUE_CONFIGURATION",
                        "CONTINUE_CONFIGURATION","VIEW_PUBLISHED");
        assertThat(page.total()).isEqualTo(5L);
        assertThat(page.blockerTemplates()).isEqualTo(1);
        assertThat(page.warningTemplates()).isEqualTo(1);
        assertThat(page.readyTemplates()).isEqualTo(3);
        verify(mapper,times(1)).selectTemplateJourneySummaries(anyMap());
        verify(mapper,never()).countTemplateJourneySummaries(anyMap());
        verifyNoInteractions(query,resources,templates);
    }

    @Test void exposesTypedBusinessOutcomesWithTheCurrentJourneyResources()
    {
        when(query.template(42L)).thenReturn(fixtureTemplate());
        when(outcomes.resolve(org.mockito.ArgumentMatchers.eq("TODO-42"),
                org.mockito.ArgumentMatchers.eq("LEAD"),org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TodoBusinessOutcomeCatalogService.BusinessOutcomeSet(
                        "contactResult","首联结果","首联后的联系结论",
                        List.of(new TodoBusinessOutcomeCatalogService.BusinessOutcomeOption(
                                "VALID","有效","NEXT_TEMPLATE","TD-004","5天实质进展",104L)),
                        "TD001_STANDARD_ROUTE"));

        TodoConfigurationJourneyView view=service.load(42L,actor);

        assertThat(view.resources().businessOutcomeSet().resultFieldName()).isEqualTo("首联结果");
        assertThat(view.resources().businessOutcomeSet().options())
                .extracting(TodoBusinessOutcomeCatalogService.BusinessOutcomeOption::label)
                .containsExactly("有效");
    }

    @Test void persistedWarningDoesNotCompleteAnEvaluatorStepThatHasNotStarted()
    {
        Map<String,Object> row=workbenchRow(47L,"TODO-PERSISTED-WARNING","DRAFT","hash-persisted-warning",
                definition("hash-persisted-warning",false,false,false,true),
                """
                {"errors":[],"warnings":[
                  {"code":"TODO_PERSISTED_DOD_WARNING","path":"dod.config","message":"Review completion evidence"}]}
                """);
        when(mapper.selectTemplateJourneySummaries(anyMap())).thenReturn(List.of(row));

        TemplateWorkbenchPage page=service.workbench(Map.of("offset",0,"limit",20),actor);

        assertThat(page.rows()).singleElement().satisfies(item->{
            assertThat(item.completedSteps()).isEqualTo(5);
            assertThat(item.warningCount()).isEqualTo(1);
            assertThat(item.nextStepCode()).isEqualTo("DOD");
            assertThat(item.nextStepTitle()).isEqualTo("Definition of done");
        });
    }

    @Test void workbenchProjectsAuthoritativeDraftEditorAndEditTime()
    {
        LocalDateTime editedAt=LocalDateTime.of(2026,7,24,10,15);
        Map<String,Object> row=new java.util.LinkedHashMap<>(fixtureWorkbenchRows().get(2));
        row.put("last_editor","draft-editor");
        row.put("update_time",editedAt);
        when(mapper.selectTemplateJourneySummaries(anyMap())).thenReturn(List.of(row));

        TemplateWorkbenchPage page=service.workbench(Map.of("offset",0,"limit",20),actor);

        assertThat(page.rows()).singleElement().satisfies(item->{
            assertThat(item.lastEditor()).isEqualTo("draft-editor");
            assertThat(item.updateTime()).isEqualTo(editedAt);
        });
    }

    @Test void appliesIssueFilterBeforePagingAndKeepsGlobalMutuallyExclusiveSummary()
    {
        when(mapper.selectTemplateJourneySummaries(anyMap())).thenReturn(fixtureWorkbenchRows());

        TemplateWorkbenchPage page=service.workbench(Map.of("keyword","lead","businessType","LEAD",
                "businessStage","QUALIFY","publishStatus","DRAFT","issueType","WARNING","offset",0,"limit",1),actor);

        assertThat(page.rows()).extracting(TodoConfigurationJourneyView.TemplateWorkbenchItem::templateCode)
                .containsExactly("TODO-WARN");
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.blockerTemplates()).isEqualTo(1);
        assertThat(page.warningTemplates()).isEqualTo(1);
        assertThat(page.readyTemplates()).isEqualTo(3);
        ArgumentCaptor<Map<String,Object>> batch=ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectTemplateJourneySummaries(batch.capture());
        assertThat(batch.getValue()).containsEntry("keyword","lead").containsEntry("businessType","LEAD")
                .containsEntry("businessStage","QUALIFY").containsEntry("publishStatus","DRAFT")
                .containsEntry("offset",0).containsEntry("limit",5001);
        assertThat(batch.getValue()).doesNotContainKey("issueType");
        verifyNoInteractions(query,resources,templates);
    }

    @Test void rejectsAnOversizedEvaluationBatchInsteadOfReturningTruncatedTotals()
    {
        Map<String,Object> row=fixtureWorkbenchRows().get(0);
        when(mapper.selectTemplateJourneySummaries(anyMap()))
                .thenReturn(java.util.Collections.nCopies(5001,row));

        assertThatThrownBy(()->service.workbench(Map.of("offset",0,"limit",20),actor))
                .hasMessageContaining("bounded evaluation limit");
        verify(mapper,times(1)).selectTemplateJourneySummaries(anyMap());
        verify(mapper,never()).countTemplateJourneySummaries(anyMap());
        verifyNoInteractions(query,resources,templates);
    }

    private TemplateConfigurationDetail fixtureTemplate()
    {
        return new TemplateConfigurationDetail(42L,"TODO-42","Lead follow-up","LEAD","0",4,4,101L,"DRAFT",91L,3,
                new TemplateVersionDetail(101L,4,"DRAFT",91L,1,definitionJson(),"{}","{}","{}","{}","{}","hash-42",
                        "{}",null,null,null,null,null,LocalDateTime.of(2026,7,23,9,0)),List.of());
    }

    private List<Map<String,Object>> fixtureWorkbenchRows()
    {
        return List.of(
                workbenchRow(42L,"TODO-BLOCK","DRAFT","hash-block",definition("hash-block",true,false,true,true),"{}"),
                workbenchRow(43L,"TODO-WARN","DRAFT","hash-warning",definition("hash-warning",false,true,true,true),
                        """
                        {"errors":[],"warnings":[
                          {"code":"TODO_JOURNEY_TRIGGER_RECOMMENDATION","path":"event.condition","message":"duplicate"},
                          {"code":"TODO_PERSISTED_DOD_WARNING","path":"dod.config","message":"Review completion evidence"}]}
                        """),
                workbenchRow(44L,"TODO-READY","DRAFT","hash-ready",definition("hash-ready",false,false,true,true),"{}"),
                workbenchRow(45L,"TODO-PROGRESS","DRAFT","hash-progress",definition("hash-progress",false,false,false,true),"{}"),
                workbenchRow(46L,"TODO-PUBLISHED","PUBLISHED","hash-published",definition("hash-published",false,false,true,true),"{}"));
    }

    private Map<String,Object> workbenchRow(long id,String code,String publishStatus,String hash,String definition,String validation)
    {
        return Map.ofEntries(Map.entry("template_id",id),Map.entry("template_code",code),
                Map.entry("template_name",code+" lead"),Map.entry("business_type","LEAD"),
                Map.entry("business_stage","QUALIFY"),Map.entry("publish_status",publishStatus),
                Map.entry("template_status","0"),Map.entry("lock_version",4),Map.entry("version_id",id+100),
                Map.entry("version_no",4),Map.entry("definition_hash",hash),Map.entry("definition_json",definition),
                Map.entry("validation_report_json",validation),Map.entry("last_editor","Alice"),
                Map.entry("update_time",LocalDateTime.of(2026,7,23,9,0)));
    }

    private Map<String,Object> routingTarget(long templateId,String code,String name,String businessType,
            long versionId,String status)
    {
        return Map.ofEntries(Map.entry("template_id",templateId),Map.entry("template_code",code),
                Map.entry("template_name",name),Map.entry("business_type",businessType),
                Map.entry("version_id",versionId),Map.entry("version_no",1),Map.entry("status",status));
    }

    private String definition(String hash,boolean blockedOwner,boolean warningTrigger,boolean configuredDod,boolean simulated)
    {
        String condition=warningTrigger?"{}":"""
                {"all":[{"field":"lead.source","operator":"EQ","value":"WEB"}]}
                """;
        String owner=blockedOwner?"{}":"""
                {"type":"BUSINESS_OWNER","fallback":{"type":"SUPERVISOR"}}
                """;
        String dod=configuredDod?"""
                {"requiredFields":["contactedAt"]}
                """:"{}";
        String sla=configuredDod?"""
                {"calendarCode":"DEFAULT","minutes":60}
                """:"{}";
        String simulation=simulated?"""
                ,"simulationSuccessful":true,"simulationDefinitionHash":"%s"
                """.formatted(hash):"";
        return """
                {"schemaVersion":1,"templateCode":"TODO","event":{"eventType":"LEAD_CREATED","payloadVersion":1,"condition":%s},
                "owner":{"config":%s},"dod":{"config":%s},"sla":{"config":%s},
                "ui":{"config":{"businessStage":"QUALIFY"%s}},"routing":{"config":{}},
                "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """.formatted(condition,owner,dod,sla,simulation);
    }

    private String definitionJson()
    {
        return """
                {"schemaVersion":1,"templateCode":"TODO-42","event":{"eventType":"LEAD_CREATED","payloadVersion":1,
                 "condition":{"all":[{"field":"lead.source","operator":"EQ","value":"WEB"}]}},
                "owner":{"config":{"type":"BUSINESS_OWNER","fallback":{"type":"SUPERVISOR"}}},
                "dod":{"config":{"requiredFields":["contactedAt"],"evidence":{"types":["NOTE","FILE"]}}},
                "sla":{"config":{"calendarCode":"DEFAULT","minutes":60,"reminders":[15,30]}},
                "ui":{"config":{"businessStage":"QUALIFY","panels":[{"code":"summary"}]}},
                "routing":{"config":{"start":"review","nodes":[{"key":"review"}],"edges":[]}},
                "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
    }

    private TodoSimulationReadinessView ready(long templateId,long versionId,String hash)
    {
        return new TodoSimulationReadinessView(templateId,versionId,hash,
                3,3,List.of(),true,true,List.of());
    }
}

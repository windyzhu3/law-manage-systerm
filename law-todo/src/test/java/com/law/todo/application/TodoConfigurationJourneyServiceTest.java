package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateWorkbenchPage;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.application.view.TodoConfigurationViews.TemplateVersionDetail;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationJourneyServiceTest
{
    @Mock TodoConfigurationQueryService query;
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoConfigurationResourceCatalogService resources;
    private TodoConfigurationJourneyService service;
    private final Actor actor=new Actor(7L,"configuration-manager",3L);

    @BeforeEach void setUp()
    {
        service=new TodoConfigurationJourneyService(query,new TodoDefinitionCodec(),mapper,resources,
                new TodoConfigurationJourneyEvaluator(),new TodoEmployeeTodoPreviewProjector());
    }

    @Test void loadsSevenOrderedStepsAndKeepsOptimisticLockVersion()
    {
        when(query.template(42L)).thenReturn(fixtureTemplate());

        TodoConfigurationJourneyView view=service.load(42L,actor);

        assertThat(view.template().templateId()).isEqualTo(42L);
        assertThat(view.template().templateCode()).isEqualTo("TODO-42");
        assertThat(view.template().lockVersion()).isEqualTo(4);
        assertThat(Arrays.stream(TodoConfigurationJourneyView.TemplateSummary.class.getRecordComponents())
                .map(component->component.getName())).doesNotContain("definitionJson");
        assertThat(view.steps()).extracting(TodoConfigurationJourneyView.JourneyStep::code)
                .containsExactly("EVENT","TRIGGER","OWNER","DOD","SLA","ROUTING","SIMULATION_PUBLISH");
        assertThat(view.permissions().canEdit()).isTrue();
    }

    @Test void returnsWorkbenchProgressAndIssuesWithoutPerRowQueries()
    {
        when(mapper.selectTemplateJourneySummaries(anyMap())).thenReturn(fixtureWorkbenchRows());
        when(mapper.countTemplateJourneySummaries(anyMap())).thenReturn(2L);

        TemplateWorkbenchPage page=service.workbench(Map.of("offset",0,"limit",20),actor);

        assertThat(page.rows()).extracting(TodoConfigurationJourneyView.TemplateWorkbenchItem::primaryAction)
                .containsExactly("CONTINUE_CONFIGURATION","VIEW_PUBLISHED");
        assertThat(page.total()).isEqualTo(2L);
        verify(mapper,times(1)).selectTemplateJourneySummaries(anyMap());
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
                Map.of("template_id",42L,"template_name","Lead follow-up","business_type","LEAD","business_stage","QUALIFY",
                        "publish_status","DRAFT","definition_json",definitionJson(),"validation_report_json","{}","last_editor","Alice",
                        "update_time",LocalDateTime.of(2026,7,23,9,0)),
                Map.of("template_id",43L,"template_name","Published lead follow-up","business_type","LEAD","business_stage","QUALIFY",
                        "publish_status","PUBLISHED","definition_json",definitionJson(),"validation_report_json","{}","last_editor","Bob",
                        "update_time",LocalDateTime.of(2026,7,22,9,0)));
    }

    private String definitionJson()
    {
        return """
                {"schemaVersion":1,"templateCode":"TODO-42","event":{"eventType":"LEAD_CREATED","payloadVersion":1,"condition":{}},
                "owner":{"config":{}},"dod":{"config":{}},"sla":{"config":{}},"ui":{"config":{"businessStage":"QUALIFY"}},
                "routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
    }
}

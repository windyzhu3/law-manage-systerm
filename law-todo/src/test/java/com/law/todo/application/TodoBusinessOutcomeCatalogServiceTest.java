package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.view.TodoConfigurationViews.RoutingTargetCatalogEntry;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoBusinessOutcomeCatalogServiceTest
{
    @Mock TodoConfigurationResourceCatalogService resources;
    @Mock TodoTemplateService templates;
    @Mock TodoConfigurationMapper mapper;
    private TodoBusinessOutcomeCatalogService service;

    @BeforeEach void setUp()
    {
        service=new TodoBusinessOutcomeCatalogService(resources,templates,mapper);
        when(resources.fields("LEAD")).thenReturn(List.of(new FieldResource(
                "contactResult","首联结果","string",true,null,false,List.of("EQ"),List.of(),List.of(),
                null,0,"GOVERNED","首联后的联系结论","LEAD","ACTIVE",10,List.of(),
                "DICT","SYSTEM_DICTIONARY","law_first_contact_result",null)));
        when(mapper.selectEnabledDictionaryData("law_first_contact_result")).thenReturn(List.of(
                Map.of("dict_label","有效","dict_value","VALID"),
                Map.of("dict_label","疑似无效","dict_value","SUSPECT_INVALID"),
                Map.of("dict_label","无法联系","dict_value","UNREACHABLE")));
        when(templates.listRoutingTargetCatalog()).thenReturn(List.of(
                target(4L,"TD-004","5天实质进展",104L),
                target(2L,"TD-002","疑似无效复核",102L),
                target(3L,"TD-003","无法联系重试",103L),
                target(3L,"TD-003","无法联系重试（历史版本）",84L)));
    }

    @Test void returnsTheTypedFirstContactOutcomeSetAndRecommendedTargets()
    {
        var result=service.resolve("TD-001","LEAD",definition());

        assertThat(result.resultField()).isEqualTo("contactResult");
        assertThat(result.resultFieldName()).isEqualTo("首联结果");
        assertThat(result.recommendationCode()).isEqualTo("TD001_STANDARD_ROUTE");
        assertThat(result.options()).extracting(option->option.value())
                .containsExactly("VALID","SUSPECT_INVALID","UNREACHABLE");
        assertThat(result.options()).extracting(option->option.label())
                .containsExactly("有效","疑似无效","无法联系");
        assertThat(result.options()).extracting(option->option.targetTemplateCode())
                .containsExactly("TD-004","TD-002","TD-003");
        assertThat(result.options()).extracting(option->option.targetVersionId())
                .containsExactly(104L,102L,103L);
    }

    @Test void reportsMissingDuplicateAndStaleFirstContactRoutes()
    {
        TodoDefinitionDocument definition=definition(Map.of(
                "businessOutcomes",List.of(
                        outcome("VALID",104L),
                        outcome("VALID",104L),
                        outcome("SUSPECT_INVALID",999L))));

        var issues=service.validate("TD-001","LEAD",definition);

        assertThat(issues).extracting(TodoBusinessOutcomeCatalogService.OutcomeIssue::code)
                .containsExactly(
                        "TODO_ROUTING_OUTCOME_DUPLICATE",
                        "TODO_ROUTING_TARGET_VERSION_INVALID",
                        "TODO_ROUTING_OUTCOME_INCOMPLETE");
        assertThat(issues).extracting(TodoBusinessOutcomeCatalogService.OutcomeIssue::path)
                .contains("routing.businessOutcomes[1].resultValue",
                        "routing.businessOutcomes[2].targetVersionId",
                        "routing.businessOutcomes");
    }

    private RoutingTargetCatalogEntry target(long templateId,String code,String name,long versionId)
    {return new RoutingTargetCatalogEntry(templateId,code,name,"LEAD",versionId,1,"PUBLISHED");}

    private TodoDefinitionDocument definition()
    {return definition(Map.of());}

    private TodoDefinitionDocument definition(Map<String,Object> routing)
    {
        return new TodoDefinitionDocument(1,"TD-001",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","PAYLOAD","field","ownerId")),
                new DodRule(Map.of("requiredFields",List.of("contactResult","contactedAt"))),
                new SlaRule(Map.of()),new UiSchema(Map.of()),new RoutingGraph(routing),
                List.of(),List.of(),List.of());
    }

    private Map<String,Object> outcome(String value,long targetVersionId)
    {
        return Map.of("resultField","contactResult","resultValue",value,
                "targetVersionId",targetVersionId,"resultType","NEXT");
    }
}

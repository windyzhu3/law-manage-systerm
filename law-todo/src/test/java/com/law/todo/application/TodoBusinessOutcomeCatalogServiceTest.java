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
    @Mock TodoConfigurationMapper mapper;
    private TodoBusinessOutcomeCatalogService service;

    @BeforeEach void setUp()
    {
        service=new TodoBusinessOutcomeCatalogService(resources,mapper);
        org.mockito.Mockito.lenient().when(mapper.selectPublishedRoutingTargetCatalog("LEAD")).thenReturn(List.of(
                target(4L,"TD-004","5天实质进展","LEAD",104L),
                target(2L,"TD-002","疑似无效复核","LEAD",102L),
                target(3L,"TD-003","无法联系重试","LEAD",103L),
                target(1L,"TD-001","首联待办","LEAD",101L),
                target(8L,"CASE_ACCEPT","律师接案确认","CASE",108L)));
    }

    @Test void returnsTheTypedFirstContactOutcomeSetAndRecommendedTargets()
    {
        when(resources.fields("LEAD")).thenReturn(List.of(new FieldResource(
                "contactResult","首联结果","string",true,null,false,List.of("EQ"),List.of(),List.of(),
                null,0,"GOVERNED","首联后的联系结论","LEAD","ACTIVE",10,List.of(),
                "DICT","SYSTEM_DICTIONARY","law_first_contact_result",null)));
        when(mapper.selectEnabledDictionaryData("law_first_contact_result")).thenReturn(List.of(
                Map.of("dict_label","有效","dict_value","VALID"),
                Map.of("dict_label","疑似无效","dict_value","SUSPECT_INVALID"),
                Map.of("dict_label","无法联系","dict_value","UNREACHABLE")));

        var result=service.resolve("TD-001","LEAD",definition("TD-001","contactResult",Map.of()));

        assertThat(result.resultField()).isEqualTo("contactResult");
        assertThat(result.resultFieldName()).isEqualTo("首联结果");
        assertThat(result.recommendationCode()).isEqualTo("TD001_STANDARD_ROUTE");
        assertThat(result.options()).extracting(option->option.value())
                .containsExactly("VALID","SUSPECT_INVALID","UNREACHABLE");
        assertThat(result.options()).extracting(option->option.label())
                .containsExactly("有效","疑似无效","无法联系");
        assertThat(result.options()).extracting(option->option.effectKind())
                .containsOnly("NEXT_TEMPLATE");
        assertThat(result.options()).extracting(option->option.targetTemplateCode())
                .containsExactly("TD-004","TD-002","TD-003");
        assertThat(result.options()).extracting(option->option.targetVersionId())
                .containsExactly(104L,102L,103L);
    }

    @Test void returnsExactGovernedOutcomeEffectsForTheThreeLeadTemplates()
    {
        assertThat(service.resolve("TD-002","LEAD",definition("TD-002","reviewResult",Map.of())).options())
                .extracting(option->List.of(option.value(),option.label(),option.effectKind(),
                        String.valueOf(option.targetTemplateCode())))
                .containsExactly(
                        List.of("TRUE_INVALID","确认无效","END","null"),
                        List.of("MISJUDGED_VALID","误判有效","NEXT_TEMPLATE","TD-001"));
        assertThat(service.resolve("TD-003","LEAD",definition("TD-003","contactResult",Map.of())).options())
                .extracting(option->List.of(option.value(),option.label(),option.effectKind(),
                        String.valueOf(option.targetTemplateCode())))
                .containsExactly(
                        List.of("CONNECTED","联系成功","NEXT_TEMPLATE","TD-004"),
                        List.of("CONTINUE_CURRENT_WINDOW","本窗口继续","RETAIN_CURRENT","null"),
                        List.of("NEXT_WINDOW","进入下一窗口","SCHEDULE_NEXT","null"),
                        List.of("EXHAUSTED","全部重试耗尽","END","null"));
        assertThat(service.resolve("TD-004","LEAD",definition("TD-004","progressType",Map.of())).options())
                .extracting(option->List.of(option.value(),option.label(),option.effectKind(),
                        String.valueOf(option.targetTemplateCode())))
                .containsExactly(List.of("PROGRESS_RECORDED","已记录实质进展","SCHEDULE_SELF","TD-004"));
    }

    @Test void td004SelfScheduleUsesOnlyTheExactEditableCandidateVersion()
    {
        TodoDefinitionDocument exact=definition("TD-004","progressType",Map.of(
                "businessOutcomes",List.of(outcome("result","PROGRESS_RECORDED",
                        null,"SCHEDULE_SELF","TD-004",188L))));

        var catalog=service.resolve("TD-004","LEAD",188L,exact);

        assertThat(catalog.options()).singleElement().satisfies(option->{
            assertThat(option.targetTemplateCode()).isEqualTo("TD-004");
            assertThat(option.targetVersionId()).isEqualTo(188L);
        });
        assertThat(service.validate("TD-004","LEAD",188L,exact)).isEmpty();
    }

    @Test void td004SelfScheduleRejectsMissingStaleWrongAndPublishedSubstituteVersions()
    {
        for(Long configuredVersion:List.of(187L,999L,104L))
        {
            TodoDefinitionDocument configured=definition("TD-004","progressType",Map.of(
                    "businessOutcomes",List.of(outcome("result","PROGRESS_RECORDED",
                            null,"SCHEDULE_SELF","TD-004",configuredVersion))));
            assertThat(service.validate("TD-004","LEAD",188L,configured))
                    .extracting(TodoBusinessOutcomeCatalogService.OutcomeIssue::code)
                    .containsExactly("TODO_ROUTING_TARGET_VERSION_INVALID");
        }
        TodoDefinitionDocument missing=definition("TD-004","progressType",Map.of(
                "businessOutcomes",List.of(outcome("result","PROGRESS_RECORDED",
                        null,"SCHEDULE_SELF","TD-004",null))));
        assertThat(service.validate("TD-004","LEAD",188L,missing))
                .extracting(TodoBusinessOutcomeCatalogService.OutcomeIssue::code)
                .containsExactly("TODO_ROUTING_TARGET_VERSION_INVALID");
    }

    @Test void publishedDefinitionKeepsItsExactPublishedHistoricalTargetAfterANewerVersionExists()
    {
        when(mapper.selectTemplateIdentityByVersionId(79L)).thenReturn(Map.ofEntries(
                Map.entry("template_id",4L),Map.entry("template_code","TD-004"),
                Map.entry("template_name","5天实质进展"),Map.entry("business_type","LEAD"),
                Map.entry("version_id",79L),Map.entry("version_no",2),Map.entry("status","PUBLISHED")));
        TodoDefinitionDocument published=definition("TD-001","contactResult",Map.of(
                "businessOutcomes",List.of(
                        outcome("contactResult","VALID","有效","NEXT_TEMPLATE","TD-004",79L))));

        assertThat(service.validate("TD-001","LEAD",null,published))
                .extracting(TodoBusinessOutcomeCatalogService.OutcomeIssue::code)
                .doesNotContain("TODO_ROUTING_TARGET_VERSION_INVALID");
    }

    @Test void reportsMissingDuplicateAndStaleFirstContactRoutes()
    {
        TodoDefinitionDocument definition=definition("TD-001","contactResult",Map.of(
                "businessOutcomes",List.of(
                        outcome("contactResult","VALID","有效","NEXT_TEMPLATE","TD-004",104L),
                        outcome("contactResult","VALID","有效","NEXT_TEMPLATE","TD-004",104L),
                        outcome("contactResult","SUSPECT_INVALID","疑似无效","NEXT_TEMPLATE","TD-002",999L))));
        when(resources.fields("LEAD")).thenReturn(List.of(new FieldResource(
                "contactResult","首联结果","string",true,null,false,List.of("EQ"),List.of(),
                List.of(Map.of("label","有效","value","VALID"),Map.of("label","疑似无效","value","SUSPECT_INVALID"),
                        Map.of("label","无法联系","value","UNREACHABLE")),null,0,"GOVERNED",
                "首联后的联系结论","LEAD","ACTIVE",10,List.of(),"DICT",null,null,null)));

        var issues=service.validate("TD-001","LEAD",188L,definition);

        assertThat(issues).extracting(TodoBusinessOutcomeCatalogService.OutcomeIssue::code)
                .containsExactly(
                        "TODO_ROUTING_OUTCOME_DUPLICATE",
                        "TODO_ROUTING_TARGET_VERSION_INVALID",
                        "TODO_ROUTING_OUTCOME_INCOMPLETE");
    }

    @Test void acceptsTheBusinessSentenceLabelWhenTheGovernedResultLabelIsExact()
    {
        when(resources.fields("LEAD")).thenReturn(List.of(new FieldResource(
                "contactResult","首联结果","string",true,null,false,List.of("EQ"),List.of(),
                List.of(Map.of("label","有效","value","VALID")),null,0,"GOVERNED",
                "首联后的联系结论","LEAD","ACTIVE",10,List.of(),"DICT",null,null,null)));
        Map<String,Object> configured=new java.util.LinkedHashMap<>(
                outcome("contactResult","VALID","当首联结果为有效时","NEXT_TEMPLATE","TD-004",104L));
        configured.put("resultLabel","有效");

        var issues=service.validate("TD-001","LEAD",188L,definition("TD-001","contactResult",
                Map.of("businessOutcomes",List.of(configured))));

        assertThat(issues).extracting(TodoBusinessOutcomeCatalogService.OutcomeIssue::code)
                .doesNotContain("TODO_ROUTING_OUTCOME_LABEL_INVALID");
    }

    @Test void rejectsArbitraryLabelsAndCrossBusinessTargetsForGovernedOutcomes()
    {
        TodoDefinitionDocument definition=definition("TD-002","reviewResult",Map.of(
                "businessOutcomes",List.of(
                        outcome("reviewResult","TRUE_INVALID","随意文本","END",null,null),
                        outcome("reviewResult","MISJUDGED_VALID","误判有效","NEXT_TEMPLATE","CASE_ACCEPT",108L))));

        var issues=service.validate("TD-002","LEAD",188L,definition);

        assertThat(issues).extracting(TodoBusinessOutcomeCatalogService.OutcomeIssue::code)
                .contains("TODO_ROUTING_OUTCOME_LABEL_INVALID","TODO_ROUTING_TARGET_VERSION_INVALID");
    }

    private Map<String,Object> target(long templateId,String code,String name,String businessType,long versionId)
    {
        return Map.ofEntries(Map.entry("template_id",templateId),Map.entry("template_code",code),
                Map.entry("template_name",name),Map.entry("business_type",businessType),
                Map.entry("version_id",versionId),Map.entry("version_no",1),Map.entry("status","PUBLISHED"));
    }

    private TodoDefinitionDocument definition(String templateCode,String requiredField,Map<String,Object> routing)
    {
        return new TodoDefinitionDocument(1,templateCode,new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("type","PAYLOAD","field","ownerId")),
                new DodRule(Map.of("requiredFields",List.of(requiredField))),
                new SlaRule(Map.of()),new UiSchema(Map.of()),new RoutingGraph(routing),
                List.of(),List.of(),List.of());
    }

    private Map<String,Object> outcome(String field,String value,String label,String effectKind,
            String targetTemplateCode,Long targetVersionId)
    {
        Map<String,Object> result=new java.util.LinkedHashMap<>();
        result.put("resultField",field);result.put("resultValue",value);result.put("label",label);
        result.put("effectKind",effectKind);result.put("targetTemplateCode",targetTemplateCode);
        result.put("targetVersionId",targetVersionId);return result;
    }
}

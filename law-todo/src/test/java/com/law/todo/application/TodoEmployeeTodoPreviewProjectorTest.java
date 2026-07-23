package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.law.todo.application.TodoConfigurationResourceCatalogService.MaterialResource;
import com.law.todo.application.view.TodoConfigurationJourneyView.EmployeeTodoPreview;
import com.law.todo.application.view.TodoConfigurationJourneyView.PreviewField;
import com.law.todo.application.view.TodoConfigurationJourneyView.PreviewMaterial;
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
class TodoEmployeeTodoPreviewProjectorTest
{
    @Mock TodoConfigurationResourceCatalogService resources;
    private TodoEmployeeTodoPreviewProjector projector;

    @BeforeEach void setUp()
    {
        projector=new TodoEmployeeTodoPreviewProjector(resources);
        when(resources.fields("LEAD")).thenReturn(List.of(
                new FieldResource("contactedAt","Contact time","datetime",false,List.of(),List.of("LEAD_ASSIGNED")),
                new FieldResource("contactResult","Follow-up result","string",false,List.of(),List.of("LEAD_ASSIGNED"))));
        when(resources.materials("LEAD")).thenReturn(List.of(
                new MaterialResource("CALL_NOTE","Call note","A business note","LEAD","ACTIVE",1)));
    }

    @Test void previewUsesBusinessLabelsInsteadOfTechnicalCodes()
    {
        EmployeeTodoPreview view=projector.project(detail(),definition());

        assertThat(view.fields()).extracting(PreviewField::label)
                .containsExactly("Contact time","Follow-up result");
        assertThat(view.completionInstructions()).contains("Record the contact time and complete the follow-up result");
        assertThat(view.assigneeSummary()).doesNotContain("USER").doesNotContain("7");
        assertThat(view.dueSummary()).doesNotContain("DEFAULT");
    }

    @Test void replacesJsonAndTechnicalConfiguredDisplayTextWithBusinessSafeFallbacks()
    {
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("displayName","LEAD_ASSIGNED","type","USER","value",7)),
                new DodRule(Map.of()),new SlaRule(Map.of("displayName","{\"calendarCode\":\"DEFAULT\"}","minutes",480)),
                new UiSchema(Map.of("employeeTitle","{\"eventType\":\"LEAD_ASSIGNED\"}")),new RoutingGraph(Map.of()),
                List.of(),List.of(),List.of());

        EmployeeTodoPreview view=projector.project(detail(),definition);

        assertThat(view.title()).isEqualTo("Lead follow-up");
        assertThat(view.assigneeSummary()).isEqualTo("按已配置的负责人规则分配");
        assertThat(view.dueSummary()).isEqualTo("Due according to the configured service-level agreement");
    }

    @Test void previewRecognizesCanonicalMaterialsAndConditionalRequiredFields()
    {
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,"TODO-42",
                new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("displayName","Lead coordinator","type","USER","value",7)),
                new DodRule(Map.of(
                        "requiredFields",List.of("contactedAt"),
                        "materials",List.of(Map.of("type","CALL_NOTE","minCount",2,"label","Call evidence")),
                        "conditionalRequired",List.of(Map.of("field","contactResult",
                                "when",Map.of("field","connected","equals",true))))),
                new SlaRule(Map.of("calendarCode","DEFAULT","minutes",480)),
                new UiSchema(Map.of("employeeTitle","Follow up with the lead")),
                new RoutingGraph(Map.of()),List.of(),List.of(),List.of());

        EmployeeTodoPreview view=projector.project(detail(),definition);

        assertThat(view.fields()).extracting(PreviewField::code)
                .containsExactly("contactedAt","contactResult");
        assertThat(view.fields()).extracting(PreviewField::required)
                .containsExactly(true,false);
        assertThat(view.materials()).extracting(PreviewMaterial::code)
                .containsExactly("CALL_NOTE");
        assertThat(view.materials()).extracting(PreviewMaterial::label)
                .containsExactly("Call evidence");
    }

    private TemplateConfigurationDetail detail()
    {
        return new TemplateConfigurationDetail(42L,"TODO-42","Lead follow-up","LEAD","0",4,4,101L,"DRAFT",91L,3,
                new TemplateVersionDetail(101L,4,"DRAFT",91L,1,"{}","{}","{}","{}","{}","{}","hash-42","{}",
                        null,null,null,null,null,LocalDateTime.of(2026,7,23,9,0)),List.of());
    }

    private TodoDefinitionDocument definition()
    {
        return new TodoDefinitionDocument(1,"TODO-42",new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of("displayName","Lead coordinator","type","USER","value",7)),
                new DodRule(Map.of("requiredFields",List.of("contactedAt","contactResult"),"requiredAttachments",List.of("CALL_NOTE"),
                        "employeeInstructions",List.of("Record the contact time and complete the follow-up result"))),
                new SlaRule(Map.of("displayName","Complete within one business day","calendarCode","DEFAULT","minutes",480)),
                new UiSchema(Map.of("employeeTitle","Follow up with the lead")),new RoutingGraph(Map.of()),List.of(),List.of(),List.of());
    }
}

package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.JourneyPayloadCommand;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyPermissions;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateSummary;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;
import com.law.todo.spi.TodoFieldReferenceDirectory.DisplayReference;

@ExtendWith(MockitoExtension.class)
class TodoJourneyPayloadPreparationServiceTest
{
    @Mock TodoConfigurationJourneyService journeys;
    @Mock TodoBusinessPayloadHydrationService payloads;
    @Mock TodoConfigurationResourceCatalogService resources;
    @Mock TodoFieldDisplayResolutionService displays;

    @Test void returnsOnlyCreationAndCurrentTemplateFieldsWithResolvedLabels()
    {
        String definition="""
                {"schemaVersion":1,"templateCode":"TD-001",
                 "event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{"type":"PAYLOAD","field":"ownerId"}},
                 "dod":{"config":{"requiredFields":["contactResult","contactedAt"]}},
                 "ui":{"config":{"fields":[{"key":"contactResult"},{"key":"contactedAt"}]}},
                 "routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
        when(journeys.load(42L,actor())).thenReturn(journey(definition));
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition);
        List<FieldResource> descriptors=List.of(
                field("schemaVersion",true,"PLAIN_VALUE",null),
                field("assignmentId",true,"PLAIN_VALUE",null),
                field("ownerId",true,"USER_ID","SYSTEM_USER"),
                field("ownerDeptId",true,"DEPT_ID","SYSTEM_DEPARTMENT"),
                field("operatorId",true,"USER_ID","SYSTEM_USER"),
                governed("contactResult",true),governed("contactedAt",false),governed("reviewResult",false));
        when(resources.fields("LEAD","LEAD_ASSIGNED")).thenReturn(descriptors);
        Map<String,Object> values=Map.of("schemaVersion",1,"assignmentId",1001L,"ownerId",11L,
                "ownerDeptId",103L,"operatorId",1L);
        when(payloads.hydrate("LEAD_ASSIGNED",1,"LEAD",-1001L,actor(),Map.of()))
                .thenReturn(new PayloadHydration(values,List.of(
                        source("schemaVersion",1,true),source("assignmentId",1001L,true),
                        source("ownerId",11L,true),source("ownerDeptId",103L,true),source("operatorId",1L,true)),true));
        when(displays.resolve(org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.eq(actor())))
                .thenReturn(Map.of("ownerId",new DisplayReference(11L,"张三",Map.of("deptName","销售一部"),true,false,null),
                        "ownerDeptId",new DisplayReference(103L,"销售一部",Map.of(),true,false,null),
                        "operatorId",new DisplayReference(1L,"管理员",Map.of(),true,false,null)));
        TodoJourneyPayloadPreparationService service=new TodoJourneyPayloadPreparationService(
                journeys,payloads,resources,new TodoTemplateFieldUsageService(),displays);

        var view=service.prepare(command(),actor());

        assertThat(view.eventInput()).extracting(field->field.path())
                .containsExactly("schemaVersion","assignmentId","ownerId","ownerDeptId","operatorId");
        assertThat(view.completionFields()).extracting(field->field.path())
                .containsExactly("contactResult","contactedAt");
        assertThat(view.allDefaultPaths()).doesNotContain("reviewResult","reviewOpinion","attemptStage","attemptCount");
        assertThat(view.eventInput().stream().filter(field->field.path().equals("ownerId")).findFirst().orElseThrow()
                .displayValue()).isEqualTo("张三");
        assertThat(view.creationCoveragePercent()).isEqualTo(100);
        assertThat(view.blockingIssues()).isEmpty();
    }

    private JourneyPayloadCommand command()
    {return new JourneyPayloadCommand(42L,9L,"LEAD_ASSIGNED",1,"LEAD",-1001L,Map.of(),"hash-1");}
    private Actor actor(){return new Actor(1L,"admin",103L);}
    private TodoConfigurationJourneyView journey(String definition)
    {
        return new TodoConfigurationJourneyView(new TemplateSummary(42L,9L,1,0,"TD-001","首联待办","LEAD",
                "LEAD","DRAFT","hash-1"),List.of(),null,null,List.of(),
                new JourneyPermissions(true,true,true,true,true,true));
    }
    private FieldResource field(String code,boolean required,String semantic,String optionSource)
    {
        return new FieldResource(code,code,"integer",required,null,false,List.of(),List.of("LEAD_ASSIGNED"),List.of(),
                null,0,"EVENT_SCHEMA",null,"LEAD","ACTIVE",0,List.of("LEAD_ASSIGNED@1"),semantic,optionSource,null,null);
    }
    private FieldResource governed(String code,boolean required)
    {
        return new FieldResource(code,code,"string",required,null,false,List.of(),List.of(),List.of(),
                null,0,"GOVERNED",null,"LEAD","ACTIVE",0,List.of(),"PLAIN_VALUE",null,null,null);
    }
    private PayloadFieldSource source(String path,Object value,boolean required)
    {return new PayloadFieldSource(path,value,"EVENT_SAMPLE",required,false,null,false);}
}

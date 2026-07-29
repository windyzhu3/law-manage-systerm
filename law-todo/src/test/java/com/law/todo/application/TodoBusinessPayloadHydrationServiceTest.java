package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoBusinessDirectoryAccess;
import com.law.todo.spi.TodoBusinessPayloadAccess;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;

@ExtendWith(MockitoExtension.class)
class TodoBusinessPayloadHydrationServiceTest
{
    @Mock TodoBusinessPayloadAccess access;
    @Mock TodoBusinessDirectoryAccess directory;
    @Mock TodoConfigurationResourceCatalogService resources;
    @Mock TodoSimulationSampleCatalog samples;
    @Mock TodoConfigurationMapper mapper;
    private final Actor actor=new Actor(7L,"operator",3L);
    private TodoBusinessPayloadHydrationService service;

    @BeforeEach void setUp()
    {
        service=new TodoBusinessPayloadHydrationService(List.of(access),List.of(directory),resources,samples);
    }

    @Test void hydratesRequiredFieldsAndMarksMissingValues()
    {
        stubLeadResources();
        when(access.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor)).thenReturn(new PayloadHydration(
                Map.of("leadId",81L,"contact",Map.of("mobile","13800000000")),
                List.of(source("leadId",81L,"BUSINESS_OBJECT"),source("contact.mobile","13800000000","BUSINESS_OBJECT")),false));

        PayloadHydration result=service.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor);

        assertThat(result.payload()).containsEntry("leadId",81L);
        assertThat(result.fields()).extracting(PayloadFieldSource::source)
                .containsExactly("BUSINESS_OBJECT","MISSING","BUSINESS_OBJECT");
        assertThat(result.fields()).filteredOn(PayloadFieldSource::missing)
                .extracting(PayloadFieldSource::path).containsExactly("ownerId");
        assertThat(result.coveragePercent()).isEqualTo(50);
    }

    @Test void sampleObjectUsesEventSampleAndRemainsReadOnly()
    {
        when(resources.fields("LEAD","LEAD_ASSIGNED")).thenReturn(List.of(
                field("leadId",true,false,"LEAD_ASSIGNED"),ownerField()));
        when(samples.contains("LEAD",-1001L)).thenReturn(true);
        when(samples.samplePayload("LEAD_ASSIGNED",1,"LEAD","LEAD",-1001L))
                .thenReturn(Map.of("leadId",-1001L,"ownerId",11L));

        PayloadHydration result=service.hydrate("LEAD_ASSIGNED",1,"LEAD",-1001L,actor);

        assertThat(result.sample()).isTrue();
        assertThat(result.payload()).containsEntry("leadId",-1001L).containsEntry("ownerId",actor.userId());
        assertThat(result.fields()).allMatch(field->!"BUSINESS_OBJECT".equals(field.source()));
        assertThat(result.fields()).filteredOn(field->!field.missing())
                .allMatch(field->"EVENT_SAMPLE".equals(field.source()));
        verify(access,never()).hydrate("LEAD_ASSIGNED",1,"LEAD",-1001L,actor);
    }

    @Test void appliesManualOverridesLastAndRecordsTheirProvenance()
    {
        stubLeadResources();
        when(access.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor)).thenReturn(new PayloadHydration(
                Map.of("leadId",81L,"ownerId",7L),List.of(source("leadId",81L,"BUSINESS_OBJECT"),
                        source("ownerId",7L,"BUSINESS_OBJECT")),false));

        PayloadHydration result=service.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor,
                Map.of("ownerId",9L,"contact.mobile","13900000000"));

        assertThat(result.payload()).containsEntry("ownerId",9L);
        assertThat(result.fields()).filteredOn(field->field.path().equals("ownerId"))
                .extracting(PayloadFieldSource::source).containsExactly("MANUAL_OVERRIDE");
        assertThat(result.fields()).filteredOn(field->field.path().equals("contact.mobile"))
                .extracting(PayloadFieldSource::source).containsExactly("MANUAL_OVERRIDE");
    }

    @Test void redactsSensitiveValuesInPayloadAndFieldResponse()
    {
        stubLeadResources();
        when(access.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor)).thenReturn(new PayloadHydration(
                Map.of("leadId",81L,"ownerId",7L,"contact",Map.of("mobile","13800000000")),
                List.of(source("leadId",81L,"BUSINESS_OBJECT"),source("ownerId",7L,"BUSINESS_OBJECT"),
                        source("contact.mobile","13800000000","BUSINESS_OBJECT")),false));

        PayloadHydration result=service.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor);

        assertThat(((Map<?,?>)result.payload().get("contact")).get("mobile")).isEqualTo("[REDACTED]");
        assertThat(result.fields()).filteredOn(field->field.path().equals("contact.mobile"))
                .extracting(PayloadFieldSource::value).containsExactly("[REDACTED]");
    }

    @Test void keepsSensitiveValuesOnlyInsideTheNonSerializableExecutionView()
    {
        stubLeadResources();
        when(access.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor)).thenReturn(new PayloadHydration(
                Map.of("leadId",81L,"ownerId",7L,"contact",Map.of("mobile","13800000000")),
                List.of(source("leadId",81L,"BUSINESS_OBJECT"),source("ownerId",7L,"BUSINESS_OBJECT"),
                        source("contact.mobile","13800000000","BUSINESS_OBJECT")),false));

        TodoBusinessPayloadHydrationService.ExecutionHydration internal=
                service.hydrateForExecution("LEAD_ASSIGNED",1,"LEAD",81L,actor,Map.of());

        assertThat(((Map<?,?>)internal.payload().get("contact")).get("mobile")).isEqualTo("13800000000");
        assertThat(((Map<?,?>)internal.publicView().payload().get("contact")).get("mobile"))
                .isEqualTo("[REDACTED]");
        assertThat(internal.toString()).doesNotContain("13800000000");
        assertThat(JSON.toJSONString(internal)).doesNotContain("13800000000");
    }

    @Test void resolvesGovernedLogicalTypesToEventAwarePhysicalSources()
    {
        when(access.supports("CASE")).thenReturn(true);
        when(access.supports("MATTER")).thenReturn(true);
        when(resources.fields("NON_LITIGATION","CASE_CLASSIFIED_NON_LITIGATION")).thenReturn(List.of());
        when(resources.fields("NON_LITIGATION","NON_LITIGATION_WORK_COMPLETED")).thenReturn(List.of());
        when(resources.fields("ENFORCEMENT","CASE_CLASSIFIED_ENFORCEMENT")).thenReturn(List.of());
        when(resources.fields("ENFORCEMENT","ENFORCEMENT_ORDER_ACCEPTED")).thenReturn(List.of());
        when(resources.fields("ENFORCEMENT","ENFORCEMENT_SERVICE_NODE_READY")).thenReturn(List.of());
        when(access.hydrate("CASE_CLASSIFIED_NON_LITIGATION",1,"CASE",84L,actor))
                .thenReturn(new PayloadHydration(Map.of("caseId",84L),List.of(),false));
        when(access.hydrate("NON_LITIGATION_WORK_COMPLETED",1,"MATTER",85L,actor))
                .thenReturn(new PayloadHydration(Map.of("matterId",85L),List.of(),false));
        when(access.hydrate("CASE_CLASSIFIED_ENFORCEMENT",1,"CASE",84L,actor))
                .thenReturn(new PayloadHydration(Map.of("caseId",84L),List.of(),false));
        when(access.hydrate("ENFORCEMENT_ORDER_ACCEPTED",1,"MATTER",85L,actor))
                .thenReturn(new PayloadHydration(Map.of("matterId",85L),List.of(),false));
        when(access.hydrate("ENFORCEMENT_SERVICE_NODE_READY",1,"MATTER",85L,actor))
                .thenReturn(new PayloadHydration(Map.of("matterId",85L),List.of(),false));

        service.hydrate("CASE_CLASSIFIED_NON_LITIGATION",1,"NON_LITIGATION",84L,actor);
        service.hydrate("NON_LITIGATION_WORK_COMPLETED",1,"NON_LITIGATION",85L,actor);
        service.hydrate("CASE_CLASSIFIED_ENFORCEMENT",1,"ENFORCEMENT",84L,actor);
        service.hydrate("ENFORCEMENT_ORDER_ACCEPTED",1,"ENFORCEMENT",85L,actor);
        service.hydrate("ENFORCEMENT_SERVICE_NODE_READY",1,"ENFORCEMENT",85L,actor);

        verify(access).hydrate("CASE_CLASSIFIED_NON_LITIGATION",1,"CASE",84L,actor);
        verify(access).hydrate("NON_LITIGATION_WORK_COMPLETED",1,"MATTER",85L,actor);
        verify(access).hydrate("CASE_CLASSIFIED_ENFORCEMENT",1,"CASE",84L,actor);
        verify(access).hydrate("ENFORCEMENT_ORDER_ACCEPTED",1,"MATTER",85L,actor);
        verify(access).hydrate("ENFORCEMENT_SERVICE_NODE_READY",1,"MATTER",85L,actor);
        verify(resources).fields("NON_LITIGATION","CASE_CLASSIFIED_NON_LITIGATION");
        verify(resources).fields("ENFORCEMENT","ENFORCEMENT_SERVICE_NODE_READY");
    }

    @Test void resolvesLogicalSampleAgainstPhysicalIdentityButKeepsLogicalCatalogType()
    {
        when(samples.contains("CASE",-1004L)).thenReturn(true);
        when(samples.samplePayload("CASE_CLASSIFIED_NON_LITIGATION",1,
                "NON_LITIGATION","CASE",-1004L)).thenReturn(Map.of("caseId",-1004L));
        when(resources.fields("NON_LITIGATION","CASE_CLASSIFIED_NON_LITIGATION")).thenReturn(List.of());

        PayloadHydration result=service.hydrate(
                "CASE_CLASSIFIED_NON_LITIGATION",1,"NON_LITIGATION",-1004L,actor);

        assertThat(result.payload()).containsEntry("caseId",-1004L);
        verify(samples).contains("CASE",-1004L);
        verify(resources).fields("NON_LITIGATION","CASE_CLASSIFIED_NON_LITIGATION");
    }

    @Test void explicitNullOverrideClearsTheValueAndMarksItMissing()
    {
        stubLeadResources();
        when(access.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor)).thenReturn(new PayloadHydration(
                Map.of("leadId",81L,"ownerId",7L),List.of(source("leadId",81L,"BUSINESS_OBJECT"),
                        source("ownerId",7L,"BUSINESS_OBJECT")),false));
        Map<String,Object> overrides=new java.util.LinkedHashMap<>();overrides.put("ownerId",null);

        PayloadHydration result=service.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor,overrides);

        assertThat(result.payload()).doesNotContainKey("ownerId");
        assertThat(result.fields()).filteredOn(field->field.path().equals("ownerId"))
                .allMatch(field->field.missing()&&"MISSING".equals(field.source()));
        assertThat(result.coveragePercent()).isEqualTo(50);
    }

    @Test void missingStructuredRejectionReasonCodeRemainsMissingUntilManuallyCompleted()
    {
        when(access.supports("CASE")).thenReturn(true);
        when(resources.fields("CASE","CASE_REJECTED")).thenReturn(List.of(
                field("reasonCode",true,false,"CASE_REJECTED"),
                field("reason",true,false,"CASE_REJECTED"),
                field("lawyerId",true,false,"CASE_REJECTED")));
        when(access.hydrate("CASE_REJECTED",1,"CASE",84L,actor)).thenReturn(new PayloadHydration(
                Map.of("reason","存在利益冲突","lawyerId",21L),
                List.of(source("reason","存在利益冲突","BUSINESS_OBJECT"),
                        source("lawyerId",21L,"BUSINESS_OBJECT")),false));

        PayloadHydration missing=service.hydrate("CASE_REJECTED",1,"CASE",84L,actor);
        PayloadHydration completed=service.hydrate("CASE_REJECTED",1,"CASE",84L,actor,
                Map.of("reasonCode","CONFLICT"));

        assertThat(missing.payload()).doesNotContainKey("reasonCode");
        assertThat(missing.fields()).filteredOn(field->field.path().equals("reasonCode"))
                .allMatch(field->field.missing()&&"MISSING".equals(field.source()));
        assertThat(missing.coveragePercent()).isEqualTo(67);
        assertThat(completed.payload()).containsEntry("reasonCode","CONFLICT");
        assertThat(completed.fields()).filteredOn(field->field.path().equals("reasonCode"))
                .allMatch(field->!field.missing()&&"MANUAL_OVERRIDE".equals(field.source()));
        assertThat(completed.coveragePercent()).isEqualTo(100);
    }

    @Test void rejectsZeroUnknownSampleAndAmbiguousAdapters()
    {
        assertThatThrownBy(()->service.hydrate("LEAD_ASSIGNED",1,"LEAD",0L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_BUSINESS_ID_INVALID");
        assertThatThrownBy(()->service.hydrate("LEAD_ASSIGNED",1,"LEAD",-9999L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND");

        TodoBusinessPayloadAccess duplicate=org.mockito.Mockito.mock(TodoBusinessPayloadAccess.class);
        when(access.supports("LEAD")).thenReturn(true);
        when(duplicate.supports("LEAD")).thenReturn(true);
        TodoBusinessPayloadHydrationService ambiguous=
                new TodoBusinessPayloadHydrationService(List.of(access,duplicate),resources,samples);
        assertThatThrownBy(()->ambiguous.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_PAYLOAD_ADAPTER_AMBIGUOUS");
    }

    @Test void reportsStableDataSourceStatusForAllFiveBusinessFamilies()
    {
        when(access.supports("LEAD")).thenReturn(true);
        when(access.supports("CUSTOMER")).thenReturn(true);
        when(access.supports("CONTRACT")).thenReturn(true);
        when(access.supports("CASE")).thenReturn(true);
        when(access.supports("MATTER")).thenReturn(true);
        when(directory.supports("LEAD")).thenReturn(true);
        when(directory.supports("CUSTOMER")).thenReturn(true);
        when(directory.supports("CONTRACT")).thenReturn(true);
        when(directory.supports("CASE")).thenReturn(true);
        when(directory.supports("MATTER")).thenReturn(true);
        for(String type:List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER"))
            when(samples.hasSample(type)).thenReturn(true);

        assertThat(service.dataSources()).extracting(TodoBusinessPayloadAccess.DataSourceStatus::businessType)
                .containsExactly("LEAD","CUSTOMER","CONTRACT","CASE","MATTER");
        assertThat(service.dataSources()).allMatch(status->status.payloadAvailable()&&status.sampleAvailable()
                &&status.directoryAvailable()&&"READY".equals(status.status()));
    }

    @Test void reportsDirectoryAvailabilityIndependentlyFromPayloadAvailability()
    {
        when(directory.supports("LEAD")).thenReturn(true);
        when(samples.hasSample("LEAD")).thenReturn(true);

        var lead=service.dataSources().stream().filter(status->status.businessType().equals("LEAD")).findFirst().orElseThrow();

        assertThat(lead.directoryAvailable()).isTrue();
        assertThat(lead.payloadAvailable()).isFalse();
        assertThat(lead.sampleAvailable()).isTrue();
    }

    @Test void sampleCatalogUsesGovernedEventSampleAndAddsReadOnlyIdentity()
    {
        when(mapper.selectEventResourceByTypeVersion("LEAD_ASSIGNED",1)).thenReturn(Map.of(
                "business_object_type","LEAD",
                "sample_payload_json","{\"assignmentId\":1001,\"ownerId\":11}"));

        Map<String,Object> payload=new TodoSimulationSampleCatalog(mapper)
                .samplePayload("LEAD_ASSIGNED",1,"LEAD",-1001L);

        assertThat(payload).containsEntry("leadId",-1001L)
                .containsEntry("assignmentId",1001)
                .containsEntry("ownerId",11)
                .containsEntry("businessNo","DEMO-L-001");
    }

    @Test void sampleCatalogRejectsUnsupportedEventTypeAndVersionTuples()
    {
        TodoSimulationSampleCatalog catalog=new TodoSimulationSampleCatalog(mapper);
        when(mapper.selectEventResourceByTypeVersion("UNKNOWN_EVENT",1)).thenReturn(null);
        when(mapper.selectEventResourceByTypeVersion("LEAD_ASSIGNED",2)).thenReturn(null);
        when(mapper.selectEventResourceByTypeVersion("LEAD_ASSIGNED",1)).thenReturn(Map.of(
                "business_object_type","CUSTOMER","sample_payload_json","{}"));

        assertUnsupported(()->catalog.samplePayload("UNKNOWN_EVENT",1,"LEAD","LEAD",-1001L));
        assertUnsupported(()->catalog.samplePayload("LEAD_ASSIGNED",2,"LEAD","LEAD",-1001L));
        assertUnsupported(()->catalog.samplePayload("LEAD_ASSIGNED",1,"LEAD","LEAD",-1001L));
    }

    private void assertUnsupported(org.assertj.core.api.ThrowableAssert.ThrowingCallable call)
    {
        assertThatThrownBy(call).isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED");
    }

    private FieldResource field(String code,boolean required,boolean sensitive,String event)
    {return new FieldResource(code,code,"string",required,null,sensitive,List.of("EQ"),List.of(event),List.of());}
    private FieldResource ownerField()
    {
        return new FieldResource("ownerId","线索负责人","integer",true,null,false,List.of("EQ"),
                List.of("LEAD_ASSIGNED"),List.of(),null,0,"EVENT_SCHEMA","已分配的线索负责人",
                "LEAD","ACTIVE",10,List.of("LEAD_ASSIGNED@1"),"USER_ID","SYSTEM_USER",
                null,null,"ownerId","OWNER",true,List.of("LEAD_ASSIGNED@1"));
    }
    private PayloadFieldSource source(String path,Object value,String source)
    {return new PayloadFieldSource(path,value,source,false,false,null,false);}
    private void stubLeadResources()
    {
        when(access.supports("LEAD")).thenReturn(true);
        when(resources.fields("LEAD","LEAD_ASSIGNED")).thenReturn(List.of(
                field("leadId",true,false,"LEAD_ASSIGNED"),
                field("ownerId",true,false,"LEAD_ASSIGNED"),
                field("contact.mobile",false,true,"LEAD_ASSIGNED")));
    }
}

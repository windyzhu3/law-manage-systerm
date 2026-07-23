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

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoBusinessPayloadAccess;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;

@ExtendWith(MockitoExtension.class)
class TodoBusinessPayloadHydrationServiceTest
{
    @Mock TodoBusinessPayloadAccess access;
    @Mock TodoConfigurationResourceCatalogService resources;
    @Mock TodoSimulationSampleCatalog samples;
    @Mock TodoConfigurationMapper mapper;
    private final Actor actor=new Actor(7L,"operator",3L);
    private TodoBusinessPayloadHydrationService service;

    @BeforeEach void setUp()
    {
        service=new TodoBusinessPayloadHydrationService(List.of(access),resources,samples);
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
                field("leadId",true,false,"LEAD_ASSIGNED"),field("ownerId",true,false,"LEAD_ASSIGNED")));
        when(samples.contains("LEAD",-1001L)).thenReturn(true);
        when(samples.samplePayload("LEAD_ASSIGNED",1,"LEAD",-1001L))
                .thenReturn(Map.of("leadId",-1001L,"ownerId",11L));

        PayloadHydration result=service.hydrate("LEAD_ASSIGNED",1,"LEAD",-1001L,actor);

        assertThat(result.sample()).isTrue();
        assertThat(result.payload()).containsEntry("leadId",-1001L).containsEntry("ownerId",11L);
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
        for(String type:List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER"))
            when(samples.hasSample(type)).thenReturn(true);

        assertThat(service.dataSources()).extracting(TodoBusinessPayloadAccess.DataSourceStatus::businessType)
                .containsExactly("LEAD","CUSTOMER","CONTRACT","CASE","MATTER");
        assertThat(service.dataSources()).allMatch(status->status.payloadAvailable()&&status.sampleAvailable()
                &&"READY".equals(status.status()));
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

    private FieldResource field(String code,boolean required,boolean sensitive,String event)
    {return new FieldResource(code,code,"string",required,null,sensitive,List.of("EQ"),List.of(event),List.of());}
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

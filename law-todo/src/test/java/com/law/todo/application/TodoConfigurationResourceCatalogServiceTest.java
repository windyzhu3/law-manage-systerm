package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoBusinessValidator;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationResourceCatalogServiceTest
{
    @Mock TodoConfigurationMapper mapper;
    private TodoConfigurationResourceCatalogService service;

    @BeforeEach void setUp()
    {
        TodoBusinessValidator registered=new TodoBusinessValidator()
        {
            @Override public void validate(TodoInstance todo,Map<String,Object> payload) { }
            @Override public String catalogCode(){return "LeadValidator";}
            @Override public boolean supports(String businessType){return "LEAD".equals(businessType);}
        };
        service=new TodoConfigurationResourceCatalogService(mapper,List.of(registered));
    }

    @Test void mergesRegisteredValidatorsWithGovernedMetadata()
    {
        when(mapper.selectValidatorMetadata()).thenReturn(List.of(
                validator("LeadValidator","线索校验","[\"LEAD\"]","ACTIVE"),
                validator("RemovedValidator","已下线校验","[\"LEAD\"]","ACTIVE")));

        var rows=service.validators("LEAD");

        assertEquals(2,rows.size());
        assertTrue(rows.stream().filter(row->row.code().equals("LeadValidator")).findFirst().orElseThrow().selectable());
        var removed=rows.stream().filter(row->row.code().equals("RemovedValidator")).findFirst().orElseThrow();
        assertEquals("UNAVAILABLE",removed.effectiveStatus());
        assertFalse(removed.selectable());
    }

    @Test void filtersValidatorsByBusinessType()
    {
        when(mapper.selectValidatorMetadata()).thenReturn(List.of(validator("LeadValidator","线索校验","[\"LEAD\"]","ACTIVE")));

        assertTrue(service.validators("CASE").isEmpty());
    }

    @Test void flattensTypedEventFieldsAndExposesOnlyValidOperators()
    {
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(Map.of(
                "event_type","LEAD_ASSIGNED",
                "payload_schema_json","""
                {"type":"object","properties":{"ownerId":{"type":"integer","title":"负责人"},
                 "source":{"type":"string","title":"来源"}},"required":["ownerId"]}
                """)));

        var fields=service.fields("LEAD");

        assertEquals(2,fields.size());
        var owner=fields.stream().filter(field->field.code().equals("ownerId")).findFirst().orElseThrow();
        assertTrue(owner.required());
        assertTrue(owner.operators().containsAll(List.of("EQ","GT","LTE","IN")));
    }

    @Test void returnsBusinessMaterialsAndRecipesAsTypedResources()
    {
        when(mapper.selectConfigurationResourceItems("MATERIAL","MATTER")).thenReturn(List.of(resource("MATERIAL","ARCHIVE_FORM","归档表","{}")));
        when(mapper.selectConfigurationResourceItems("DOD_RECIPE","MATTER")).thenReturn(List.of(resource("DOD_RECIPE","ARCHIVE_READY","归档齐备",
                "{\"requiredFields\":[\"archiveNo\"],\"requiredAttachments\":[\"ARCHIVE_FORM\"],\"validatorRefs\":[]}")));

        assertEquals("ARCHIVE_FORM",service.materials("MATTER").get(0).code());
        assertEquals(List.of("archiveNo"),service.recipes("MATTER").get(0).requiredFields());
    }

    private Map<String,Object> validator(String code,String name,String types,String status)
    {return Map.of("validator_code",code,"validator_name",name,"description",name,"business_types_json",types,
            "parameter_schema_json","{\"type\":\"object\",\"properties\":{}}","example_parameters_json","{}","status",status,
            "reference_count",0L);}

    private Map<String,Object> resource(String type,String code,String name,String value)
    {return Map.of("resource_type",type,"resource_code",code,"resource_name",name,"description",name,
            "business_type","MATTER","value_json",value,"status","ACTIVE","sort_order",1);}
}

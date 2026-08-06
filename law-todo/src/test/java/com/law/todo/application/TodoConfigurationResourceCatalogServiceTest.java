package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

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

    @Test void doesNotExposeValidatorJsonOrImplementationDetailsToBusinessEditors()
    {
        assertThat(Arrays.stream(TodoConfigurationResourceCatalogService.ValidatorResource.class.getRecordComponents())
                .map(component->component.getName()))
                .doesNotContain("parameterSchemaJson","exampleParametersJson","implementation");
    }

    @Test void flattensTypedEventFieldsAndExposesOnlyValidOperators()
    {
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(Map.of(
                "event_type","LEAD_ASSIGNED",
                "payload_version",2,
                "payload_schema_json","""
                {"type":"object","properties":{"ownerId":{"type":"integer","title":"负责人"},
                 "source":{"type":"string","title":"来源"}},"required":["ownerId"]}
                """)));

        var fields=service.fields("LEAD");

        assertEquals(2,fields.size());
        var owner=fields.stream().filter(field->field.code().equals("ownerId")).findFirst().orElseThrow();
        assertTrue(owner.required());
        assertTrue(owner.operators().containsAll(List.of("EQ","GT","LTE","IN")));
        assertEquals(List.of("LEAD_ASSIGNED@2"),owner.sourceEventVersions());
    }

    @Test void mergesGovernedFieldsWithEventSchemasWithoutLeakingSensitiveExamples()
    {
        when(mapper.selectConfigurationResourceItems("FIELD","LEAD")).thenReturn(List.of(
                resource("FIELD","mobile","联系电话","{\"type\":\"string\",\"required\":true}"),
                resource("FIELD","level","Customer level","{\"type\":\"string\"}")));
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(
                Map.of("event_type","LEAD_CREATED","payload_schema_json","""
                {"type":"object","properties":{"mobile":{"type":"string","title":"手机号","example":"13800000000","x-sensitive":true},
                "level":{"type":"string","title":"客户等级","enum":["A","B"]}}}
                """),
                Map.of("event_type","LEAD_UPDATED","payload_schema_json","""
                {"type":"object","properties":{"mobile":{"type":"string","title":"手机号","x-sensitive":true}}}
                """)));

        var fields=service.fields("LEAD");
        var mobile=fields.stream().filter(field->field.code().equals("mobile")).findFirst().orElseThrow();
        var level=fields.stream().filter(field->field.code().equals("level")).findFirst().orElseThrow();

        assertEquals("联系电话",mobile.name());
        assertTrue(mobile.required());
        assertTrue(mobile.sensitive());
        assertEquals(null,mobile.example());
        assertEquals(List.of("LEAD_CREATED","LEAD_UPDATED"),mobile.sourceEvents());
        assertEquals("客户等级",level.name());
        assertThat(level.options()).containsExactly("A","B");
        assertTrue(level.operators().contains("CONTAINS"));
    }

    @Test void ignoresBlankEventSchemasSoJourneyCanReportTheSchemaBlocker()
    {
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(Map.of(
                "event_type","LEAD_CREATED","payload_schema_json"," ")));

        assertTrue(service.fields("LEAD").isEmpty());
    }

    @Test void eventScopedFieldsDoNotInheritRequiredFlagsFromOtherEvents()
    {
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(
                Map.of("event_type","LEAD_CREATED","payload_schema_json","""
                {"type":"object","required":["ownerId"],"properties":{"ownerId":{"type":"integer","title":"负责人"}}}
                """),
                Map.of("event_type","LEAD_ASSIGNED","payload_schema_json","""
                {"type":"object","properties":{"ownerId":{"type":"integer","title":"负责人"}}}
                """)));

        var fields=service.fields("LEAD","LEAD_ASSIGNED");

        assertEquals(1,fields.size());
        assertFalse(fields.get(0).required());
        assertEquals(List.of("LEAD_ASSIGNED"),fields.get(0).sourceEvents());
    }

    @Test void placeholderFromAnotherEventDoesNotOverwriteTheSelectedBusinessLabel()
    {
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(
                Map.of("event_type","LEAD_ASSIGNED","payload_version",1,"owner_field_paths_json","[\"ownerId\"]",
                        "payload_schema_json","""
                        {"type":"object","properties":{
                          "ownerId":{"type":"integer","title":"线索负责人","x-semantic-type":"USER_ID"}
                        }}
                        """),
                Map.of("event_type","LEAD_FIRST_CONTACT_VALID","payload_version",1,"owner_field_paths_json","[]",
                        "payload_schema_json","""
                        {"type":"object","properties":{
                          "ownerId":{"type":"integer","x-semantic-type":"USER_ID"}
                        }}
                        """)));

        var owner=service.fields("LEAD").stream()
                .filter(field->field.code().equals("ownerId")).findFirst().orElseThrow();

        assertEquals("线索负责人",owner.name());
    }

    @Test void eventOwnerWhitelistExcludesTheOperatorFromOwnerSelection()
    {
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(
                Map.of("event_type","LEAD_ASSIGNED","payload_version",1,
                        "owner_field_paths_json","[\"ownerId\"]",
                        "payload_schema_json","""
                        {"type":"object","properties":{
                          "ownerId":{"type":"integer","title":"线索负责人",
                            "x-semantic-type":"USER_ID","x-option-source":"SYSTEM_USER"},
                          "operatorId":{"type":"integer","title":"操作人",
                            "x-semantic-type":"USER_ID","x-option-source":"SYSTEM_USER"}
                        }}
                        """)));

        var fields=service.fields("LEAD","LEAD_ASSIGNED");
        var owner=fields.stream().filter(field->field.code().equals("ownerId")).findFirst().orElseThrow();
        var operator=fields.stream().filter(field->field.code().equals("operatorId")).findFirst().orElseThrow();

        assertEquals("LEAD_ASSIGNED@1:ownerId",owner.fieldKey());
        assertEquals("OWNER",owner.eventRole());
        assertTrue(owner.ownerEligible());
        assertEquals(List.of("LEAD_ASSIGNED@1"),owner.ownerSourceEventVersions());
        assertEquals("OPERATOR",operator.eventRole());
        assertFalse(operator.ownerEligible());
        assertTrue(operator.ownerSourceEventVersions().isEmpty());
    }

    @Test void exposesPurposeSpecificAllowlistsWithoutMakingTechnicalIdsConfigurable()
    {
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(Map.of(
                "event_type","LEAD_SUSPECT_INVALID_MARKED","payload_version",1,
                "owner_field_paths_json","[\"ownerId\"]",
                "condition_field_paths_json","[\"reasonCode\"]",
                "default_value_field_paths_json","[\"reasonCode\"]",
                "payload_schema_json","""
                {"type":"object","properties":{
                  "schemaVersion":{"type":"integer","title":"载荷版本","x-semantic-type":"SYSTEM_VERSION"},
                  "leadId":{"type":"integer","title":"线索","x-semantic-type":"BUSINESS_ID"},
                  "ownerId":{"type":"integer","title":"线索负责人","x-semantic-type":"USER_ID"},
                  "reasonCode":{"type":"string","title":"疑似无效原因","x-semantic-type":"DICT"},
                  "reviewId":{"type":"integer","title":"复核记录","x-semantic-type":"SYSTEM_ID"}
                }}
                """)));

        var fields=service.fields("LEAD","LEAD_SUSPECT_INVALID_MARKED");
        var owner=fields.stream().filter(field->field.code().equals("ownerId")).findFirst().orElseThrow();
        var reason=fields.stream().filter(field->field.code().equals("reasonCode")).findFirst().orElseThrow();
        var version=fields.stream().filter(field->field.code().equals("schemaVersion")).findFirst().orElseThrow();
        var review=fields.stream().filter(field->field.code().equals("reviewId")).findFirst().orElseThrow();

        assertTrue(owner.ownerEligible());
        assertFalse(owner.conditionEligible());
        assertTrue(reason.conditionEligible());
        assertEquals(List.of("LEAD_SUSPECT_INVALID_MARKED@1"),reason.conditionSourceEventVersions());
        assertTrue(reason.defaultValueEligible());
        assertFalse(version.businessConfigurable());
        assertFalse(review.businessConfigurable());
    }

    @Test void exposesSemanticMetadataFromGovernedFieldsAndEventSchemas()
    {
        when(mapper.selectConfigurationResourceItems("FIELD","LEAD")).thenReturn(List.of(
                resource("FIELD","contactResult","首联结果",
                        "{\"type\":\"string\",\"semanticType\":\"DICT\",\"dictType\":\"law_lead_contact_result\"}")));
        when(mapper.selectActiveEventResourceSchemas("LEAD")).thenReturn(List.of(Map.of(
                "event_type","LEAD_ASSIGNED","payload_version",1,"payload_schema_json","""
                {"type":"object","properties":{
                  "ownerId":{"type":"integer","title":"负责人","x-semantic-type":"USER_ID","x-option-source":"SYSTEM_USER"},
                  "contactResult":{"type":"string","title":"首联结果","x-semantic-type":"DICT",
                    "x-dict-type":"law_lead_contact_result"}}}
                """)));

        var fields=service.fields("LEAD","LEAD_ASSIGNED");
        var owner=fields.stream().filter(field->field.code().equals("ownerId")).findFirst().orElseThrow();
        var result=fields.stream().filter(field->field.code().equals("contactResult")).findFirst().orElseThrow();

        assertEquals("USER_ID",owner.semanticType());
        assertEquals("SYSTEM_USER",owner.optionSource());
        assertEquals("DICT",result.semanticType());
        assertEquals("law_lead_contact_result",result.dictType());
    }

    @Test void returnsBusinessMaterialsAndRecipesAsTypedResources()
    {
        when(mapper.selectConfigurationResourceItems("MATERIAL","MATTER")).thenReturn(List.of(resource("MATERIAL","ARCHIVE_FORM","归档表","{}")));
        when(mapper.selectConfigurationResourceItems("DOD_RECIPE","MATTER")).thenReturn(List.of(resource("DOD_RECIPE","ARCHIVE_READY","归档齐备",
                "{\"requiredFields\":[\"archiveNo\"],\"requiredAttachments\":[\"ARCHIVE_FORM\"],\"validatorRefs\":[]}")));

        assertEquals("ARCHIVE_FORM",service.materials("MATTER").get(0).code());
        assertEquals(List.of("archiveNo"),service.recipes("MATTER").get(0).requiredFields());
    }

    @Test void exposesGovernedResourceIdentityForSafeEditing()
    {
        Map<String,Object> field=new java.util.HashMap<>(resource("FIELD","ownerId","Owner","{\"type\":\"integer\"}"));
        field.put("resource_item_id",70L);field.put("version",2);field.put("description","Owner identity");
        field.put("sort_order",8);field.put("business_type","LEAD");
        when(mapper.selectConfigurationResourceItems("MATERIAL","MATTER")).thenReturn(List.of(Map.of(
                "resource_item_id",71L,"version",3,"resource_type","MATERIAL","resource_code","ARCHIVE_FORM",
                "resource_name","Archive form","description","Archive form","business_type","MATTER",
                "value_json","{}","status","ACTIVE","sort_order",1)));
        when(mapper.selectConfigurationResourceItems("FIELD","LEAD")).thenReturn(List.of(field));
        when(mapper.selectConfigurationResourceItems("DOD_RECIPE","MATTER")).thenReturn(List.of(Map.ofEntries(
                Map.entry("resource_item_id",72L),Map.entry("version",4),Map.entry("resource_type","DOD_RECIPE"),
                Map.entry("resource_code","ARCHIVE_READY"),Map.entry("resource_name","Archive ready"),
                Map.entry("description","Archive ready"),Map.entry("business_type","MATTER"),
                Map.entry("value_json","{}"),Map.entry("status","ACTIVE"),Map.entry("sort_order",9))));

        var material=service.materials("MATTER").get(0);
        var governedField=service.fields("LEAD").get(0);
        var recipe=service.recipes("MATTER").get(0);

        assertEquals(71L,material.resourceItemId());
        assertEquals(3,material.version());
        assertEquals("GOVERNED",material.source());
        assertEquals("Owner identity",governedField.description());
        assertEquals("LEAD",governedField.businessType());
        assertEquals("ACTIVE",governedField.status());
        assertEquals(8,governedField.sortOrder());
        assertEquals(9,recipe.sortOrder());
        assertEquals("ACTIVE",recipe.status());
    }

    private Map<String,Object> validator(String code,String name,String types,String status)
    {return Map.of("validator_code",code,"validator_name",name,"description",name,"business_types_json",types,
            "parameter_schema_json","{\"type\":\"object\",\"properties\":{}}","example_parameters_json","{}","status",status,
            "reference_count",0L);}

    private Map<String,Object> resource(String type,String code,String name,String value)
    {return Map.of("resource_type",type,"resource_code",code,"resource_name",name,"description",name,
            "business_type","MATTER","value_json",value,"status","ACTIVE","sort_order",1);}
}

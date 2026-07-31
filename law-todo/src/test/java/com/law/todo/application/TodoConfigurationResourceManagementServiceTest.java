package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationResourceCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationResourceManagementServiceTest
{
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoMapper todoMapper;
    @Mock TodoConfigurationResourceCatalogService catalog;
    private TodoConfigurationResourceManagementService resources;
    private final Actor actor=new Actor(7L,"alice",2L);

    @BeforeEach
    void setUp()
    {
        resources=new TodoConfigurationResourceManagementService(mapper,todoMapper,catalog);
    }

    @Test
    void rejectsRecipeThatReferencesUnknownBusinessField()
    {
        ConfigurationResourceCommand command=recipe(null,
                "{\"businessActions\":[\"FIRST_CONTACT\"],\"templateStages\":[\"LEAD_FOLLOWUP\"],"
                +"\"recommendationPriority\":100,\"requiredFields\":[\"unknownField\"],\"requiredAttachments\":[],"
                +"\"validatorRefs\":[],\"conditionalRules\":[],\"employeeInstructions\":[\"记录联系结果\"]}",0);

        assertThatThrownBy(()->resources.save(command,actor))
                .isInstanceOf(TodoException.class)
                .hasMessageContaining("TODO_CONFIGURATION_RESOURCE_REFERENCE_UNKNOWN");
        verify(mapper,never()).insertConfigurationResourceItem(anyMap());
    }

    @Test
    void rejectsResourceJsonThatDoesNotMatchItsType()
    {
        ConfigurationResourceCommand command=field(null,"{}",0);

        assertThatThrownBy(()->resources.save(command,actor))
                .isInstanceOf(TodoException.class)
                .hasMessageContaining("TODO_CONFIGURATION_RESOURCE_VALUE_INVALID");
        verify(todoMapper,never()).insertDefinitionActionClaim(anyMap());
    }

    @Test
    void insertsGovernedFieldAndCompletesItsActionClaim()
    {
        claim("resource-create",true);
        when(mapper.insertConfigurationResourceItem(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("resourceItemId",41L);
            return 1;
        });

        assertEquals(41L,resources.save(field(null,"{\"type\":\"string\"}",0),actor));

        verify(mapper).insertConfigurationResourceItem(argThat(row->
                "FIELD".equals(row.get("resourceType"))
                &&"contactedAt".equals(row.get("resourceCode"))
                &&"alice".equals(row.get("createBy"))));
        verify(todoMapper).completeDefinitionAction(
                org.mockito.ArgumentMatchers.eq("resource-create"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(41L));
    }

    @Test
    void staleResourceUpdateReturnsStableConflict()
    {
        claim("resource-update",true);
        when(mapper.selectConfigurationResourceItem(41L)).thenReturn(existing(41L,"FIELD","contactedAt","LEAD",3));
        when(mapper.updateConfigurationResourceItemConditionally(anyMap())).thenReturn(0);

        TodoException error=org.junit.jupiter.api.Assertions.assertThrows(TodoException.class,
                ()->resources.save(field(41L,"{\"type\":\"string\"}",3),actor));

        assertEquals("TODO_CONFIGURATION_RESOURCE_VERSION_CONFLICT",error.getBusinessCode());
    }

    @Test
    void repeatsSuccessfulActionWithoutWritingTheResourceAgain()
    {
        replay("resource-create",41L);

        assertEquals(41L,resources.save(field(null,"{\"type\":\"string\"}",0),actor));

        verify(mapper,never()).insertConfigurationResourceItem(anyMap());
    }

    @Test
    void rejectsRecipeThatReferencesUnknownMaterial()
    {
        when(catalog.isKnownField("contactedAt","LEAD")).thenReturn(true);
        ConfigurationResourceCommand command=recipe(null,recipeValue("100",
                "[\"contactedAt\"]","[\"UNKNOWN_MATERIAL\"]","[]"),0);

        assertThatThrownBy(()->resources.save(command,actor))
                .isInstanceOf(TodoException.class)
                .hasMessageContaining("TODO_CONFIGURATION_RESOURCE_REFERENCE_UNKNOWN");
    }

    @Test
    void rejectsRecipeThatReferencesUnavailableValidator()
    {
        when(catalog.isKnownField("contactedAt","LEAD")).thenReturn(true);
        ConfigurationResourceCommand command=recipe(null,recipeValue("100",
                "[\"contactedAt\"]","[]","[\"UnknownValidator\"]"),0);

        assertThatThrownBy(()->resources.save(command,actor))
                .isInstanceOf(TodoException.class)
                .hasMessageContaining("TODO_CONFIGURATION_RESOURCE_REFERENCE_UNKNOWN");
    }

    @Test
    void rejectsNonNumericAndNonIntegralRecommendationPriorities()
    {
        for(String priority:java.util.List.of("\"high\"","12.5"))
        {
            ConfigurationResourceCommand command=recipe(null,recipeValue(priority,"[]","[]","[]"),0);

            assertThatThrownBy(()->resources.save(command,actor))
                    .isInstanceOf(TodoException.class)
                    .hasMessageContaining("TODO_CONFIGURATION_RESOURCE_VALUE_INVALID");
        }
    }

    @Test
    void acceptsACompleteGovernedSimulationScenario()
    {
        claim("resource-scenario",true);
        when(mapper.insertConfigurationResourceItem(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("resourceItemId",51L);
            return 1;
        });
        when(catalog.isKnownField("contactResult","LEAD")).thenReturn(true);
        when(catalog.isKnownField("contactedAt","LEAD")).thenReturn(true);
        when(mapper.selectTemplateIdByCode("TD-001")).thenReturn(1L);
        when(mapper.selectTemplateIdByCode("TD-004")).thenReturn(4L);
        String value="{\"templateCode\":\"TD-001\",\"scenarioVersion\":1,"
                +"\"completionPayload\":{\"contactResult\":\"VALID\",\"contactedAt\":\"${SIMULATION_NOW}\"},"
                +"\"editableFields\":[\"contactResult\",\"contactedAt\"],\"requiredMaterials\":[],"
                +"\"completionNodeKey\":\"TD-001\",\"occurrence\":1,\"expectedNextTemplateCode\":\"TD-004\","
                +"\"requiredForPublish\":true}";
        ConfigurationResourceCommand command=new ConfigurationResourceCommand(null,"SIMULATION_SCENARIO",
                "TD001_VALID","有效首联","验证有效首联路由","LEAD",value,"ACTIVE",10,
                "resource-scenario",0);

        assertEquals(51L,resources.save(command,actor));
    }

    @Test
    void acceptsTerminalScenarioWithoutALegacyNextTemplate()
    {
        claim("resource-terminal-scenario",true);
        when(mapper.insertConfigurationResourceItem(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("resourceItemId",52L);
            return 1;
        });
        when(mapper.selectTemplateIdByCode("TD-002")).thenReturn(2L);
        String value="{\"templateCode\":\"TD-002\",\"scenarioVersion\":1,"
                +"\"completionPayload\":{\"reviewResult\":\"TRUE_INVALID\"},"
                +"\"editableFields\":[],\"requiredMaterials\":[],\"completionNodeKey\":\"TD-002\","
                +"\"occurrence\":1,\"expectedEffect\":{\"kind\":\"END\"},"
                +"\"requiredForPublish\":true}";
        ConfigurationResourceCommand command=new ConfigurationResourceCommand(null,"SIMULATION_SCENARIO",
                "TD002_TRUE_INVALID","确认无效","验证终止效果","LEAD",value,"ACTIVE",10,
                "resource-terminal-scenario",0);

        assertEquals(52L,resources.save(command,actor));
    }

    @Test
    void requiresExpectedErrorCodeOnlyForExpectedValidationFailure()
    {
        String value="{\"templateCode\":\"TD-004\",\"scenarioVersion\":1,"
                +"\"completionPayload\":{},\"editableFields\":[],\"requiredMaterials\":[],"
                +"\"completionNodeKey\":\"TD-004\",\"occurrence\":1,"
                +"\"expectedEffect\":{\"kind\":\"EXPECTED_VALIDATION_FAILURE\"},"
                +"\"requiredForPublish\":true}";
        ConfigurationResourceCommand command=new ConfigurationResourceCommand(null,"SIMULATION_SCENARIO",
                "TD004_PROOF_REQUIRED","凭证必填","验证缺少凭证","LEAD",value,"ACTIVE",10,
                "resource-validation-scenario",0);

        assertThatThrownBy(()->resources.save(command,actor))
                .isInstanceOf(TodoException.class)
                .hasMessageContaining("TODO_SIMULATION_EXPECTED_ERROR_CODE_REQUIRED");
    }

    private ConfigurationResourceCommand field(Long id,String valueJson,int version)
    {
        return new ConfigurationResourceCommand(id,"FIELD","contactedAt","联系时间","实际联系时间","LEAD",
                valueJson,"ACTIVE",10,id==null?"resource-create":"resource-update",version);
    }

    private ConfigurationResourceCommand recipe(Long id,String valueJson,int version)
    {
        return new ConfigurationResourceCommand(id,"DOD_RECIPE","LEAD_FIRST_CONTACT","首联完成","记录首联结果","LEAD",
                valueJson,"ACTIVE",10,"resource-recipe",version);
    }

    private String recipeValue(String priority,String fields,String materials,String validators)
    {
        return "{\"businessActions\":[\"FIRST_CONTACT\"],\"templateStages\":[\"LEAD_FOLLOWUP\"],"
                +"\"recommendationPriority\":"+priority+",\"requiredFields\":"+fields
                +",\"requiredAttachments\":"+materials+",\"validatorRefs\":"+validators
                +",\"conditionalRules\":[],\"employeeInstructions\":[\"记录联系结果\"]}";
    }

    private Map<String,Object> existing(long id,String type,String code,String businessType,int version)
    {
        Map<String,Object> row=new HashMap<>();
        row.put("resource_item_id",id);row.put("resource_type",type);row.put("resource_code",code);
        row.put("business_type",businessType);row.put("version",version);return row;
    }

    @SuppressWarnings("unchecked")
    private void claim(String actionId,boolean inserted)
    {
        when(todoMapper.insertDefinitionActionClaim(anyMap())).thenReturn(inserted?1:0);
        when(todoMapper.selectDefinitionActionForUpdate(actionId)).thenAnswer(invocation->{
            Map<String,Object> claim=org.mockito.Mockito.mockingDetails(todoMapper).getInvocations().stream()
                    .filter(call->call.getMethod().getName().equals("insertDefinitionActionClaim")).reduce((first,last)->last)
                    .map(call->(Map<String,Object>)call.getArgument(0)).orElse(Map.of());
            Map<String,Object> row=new HashMap<>();
            row.put("action_type","SAVE_CONFIGURATION_RESOURCE");row.put("entity_type","CONFIGURATION_RESOURCE");
            row.put("operator_id",7L);row.put("operator_name","alice");row.put("operator_dept_id",2L);
            row.put("source_entity_id",claim.get("sourceEntityId"));
            row.put("request_fingerprint",claim.get("requestFingerprint"));row.put("action_status","CLAIMED");
            return row;
        });
        lenient().when(todoMapper.completeDefinitionAction(
                org.mockito.ArgumentMatchers.eq(actionId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
    }

    @SuppressWarnings("unchecked")
    private void replay(String actionId,long resourceItemId)
    {
        when(todoMapper.insertDefinitionActionClaim(anyMap())).thenReturn(0);
        when(todoMapper.selectDefinitionActionForUpdate(actionId)).thenAnswer(invocation->{
            Map<String,Object> claim=org.mockito.Mockito.mockingDetails(todoMapper).getInvocations().stream()
                    .filter(call->call.getMethod().getName().equals("insertDefinitionActionClaim")).reduce((first,last)->last)
                    .map(call->(Map<String,Object>)call.getArgument(0)).orElse(Map.of());
            Map<String,Object> row=new HashMap<>();
            row.put("action_type","SAVE_CONFIGURATION_RESOURCE");row.put("entity_type","CONFIGURATION_RESOURCE");
            row.put("operator_id",7L);row.put("operator_name","alice");row.put("operator_dept_id",2L);
            row.put("source_entity_id",claim.get("sourceEntityId"));row.put("request_fingerprint",claim.get("requestFingerprint"));
            row.put("entity_id",resourceItemId);row.put("action_status","APPLIED");return row;
        });
    }
}

package com.ruoyi.web.controller.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.aMapWithSize;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.ArgumentCaptor;

import com.law.todo.application.TodoConfigurationQueryService;
import com.law.todo.application.TodoConfigurationSimulationService;
import com.law.todo.application.TodoDefinitionDiffService;
import com.law.todo.application.TodoDefinitionService;
import com.law.todo.application.TodoDodRuleManagementService;
import com.law.todo.application.TodoSlaRuleManagementService;
import com.law.todo.application.TodoTemplateService;
import com.law.todo.application.view.TriggerTemplateVersionCatalogView;
import com.law.todo.application.command.TodoConfigurationCommands.SlaRuleCommand;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateMetadataCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

class TodoConfigurationControllerValidationTest
{
    @Test
    void rejectsSlaWithUnorderedThresholdsBeforeServiceMutation() throws Exception
    {
        MockMvcBuilders.standaloneSetup(controller()).setValidator(validator()).build()
                .perform(post("/todo/config/sla-rules").contentType("application/json").content("""
                        {"ruleCode":"SLA-A","ruleName":"A","slaType":"RESPONSE","durationValue":30,
                         "durationUnit":"MINUTE","calendarCode":"DEFAULT","startStrategy":"TODO_CREATED",
                         "softRemindPercent":100,"hardRemindPercent":80,"escalatePercent":150,
                         "status":"0","actionId":"sla-invalid","expectedVersion":0}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDoDWithInvalidRequiredJsonBeforeServiceMutation() throws Exception
    {
        MockMvcBuilders.standaloneSetup(controller()).setValidator(validator()).build()
                .perform(post("/todo/config/dod-rules").contentType("application/json").content("""
                        {"ruleCode":"DOD-A","ruleName":"A","ruleType":"FORM","requiredFieldsJson":"not-json",
                         "requiredAttachmentsJson":"[]","conditionalRulesJson":"[]","validatorRefsJson":"[]",
                         "errorMessagesJson":"{}","status":"0","actionId":"dod-invalid","expectedVersion":0}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPathAndBodyIdentifierMismatch() {
        SlaRuleCommand body = new SlaRuleCommand(3L,"SLA-A","A","RESPONSE",30,"MINUTE","DEFAULT","TODO_CREATED",
                80,100,150,null,null,null,"0","mismatch",0);
        TodoException failure = assertThrows(TodoException.class,() -> controller().updateSlaRule(2L,body));
        assertEquals("TODO_CONFIGURATION_PATH_BODY_MISMATCH",failure.getBusinessCode());
    }

    @Test void rejectsTemplateMetadataEditWithoutConcurrencyAndActionTokens() throws Exception
    {
        MockMvcBuilders.standaloneSetup(controller()).setValidator(validator()).build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/todo/config/templates/5")
                        .contentType("application/json").content("""
                        {"templateId":5,"templateCode":"T-5","templateName":"Template 5","businessType":"LEAD"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedReleasePaginationThroughTypedQueryContract() throws Exception
    {
        MockMvcBuilders.standaloneSetup(controller()).setValidator(validator()).build()
                .perform(get("/todo/config/release-records").param("offset","-1").param("limit","0"))
                .andExpect(status().isBadRequest());
    }

    @Test void releaseQueryExposesStatusAndPublisherFilters()
    {
        List<String> fields=Arrays.stream(TodoConfigurationController.ReleaseListQuery.class.getRecordComponents())
                .map(component->component.getName()).toList();
        org.junit.jupiter.api.Assertions.assertTrue(fields.contains("status"));
        org.junit.jupiter.api.Assertions.assertTrue(fields.contains("publisher"));
        org.junit.jupiter.api.Assertions.assertTrue(Arrays.stream(TodoConfigurationController.class.getDeclaredMethods())
                .anyMatch(method->method.getName().equals("copyReleaseDraft")));
    }

    @Test void dodTemplateCatalogExposesFormRequirementMetadata()
    {
        List<String> fields=Arrays.stream(TodoConfigurationController.TemplateRuleCatalogEntry.class.getRecordComponents())
                .map(component->component.getName()).toList();
        org.junit.jupiter.api.Assertions.assertTrue(fields.contains("requiredFieldsJson"));
        org.junit.jupiter.api.Assertions.assertTrue(fields.contains("requiredAttachmentsJson"));
        org.junit.jupiter.api.Assertions.assertTrue(fields.contains("conditionalRulesJson"));
    }

    @Test
    void exposesMigrationPermissionsOnRepresentativeReadAndWriteEndpoints() throws Exception
    {
        assertPermission("dashboard","todo:template:list");
        assertPermission("createSlaRule","todo:sla-rule:create");
        assertPermission("simulate","todo:simulation:simulate");
        assertPermission("publish","todo:release:publish");
        assertPermission("triggerEventCatalog","todo:trigger:list");
        assertPermission("triggerTemplateCatalog","todo:trigger:list");
        assertPermission("triggerTemplateVersions","todo:trigger:list");
        assertPermission("importTemplate","todo:template:import");
        assertPermission("toggleTemplate","todo:template:toggle");
        assertPermissionExpression("preflightTemplateDraft","@ss.hasAnyPermi('todo:release:publish,todo:simulation:simulate')");
        assertPermissionExpression("templateEventCatalog","@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:list,todo:release:publish')");
        String editorRead="@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:simulate,todo:release:publish')";
        assertPermissionExpression("templateOwnerCatalog",editorRead);
        assertPermissionExpression("templateRoutingTargetCatalog",editorRead);
        assertPermissionExpression("templateValidatorCatalog","@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:simulate,todo:release:publish,todo:dod-rule:list,todo:dod-rule:create,todo:dod-rule:edit,todo:dod-rule:copy')");
        assertPermissionExpression("templateCalendarCatalog","@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:sla-rule:list,todo:sla-rule:create,todo:sla-rule:edit,todo:sla-rule:copy')");
    }

    @Test
    void triggerVersionCatalogResponseContainsOnlyPublishedIdentityFields() throws Exception
    {
        TodoTemplateService templates=org.mockito.Mockito.mock(TodoTemplateService.class);
        org.mockito.Mockito.when(templates.listPublishedVersionCatalog(7L)).thenReturn(List.of(
                new TriggerTemplateVersionCatalogView(91L,3,"PUBLISHED")));
        TodoConfigurationController controller=new TodoConfigurationController(org.mockito.Mockito.mock(TodoConfigurationQueryService.class),
                org.mockito.Mockito.mock(TodoSlaRuleManagementService.class),org.mockito.Mockito.mock(TodoDodRuleManagementService.class),templates,
                org.mockito.Mockito.mock(TodoDefinitionService.class),org.mockito.Mockito.mock(TodoDefinitionDiffService.class),
                org.mockito.Mockito.mock(TodoConfigurationSimulationService.class));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/todo/config/trigger-catalog/templates/7/versions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]",aMapWithSize(3)))
                .andExpect(jsonPath("$.data[0].versionId").value(91))
                .andExpect(jsonPath("$.data[0].versionNo").value(3))
                .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data[0].definitionJson").doesNotExist());
        org.mockito.Mockito.verify(templates).listPublishedVersionCatalog(7L);
    }

    @Test
    void doesNotAcceptRawMapRequestBodiesAndUsesSafeSimulationFacade() throws Exception
    {
        for (Method method : TodoConfigurationController.class.getDeclaredMethods())
            for (int index=0;index<method.getParameterCount();index++)
                if (Arrays.stream(method.getParameterAnnotations()[index]).anyMatch(RequestBody.class::isInstance))
                    assertFalse(Map.class.isAssignableFrom(method.getParameterTypes()[index]),method.getName()+" accepts raw Map body");
        assertEquals(TodoConfigurationSimulationService.class,
                TodoConfigurationController.class.getDeclaredField("simulation").getType());
    }

    @Test
    void ignoresSpoofedOperatorFieldsAndSuppliesOnlyAuthenticatedActorToSimulation()
    {
        TodoConfigurationSimulationService simulation=org.mockito.Mockito.mock(TodoConfigurationSimulationService.class);
        TodoConfigurationController controller=new TodoConfigurationController(org.mockito.Mockito.mock(TodoConfigurationQueryService.class),
                org.mockito.Mockito.mock(TodoSlaRuleManagementService.class),org.mockito.Mockito.mock(TodoDodRuleManagementService.class),
                org.mockito.Mockito.mock(TodoTemplateService.class),org.mockito.Mockito.mock(TodoDefinitionService.class),
                org.mockito.Mockito.mock(TodoDefinitionDiffService.class),simulation);
        SysUser user=new SysUser();user.setUserId(77L);user.setDeptId(88L);user.setUserName("server-user");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(77L,88L,user,java.util.Set.of()),"N/A"));
        try
        {
            ConfigurationSimulationCommand command=new ConfigurationSimulationCommand("simulation-server-actor",9L,"LEAD_ASSIGNED","LEAD",5L,
                    Map.of("operatorId",999L,"operatorName","spoofed"),LocalDateTime.of(2026,7,21,9,0),List.of());
            controller.simulate(command);
            ArgumentCaptor<Actor> actors=ArgumentCaptor.forClass(Actor.class);
            org.mockito.Mockito.verify(simulation).simulate(org.mockito.Mockito.same(command),actors.capture());
            assertEquals(77L,actors.getValue().userId());assertEquals("server-user",actors.getValue().userName());assertEquals(88L,actors.getValue().deptId());
            assertFalse(Arrays.stream(ConfigurationSimulationCommand.class.getRecordComponents()).map(component->component.getName().toLowerCase())
                    .anyMatch(name->name.contains("operator")||name.equals("userId")||name.equals("deptId")));
        }
        finally {SecurityContextHolder.clearContext();}
    }

    @Test
    void preservesLegacyTemplateAliasesWithoutConfigurationMappingCollision()
    {
        RequestMapping legacy = TodoTemplateController.class.getAnnotation(RequestMapping.class);
        assertFalse(Arrays.stream(legacy.value()).noneMatch("/todo/template"::equals));
        RequestMapping configuration = TodoConfigurationController.class.getAnnotation(RequestMapping.class);
        assertFalse(Arrays.stream(configuration.value()).anyMatch("/todo/template"::equals));
    }

    @Test void keepsLegacyTemplateWriteContractSeparateFromConfigurationMetadataConcurrencyContract() throws Exception
    {
        assertEquals(TemplateCommand.class,TodoTemplateController.class.getDeclaredMethod("update",TemplateCommand.class).getParameterTypes()[0]);
        assertEquals(TemplateMetadataCommand.class,TodoConfigurationController.class.getDeclaredMethod("updateTemplate",Long.class,TemplateMetadataCommand.class).getParameterTypes()[1]);
    }

    private void assertPermission(String methodName,String expected) throws Exception
    {
        Method method=Arrays.stream(TodoConfigurationController.class.getDeclaredMethods()).filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertEquals("@ss.hasPermi('"+expected+"')",method.getAnnotation(PreAuthorize.class).value());
    }
    private void assertPermissionExpression(String methodName,String expected) throws Exception
    {
        Method method=Arrays.stream(TodoConfigurationController.class.getDeclaredMethods()).filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertEquals(expected,method.getAnnotation(PreAuthorize.class).value());
    }

    private LocalValidatorFactoryBean validator(){LocalValidatorFactoryBean validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();return validator;}

    private TodoConfigurationController controller()
    {
        return new TodoConfigurationController(org.mockito.Mockito.mock(TodoConfigurationQueryService.class),
                org.mockito.Mockito.mock(TodoSlaRuleManagementService.class),org.mockito.Mockito.mock(TodoDodRuleManagementService.class),
                org.mockito.Mockito.mock(TodoTemplateService.class),org.mockito.Mockito.mock(TodoDefinitionService.class),
                org.mockito.Mockito.mock(TodoDefinitionDiffService.class),org.mockito.Mockito.mock(TodoConfigurationSimulationService.class));
    }
}

package com.ruoyi.web.controller.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import com.law.todo.application.*;

@SpringJUnitConfig(TodoConfigurationControllerMethodSecurityMvcTest.Config.class)
@WebAppConfiguration
class TodoConfigurationControllerMethodSecurityMvcTest {
    @jakarta.annotation.Resource TodoConfigurationController controller;
    @jakarta.annotation.Resource RequestMappingHandlerMapping handlerMapping;
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void deniesMissingPermissionWith403() throws Exception {authenticate("other");mvc().perform(get("/todo/config/dashboard")).andExpect(status().isForbidden());}
    @Test void allowsMatchingPermission() throws Exception {authenticate("todo:template:list");mvc().perform(get("/todo/config/dashboard")).andExpect(status().isOk());}
    @Test void triggerCatalogsRequireOnlyTriggerListPermission() throws Exception {
      authenticate("todo:trigger:list");
      mvc().perform(get("/todo/config/trigger-catalog/events")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/trigger-catalog/templates")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/trigger-catalog/templates/7/versions")).andExpect(status().isOk());
      authenticate("todo:template:list");
      mvc().perform(get("/todo/config/trigger-catalog/events")).andExpect(status().isForbidden());
    }
    @Test void templateCreatorCanUseOnlyPurposeSpecificEditorCatalogsAndVersionRead() throws Exception {
      authenticate("todo:template:create");
      mvc().perform(get("/todo/config/template-catalog/events")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/template-catalog/sla-rules")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/template-catalog/dod-rules")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/template-catalog/calendars")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/template-catalog/validators")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/templates/7/versions")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/sla-rules")).andExpect(status().isForbidden());
      mvc().perform(get("/todo/config/release-records")).andExpect(status().isForbidden());
    }
    @Test void ruleEditorsCanReadOnlyTheirRequiredSharedCatalogs() throws Exception {
      authenticate("todo:sla-rule:list");
      mvc().perform(get("/todo/config/template-catalog/calendars")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/template-catalog/validators")).andExpect(status().isForbidden());
      authenticate("todo:dod-rule:list");
      mvc().perform(get("/todo/config/template-catalog/validators")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/template-catalog/calendars")).andExpect(status().isForbidden());
      authenticate("todo:dod-rule:create");
      mvc().perform(get("/todo/config/resources/validators?businessType=LEAD")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/resources/fields?businessType=LEAD")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/resources/materials?businessType=LEAD")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/resources/dod-recipes?businessType=LEAD")).andExpect(status().isOk());
    }
    @Test void releaseListPermissionCanReadOnlyTheImmutableVersionDirectory() throws Exception {
      authenticate("todo:release:list");
      mvc().perform(get("/todo/config/release-records/9/versions")).andExpect(status().isOk());
    }
    @Test void simulationListPermissionCanReadOnlyItsRequiredCatalogs() throws Exception {
      authenticate("todo:simulation:list");
      mvc().perform(get("/todo/config/templates")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/templates/7")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/templates/7/versions")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/template-catalog/events")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/business-objects?businessType=LEAD&pageNum=1&pageSize=20")).andExpect(status().isOk())
          .andExpect(jsonPath("$.emptyReason").value("NO_DATA")).andExpect(jsonPath("$.sampleFallback").value(true));
      mvc().perform(post("/todo/config/simulations").contentType("application/json").content("""
          {"requestId":"r-1","versionId":7,"eventType":"LEAD_CREATED","payloadVersion":1,
           "businessType":"LEAD","businessId":1,"payload":{"stage":"READY"},
           "effectiveAt":"2026-07-21T09:00:00","taskCompletions":[],"expectedDefinitionHash":"hash"}
          """))
          .andExpect(status().isForbidden());
    }
    @Test void publishedDiagnosticsRequireSimulationExecutionPermission() throws Exception {
      authenticate("todo:simulation:list");
      mvc().perform(post("/todo/config/simulations/published-diagnostics")).andExpect(status().isForbidden());
      authenticate("todo:simulation:simulate");
      mvc().perform(post("/todo/config/simulations/published-diagnostics")).andExpect(status().isOk())
          .andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void eventResourcesUseDedicatedReadAndWritePermissions() throws Exception {
      String body="""
          {"eventType":"LEAD_ASSIGNED","payloadVersion":1,"eventName":"线索已分配",
           "businessObjectType":"LEAD","sourceModule":"lead",
           "payloadSchemaJson":"{\\"type\\":\\"object\\",\\"properties\\":{\\"ownerId\\":{\\"type\\":\\"integer\\",\\"title\\":\\"负责人\\"}}}",
           "samplePayloadJson":"{\\"ownerId\\":11}","status":"DRAFT","actionId":"evt-1","expectedVersion":0}
          """;
      authenticate("todo:resource:list");
      mvc().perform(get("/todo/config/resources/events")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/resources/validators?businessType=LEAD")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/resources/fields?businessType=LEAD")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/resources/materials?businessType=LEAD")).andExpect(status().isOk());
      mvc().perform(get("/todo/config/resources/dod-recipes?businessType=LEAD")).andExpect(status().isOk());
      mvc().perform(post("/todo/config/resources/events").contentType("application/json").content(body))
          .andExpect(status().isForbidden());
      authenticate("todo:resource:add");
      mvc().perform(get("/todo/config/resources/events")).andExpect(status().isForbidden());
      mvc().perform(post("/todo/config/resources/events").contentType("application/json").content(body))
          .andExpect(status().isOk());
    }
    @Test void registersEveryConfigurationEndpointAsOneUniqueHandler(){long count=handlerMapping.getHandlerMethods().entrySet().stream().filter(entry->entry.getValue().getBeanType()==TodoConfigurationController.class).count();org.junit.jupiter.api.Assertions.assertTrue(count>=20);org.junit.jupiter.api.Assertions.assertEquals(count,handlerMapping.getHandlerMethods().entrySet().stream().filter(entry->entry.getValue().getBeanType()==TodoConfigurationController.class).map(entry->entry.getKey().toString()).distinct().count());}
    private MockMvc mvc(){return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new Denied()).build();}
    private void authenticate(String permission){
      com.ruoyi.common.core.domain.entity.SysUser user=new com.ruoyi.common.core.domain.entity.SysUser();
      user.setUserId(7L);user.setDeptId(2L);user.setUserName("tester");
      com.ruoyi.common.core.domain.model.LoginUser principal=new com.ruoyi.common.core.domain.model.LoginUser(7L,2L,user,Set.of(permission));
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,"x",Set.of(new SimpleGrantedAuthority(permission))));
    }
    @ControllerAdvice static class Denied {@ExceptionHandler(AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN) void denied(){}}
    @Configuration @EnableWebMvc @EnableMethodSecurity static class Config {
      @Bean PermissionProbe ss(){return new PermissionProbe();}
      @Bean TodoConfigurationController controller(){
        TodoConfigurationQueryService query=org.mockito.Mockito.mock(TodoConfigurationQueryService.class);
        org.mockito.Mockito.when(query.templatePage(org.mockito.ArgumentMatchers.anyMap())).thenReturn(
            new com.law.todo.application.view.TodoConfigurationViews.TemplatePage(java.util.List.of(),0));
        org.mockito.Mockito.when(query.businessObjects(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.nullable(String.class),
            org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any())).thenReturn(
            new com.law.todo.application.view.TodoConfigurationViews.BusinessObjectPage(java.util.List.of(),0,"NO_DATA",true));
        TodoEventResourceService eventResources=org.mockito.Mockito.mock(TodoEventResourceService.class);
        org.mockito.Mockito.when(eventResources.list(org.mockito.ArgumentMatchers.anyMap())).thenReturn(
            new com.law.todo.application.view.TodoResourceViews.EventResourcePage(java.util.List.of(),0));
        org.mockito.Mockito.when(eventResources.save(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(9L);
        TodoPublishedSimulationDiagnosticService diagnostics=org.mockito.Mockito.mock(TodoPublishedSimulationDiagnosticService.class);
        org.mockito.Mockito.when(diagnostics.diagnose(org.mockito.ArgumentMatchers.any())).thenReturn(
            new com.law.todo.application.view.TodoConfigurationViews.PublishedSimulationDiagnosticSummary(
                0,0,0,0,java.util.List.of(),java.time.LocalDateTime.of(2026,7,23,9,0)));
        return new TodoConfigurationController(query,org.mockito.Mockito.mock(TodoSlaRuleManagementService.class),
            org.mockito.Mockito.mock(TodoDodRuleManagementService.class),org.mockito.Mockito.mock(TodoTemplateService.class),
            org.mockito.Mockito.mock(TodoDefinitionService.class),org.mockito.Mockito.mock(TodoDefinitionDiffService.class),
            org.mockito.Mockito.mock(TodoConfigurationSimulationService.class),org.mockito.Mockito.mock(TodoDefinitionCatalogService.class),
            org.mockito.Mockito.mock(TodoAutoActionCapabilityCatalogService.class),eventResources,
            org.mockito.Mockito.mock(TodoConfigurationResourceCatalogService.class),diagnostics);
      }
    }
    static class PermissionProbe {public boolean hasPermi(String permission){return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));}public boolean hasAnyPermi(String permissions){return java.util.Arrays.stream(permissions.split(",")).anyMatch(this::hasPermi);}}
}

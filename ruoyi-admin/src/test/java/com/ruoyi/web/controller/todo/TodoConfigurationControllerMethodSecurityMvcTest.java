package com.ruoyi.web.controller.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    @Test void registersEveryConfigurationEndpointAsOneUniqueHandler(){long count=handlerMapping.getHandlerMethods().entrySet().stream().filter(entry->entry.getValue().getBeanType()==TodoConfigurationController.class).count();org.junit.jupiter.api.Assertions.assertTrue(count>=20);org.junit.jupiter.api.Assertions.assertEquals(count,handlerMapping.getHandlerMethods().entrySet().stream().filter(entry->entry.getValue().getBeanType()==TodoConfigurationController.class).map(entry->entry.getKey().toString()).distinct().count());}
    private MockMvc mvc(){return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new Denied()).build();}
    private void authenticate(String permission){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("tester","x",Set.of(new SimpleGrantedAuthority(permission))));}
    @ControllerAdvice static class Denied {@ExceptionHandler(AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN) void denied(){}}
    @Configuration @EnableWebMvc @EnableMethodSecurity static class Config {
      @Bean PermissionProbe ss(){return new PermissionProbe();}
      @Bean TodoConfigurationController controller(){return new TodoConfigurationController(org.mockito.Mockito.mock(TodoConfigurationQueryService.class),org.mockito.Mockito.mock(TodoSlaRuleManagementService.class),org.mockito.Mockito.mock(TodoDodRuleManagementService.class),org.mockito.Mockito.mock(TodoTemplateService.class),org.mockito.Mockito.mock(TodoDefinitionService.class),org.mockito.Mockito.mock(TodoDefinitionDiffService.class),org.mockito.Mockito.mock(TodoConfigurationSimulationService.class));}
    }
    static class PermissionProbe {public boolean hasPermi(String permission){return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));}}
}

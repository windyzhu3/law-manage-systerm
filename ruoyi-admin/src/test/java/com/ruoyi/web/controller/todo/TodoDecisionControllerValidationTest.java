package com.ruoyi.web.controller.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.law.todo.application.TodoDecisionManagementService;
import com.law.todo.application.TodoDefinitionCatalogService;
import com.law.todo.application.TodoAutoActionCapabilityCatalogService;
import org.springframework.security.access.prepost.PreAuthorize;

class TodoDecisionControllerValidationTest
{
    @Test void rejectsCreateWithoutStrongDecisionContract() throws Exception
    {
        mvc().perform(post("/todo/decisions").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void rejectsUpdateWhenBodyIdOrVersionIsMissing() throws Exception
    {
        mvc().perform(put("/todo/decisions/9").contentType("application/json").content("{\"actionId\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void exposesAutoActionCatalogAtDefinitionViewEndpoint() throws Exception
    {
        mvc().perform(get("/todo/auto-action-capabilities")).andExpect(status().isOk());
    }
    @Test void autoActionCatalogRequiresDefinitionViewPermission() throws Exception
    {
        assertEquals("@ss.hasPermi('todo:definition:view')",TodoDefinitionCatalogController.class.getMethod("autoActionCapabilities").getAnnotation(PreAuthorize.class).value());
    }

    private org.springframework.test.web.servlet.MockMvc mvc()
    {
        LocalValidatorFactoryBean validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new TodoDefinitionCatalogController(
                org.mockito.Mockito.mock(TodoDefinitionCatalogService.class),
                org.mockito.Mockito.mock(TodoDecisionManagementService.class),
                new TodoAutoActionCapabilityCatalogService(java.util.List.of()))).setValidator(validator).build();
    }
}

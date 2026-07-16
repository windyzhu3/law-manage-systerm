package com.ruoyi.web.controller.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.law.todo.application.TodoCollaborationService;
import com.law.todo.application.TodoCommandService;
import com.law.todo.application.TodoQueryService;
import com.ruoyi.common.utils.SecurityUtils;

class TodoControllerValidationTest
{
    @Test
    void rejectsAttachmentWithoutTypedMaterialFields() throws Exception
    {
        TodoController controller = new TodoController(
            org.mockito.Mockito.mock(TodoQueryService.class),
            org.mockito.Mockito.mock(TodoCommandService.class),
            org.mockito.Mockito.mock(TodoCollaborationService.class));

        mvc(controller).perform(post("/todo/1/attachment")
            .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void rejectsClientSuppliedTargetStatusWithoutInvokingService() throws Exception
    {
        TodoCommandService command=org.mockito.Mockito.mock(TodoCommandService.class);
        TodoController controller = new TodoController(
            org.mockito.Mockito.mock(TodoQueryService.class), command,
            org.mockito.Mockito.mock(TodoCollaborationService.class));

        mvc(controller).perform(post("/todo/1/complete").contentType("application/json")
            .content("{\"actionId\":\"a-1\",\"fields\":{},\"fileObjectIds\":[],\"targetStatus\":\"COMPLETED\"}"))
            .andExpect(status().isBadRequest());
        verify(command,never()).complete(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }

    @Test void exposesRuntimeFormEndpoint() throws Exception
    {
        TodoQueryService query=org.mockito.Mockito.mock(TodoQueryService.class);
        TodoController controller = new TodoController(query,
            org.mockito.Mockito.mock(TodoCommandService.class),
            org.mockito.Mockito.mock(TodoCollaborationService.class));

        try(var security=org.mockito.Mockito.mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("alice");security.when(SecurityUtils::getDeptId).thenReturn(3L);
            mvc(controller).perform(get("/todo/1/form")).andExpect(status().isOk());
            verify(query).form(org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.any());
        }
    }

    private MockMvc mvc(Object controller)
    {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }
}

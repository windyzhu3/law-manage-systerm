package com.ruoyi.web.controller.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.law.todo.application.TodoCollaborationService;
import com.law.todo.application.TodoCommandService;
import com.law.todo.application.TodoQueryService;

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

    private MockMvc mvc(Object controller)
    {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }
}

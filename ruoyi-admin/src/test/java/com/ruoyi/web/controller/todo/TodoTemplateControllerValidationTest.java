package com.ruoyi.web.controller.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.law.todo.application.TodoTemplateService;

class TodoTemplateControllerValidationTest
{
    @Test
    void rejectsTemplateWithoutRequiredBusinessContract() throws Exception
    {
        TodoTemplateController controller = new TodoTemplateController(org.mockito.Mockito.mock(TodoTemplateService.class));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();validator.afterPropertiesSet();

        MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build()
            .perform(post("/todo/template").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }
}

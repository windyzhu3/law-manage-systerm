package com.ruoyi.web.controller.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import com.law.todo.application.TodoExceptionOperationService;

class TodoOperationsControllerValidationTest
{
    @Test void rejectsDangerousOperationWithoutReasonAndActionId() throws Exception
    {
        LocalValidatorFactoryBean validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        MockMvcBuilders.standaloneSetup(new TodoOperationsController(org.mockito.Mockito.mock(TodoExceptionOperationService.class))).setValidator(validator).build()
            .perform(post("/todo/operations/9/force-complete").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }
    @Test void rejectsBatchTransferWithoutTodosAndOwner() throws Exception
    {
        LocalValidatorFactoryBean validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        MockMvcBuilders.standaloneSetup(new TodoOperationsController(org.mockito.Mockito.mock(TodoExceptionOperationService.class))).setValidator(validator).build()
            .perform(post("/todo/operations/batch-transfer").contentType("application/json").content("{\"actionId\":\"a\",\"reason\":\"r\"}"))
            .andExpect(status().isBadRequest());
    }
}

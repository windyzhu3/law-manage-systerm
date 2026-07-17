package com.ruoyi.web.controller.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.law.todo.application.TodoTemplateService;
import com.law.todo.application.TodoDefinitionService;

class TodoTemplateControllerValidationTest
{
    @Test
    void rejectsTemplateWithoutRequiredBusinessContract() throws Exception
    {
        TodoTemplateController controller = controller();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();validator.afterPropertiesSet();

        MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build()
            .perform(post("/todo/template").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCopyWithoutActionIdAndStableCode() throws Exception
    {
        TodoTemplateController controller = controller();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();validator.afterPropertiesSet();

        MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build()
            .perform(post("/todo/template/1/copy").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void rejectsSimulationWithoutPayloadAndEffectiveTime() throws Exception
    {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        MockMvcBuilders.standaloneSetup(controller()).setValidator(validator).build()
            .perform(post("/todo/template/version/9/simulate").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void rejectsRollbackDraftWithoutActionAndTargetVersion() throws Exception
    {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        MockMvcBuilders.standaloneSetup(controller()).setValidator(validator).build()
            .perform(post("/todo/template/version/9/rollback-draft").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void rejectsSimulationTaskCompletionWithoutStrongPayloadAndCompletionTime() throws Exception
    {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        MockMvcBuilders.standaloneSetup(controller()).setValidator(validator).build()
            .perform(post("/todo/template/version/9/simulate").contentType("application/json").content("""
                    {"payload":{"stage":"READY"},"businessType":"LEAD","businessId":3,"effectiveAt":"2026-07-17T09:00:00",
                     "taskCompletions":[{"nodeKey":"start"}]}
                    """))
            .andExpect(status().isBadRequest());
    }
    private TodoTemplateController controller(){return new TodoTemplateController(org.mockito.Mockito.mock(TodoTemplateService.class),org.mockito.Mockito.mock(TodoDefinitionService.class),org.mockito.Mockito.mock(com.law.todo.application.TodoDefinitionSimulationService.class),org.mockito.Mockito.mock(com.law.todo.application.TodoDefinitionDiffService.class),org.mockito.Mockito.mock(com.law.todo.application.TodoDefinitionCatalogService.class));}
}

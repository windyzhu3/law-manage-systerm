package com.ruoyi.web.controller.lead;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.ruoyi.system.service.IBizLeadService;
import com.ruoyi.system.service.ISysUserService;

class BizLeadControllerValidationTest
{
    private IBizLeadService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        service = mock(IBizLeadService.class);
        BizLeadController controller = new BizLeadController();
        ReflectionTestUtils.setField(controller, "leadService", service);
        ReflectionTestUtils.setField(controller, "userService", mock(ISysUserService.class));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }

    @Test
    void assignKeepsUrlAndTypedJsonContract() throws Exception
    {
        when(service.assignLead(7L, 9L, "首次分配")).thenReturn(1);

        mvc.perform(post("/lead/assign").contentType("application/json")
                .content("{\"leadId\":7,\"ownerId\":9,\"reason\":\"首次分配\"}"))
                .andExpect(status().isOk());

        verify(service).assignLead(7L, 9L, "首次分配");
    }

    @Test
    void invalidFollowupIsRejectedBeforeService() throws Exception
    {
        mvc.perform(post("/lead/followup").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}

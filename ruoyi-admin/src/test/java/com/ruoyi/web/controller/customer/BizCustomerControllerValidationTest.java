package com.ruoyi.web.controller.customer;

import static org.mockito.ArgumentMatchers.argThat;
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

import com.ruoyi.system.service.IBizCustomerService;
import com.ruoyi.system.service.ISysUserService;

class BizCustomerControllerValidationTest
{
    private IBizCustomerService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        service = mock(IBizCustomerService.class);
        BizCustomerController controller = new BizCustomerController();
        ReflectionTestUtils.setField(controller, "customerService", service);
        ReflectionTestUtils.setField(controller, "userService", mock(ISysUserService.class));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }

    @Test
    void mergeKeepsUrlAndTypedJsonContract() throws Exception
    {
        when(service.mergeCustomer(argThat(command -> command.getMainCustomerId() == 7L
                && command.getMergedCustomerId() == 8L
                && "重复客户".equals(command.getContent())))).thenReturn(1);

        mvc.perform(post("/customer/merge").contentType("application/json")
                .content("{\"mainCustomerId\":7,\"mergedCustomerId\":8,\"content\":\"重复客户\"}"))
                .andExpect(status().isOk());

        verify(service).mergeCustomer(argThat(command -> command.getMainCustomerId() == 7L
                && command.getMergedCustomerId() == 8L));
    }

    @Test
    void invalidContactIsRejectedBeforeService() throws Exception
    {
        mvc.perform(post("/customer/contact").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}

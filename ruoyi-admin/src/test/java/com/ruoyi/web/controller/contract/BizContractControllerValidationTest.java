package com.ruoyi.web.controller.contract;

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
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.ISysUserService;

class BizContractControllerValidationTest
{
    private IBizContractService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        service = mock(IBizContractService.class);
        BizContractController controller = new BizContractController();
        ReflectionTestUtils.setField(controller, "contractService", service);
        ReflectionTestUtils.setField(controller, "userService", mock(ISysUserService.class));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }

    @Test
    void feePlanKeepsUrlAndCurrentJsonFields() throws Exception
    {
        when(service.insertFeePlan(argThat(command -> command.getContractId() == 10L
                && command.getPeriodNo() == 1))).thenReturn(1);

        mvc.perform(post("/contract/fee").contentType("application/json")
                .content("{\"contractId\":10,\"periodNo\":1,\"receivableAmount\":1000,"
                        + "\"planReceiveDate\":\"2026-08-01\"}"))
                .andExpect(status().isOk());

        verify(service).insertFeePlan(argThat(command -> command.getContractId() == 10L
                && command.getPeriodNo() == 1));
    }

    @Test
    void invalidTemplateIsRejectedBeforeService() throws Exception
    {
        mvc.perform(post("/contract/template").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void feeConfirmationKeepsOptionalJsonFields() throws Exception
    {
        when(service.confirmFeePlan(argThat(command -> command.getPlanId() == 21L
                && "100.00".equals(command.getReceivedAmount())
                && "bank".equals(command.getPaymentMethod())
                && "/proof.png".equals(command.getVoucherUrl())))).thenReturn(1);

        mvc.perform(post("/contract/fee/confirm").contentType("application/json")
                .content("{\"planId\":21,\"receivedAmount\":\"100.00\","
                        + "\"paymentMethod\":\"bank\",\"voucherUrl\":\"/proof.png\"}"))
                .andExpect(status().isOk());

        verify(service).confirmFeePlan(argThat(command -> command.getPlanId() == 21L
                && "bank".equals(command.getPaymentMethod())));
    }
}

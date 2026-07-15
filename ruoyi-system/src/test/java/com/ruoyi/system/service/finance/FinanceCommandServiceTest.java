package com.ruoyi.system.service.finance;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.finance.dto.InvoiceHandleCommand;
import com.law.business.finance.dto.PaymentConfirmCommand;
import com.law.business.finance.dto.PaymentRejectCommand;
import com.ruoyi.system.mapper.BizFinanceMapper;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class FinanceCommandServiceTest
{
    @Mock private BizFinanceMapper financeMapper;
    @Mock private IBizContractService contractService;
    @Mock private ISysDictTypeService dictTypeService;
    @InjectMocks private FinanceCommandService service;

    @Test
    void financeDelegatesToTypedContractPaymentService()
    {
        PaymentConfirmCommand command = new PaymentConfirmCommand();
        command.setPlanId(21L);
        command.setReceivedAmount("100.00");
        command.setPaymentMethod("bank");
        command.setReason("到账");

        service.confirmPayment(command);

        verify(contractService).confirmFeePlan(argThat(value -> value.getPlanId() == 21L
                && "100.00".equals(value.getReceivedAmount())
                && "bank".equals(value.getPaymentMethod())
                && "到账".equals(value.getRemark())));
    }

    @Test
    void financeDelegatesToTypedContractPaymentRejection()
    {
        PaymentRejectCommand command = new PaymentRejectCommand();
        command.setPlanId(21L);
        command.setReason("凭证不清晰");

        service.rejectPayment(command);

        verify(contractService).rejectFeePlan(argThat(value -> value.getPlanId() == 21L
                && "凭证不清晰".equals(value.getReason())));
    }

    @Test
    void financeDelegatesToTypedContractInvoiceService()
    {
        InvoiceHandleCommand command = new InvoiceHandleCommand();
        command.setPlanId(21L);
        command.setInvoiceStatus("1");
        command.setInvoiceType("normal");
        command.setReason("普票");

        service.handleInvoice(command);

        verify(contractService).invoiceFeePlan(argThat(value -> value.getPlanId() == 21L
                && "1".equals(value.getInvoiceStatus())
                && "normal".equals(value.getInvoiceType())
                && "普票".equals(value.getRemark())));
    }
}

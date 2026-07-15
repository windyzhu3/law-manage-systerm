package com.ruoyi.system.service.contract;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class ContractInvoiceServiceTest
{
    @Mock private BizContractMapper mapper;
    @Mock private ContractAccessPolicy access;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessEventPublisher events;
    @Mock private ContractActionLogService logs;
    private ContractInvoiceService service;

    @BeforeEach
    void setUp()
    {
        service = new ContractInvoiceService(mapper, access, dictionaries, events, logs);
    }

    @Test
    void invoiceUsesPlanAndPersistedLogIdentity()
    {
        when(access.requireFeePlanOperable(21L)).thenReturn(context("1", "0"));
        when(dictionaries.selectDictDataByType("law_finance_invoice_type")).thenReturn(List.of(dict("normal")));
        when(dictionaries.selectDictDataByType("law_contract_invoice_status")).thenReturn(List.of(dict("1")));
        when(dictionaries.selectDictDataByType("law_contract_status_action")).thenReturn(List.of(dict("fee_invoice")));
        when(mapper.updateFeePlanStatus(any())).thenReturn(1);
        when(logs.record(eq(10L), eq("0"), eq("1"), eq("fee_invoice"), any(), eq(actor()))).thenReturn(94L);

        service.invoice(21L, "1", null, "normal", null, null, actor());

        verify(events).publish(argThat(event ->
                "INVOICE_HANDLED:10:21:94".equals(event.getIdempotencyKey())
                        && Long.valueOf(21L).equals(event.getPayload().get("planId"))));
    }

    @Test
    void rejectsInvoiceBeforePaymentConfirmation()
    {
        when(access.requireFeePlanOperable(21L)).thenReturn(context("0", "0"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.invoice(21L, "1", null, null, null, null, actor()));

        assertEquals("STATE_CONFLICT", exception.getBusinessCode());
        verify(mapper, never()).updateFeePlanStatus(any());
    }

    private ContractFeePlanContext context(String confirm, String invoice)
    {
        return new ContractFeePlanContext(21L, 10L, "HT-10", confirm, invoice,
                ContractStatus.PERFORMING.code(), new BigDecimal("100"), new BigDecimal("100"));
    }

    private SysDictData dict(String value)
    {
        SysDictData data = new SysDictData();
        data.setDictValue(value);
        return data;
    }
}

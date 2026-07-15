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
class ContractPaymentServiceTest
{
    @Mock private BizContractMapper mapper;
    @Mock private ContractAccessPolicy access;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessEventPublisher events;
    @Mock private ContractActionLogService logs;
    private ContractPaymentService service;

    @BeforeEach
    void setUp()
    {
        service = new ContractPaymentService(mapper, access, dictionaries, events, logs);
    }

    @Test
    void paymentUsesPlanAndPersistedLogIdentity()
    {
        when(access.requireFeePlanOperable(21L)).thenReturn(context("0", 100, 0));
        when(dictionaries.selectDictDataByType("law_finance_payment_method")).thenReturn(List.of(dict("bank")));
        when(dictionaries.selectDictDataByType("law_contract_status_action")).thenReturn(List.of(dict("fee_confirm")));
        when(mapper.updateFeePlanStatus(any())).thenReturn(1);
        when(logs.record(eq(10L), eq("0"), eq("1"), eq("fee_confirm"), any(), eq(actor()))).thenReturn(93L);

        service.confirm(21L, "100.00", null, "bank", null, actor());

        verify(events).publish(argThat(event ->
                "PAYMENT_CONFIRMED:10:21:93".equals(event.getIdempotencyKey())
                        && Long.valueOf(21L).equals(event.getPayload().get("planId"))
                        && Integer.valueOf(1).equals(event.getPayload().get("schemaVersion"))));
    }

    @Test
    void fullyPaidPlanCannotBeConfirmedAgain()
    {
        when(access.requireFeePlanOperable(21L)).thenReturn(context("1", 100, 100));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.confirm(21L, "10", null, null, null, actor()));

        assertEquals("DUPLICATE_OPERATION", exception.getBusinessCode());
        verify(mapper, never()).updateFeePlanStatus(any());
        verify(events, never()).publish(any());
    }

    @Test
    void rejectionUsesPlanAndPersistedLogIdentity()
    {
        when(access.requireFeePlanOperable(21L)).thenReturn(context("0", 100, 0));
        when(dictionaries.selectDictDataByType("law_contract_status_action"))
                .thenReturn(List.of(dict("fee_reject")));
        when(mapper.updateFeePlanStatus(any())).thenReturn(1);
        when(logs.record(10L, "0", "2", "fee_reject", "凭证不清晰", actor())).thenReturn(95L);

        service.reject(21L, "凭证不清晰", actor());

        verify(events).publish(argThat(event ->
                "PAYMENT_REJECTED:10:21:95".equals(event.getIdempotencyKey())
                        && Long.valueOf(21L).equals(event.getPayload().get("planId"))
                        && Long.valueOf(95L).equals(event.getPayload().get("logId"))));
    }

    private ContractFeePlanContext context(String confirmStatus, int due, int paid)
    {
        return new ContractFeePlanContext(21L, 10L, "HT-10", confirmStatus, "0",
                ContractStatus.PERFORMING.code(), BigDecimal.valueOf(due), BigDecimal.valueOf(paid));
    }

    private SysDictData dict(String value)
    {
        SysDictData data = new SysDictData();
        data.setDictValue(value);
        return data;
    }
}

package com.ruoyi.system.service.contract;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.contract.dto.ContractFeePlanUpdateCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;

@ExtendWith(MockitoExtension.class)
class ContractFeePlanServiceTest
{
    @Mock private BizContractMapper mapper;
    @Mock private ContractAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ContractActionLogService actionLogs;
    private ContractFeePlanService service;

    @BeforeEach
    void setUp()
    {
        service = new ContractFeePlanService(mapper, access, actors, actionLogs);
    }

    @Test
    void feePlanUpdateCannotMoveToAnotherContract()
    {
        when(access.requireFeePlanOperable(21L)).thenReturn(context(21L, 10L, "0", "0", "1"));
        when(actors.current()).thenReturn(actor());
        when(mapper.updateFeePlan(org.mockito.ArgumentMatchers.anyMap())).thenReturn(1);

        service.update(update(21L, 99L));

        verify(mapper).updateFeePlan(argThat(row -> Long.valueOf(10L).equals(row.get("contractId"))
                && "0".equals(row.get("expectedConfirmStatus"))
                && "0".equals(row.get("expectedInvoiceStatus"))
                && "1".equals(row.get("expectedContractStatus"))));
    }

    @Test
    void confirmedPlanCannotBeEdited()
    {
        when(access.requireFeePlanOperable(21L)).thenReturn(context(21L, 10L, "1", "0", "1"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.update(update(21L, 10L)));

        assertEquals("STATE_CONFLICT", exception.getBusinessCode());
        verify(mapper, never()).updateFeePlan(org.mockito.ArgumentMatchers.anyMap());
    }

    private ContractFeePlanUpdateCommand update(Long planId, Long contractId)
    {
        ContractFeePlanUpdateCommand command = new ContractFeePlanUpdateCommand();
        command.setPlanId(planId);
        command.setContractId(contractId);
        command.setPeriodNo(1);
        command.setReceivableAmount(new BigDecimal("1000"));
        command.setPlanReceiveDate(LocalDate.of(2026, 8, 1));
        return command;
    }

    private ContractFeePlanContext context(Long planId, Long contractId, String confirm, String invoice,
            String contractStatus)
    {
        return new ContractFeePlanContext(planId, contractId, "HT-10", confirm, invoice,
                contractStatus, new BigDecimal("1000"), BigDecimal.ZERO);
    }
}

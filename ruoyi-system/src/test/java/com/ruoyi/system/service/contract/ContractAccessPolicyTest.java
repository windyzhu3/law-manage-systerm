package com.ruoyi.system.service.contract;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

@ExtendWith(MockitoExtension.class)
class ContractAccessPolicyTest
{
    @Mock private BizContractMapper mapper;
    @Mock private BusinessActorProvider actors;

    @Test
    void inaccessibleContractIsRejected()
    {
        when(mapper.selectContractById(9L)).thenReturn(contract(9L, "0", "0"));
        when(actors.current()).thenReturn(actor());
        when(mapper.countContractInDataScope(eq(9L), eq(8L), eq(3L), anyString())).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> policy().requireReadable(9L));

        assertEquals("ACCESS_DENIED", exception.getBusinessCode());
    }

    @Test
    void missingContractIsReportedAsDataNotFound()
    {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> policy().requireReadable(9L));

        assertEquals("DATA_NOT_FOUND", exception.getBusinessCode());
    }

    @Test
    void terminalContractCannotBeOperated()
    {
        when(mapper.selectContractById(9L)).thenReturn(contract(9L, "3", "0"));
        when(actors.current()).thenReturn(actor());
        when(mapper.countContractInDataScope(eq(9L), eq(8L), eq(3L), anyString())).thenReturn(1);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> policy().requireOperable(9L));

        assertEquals("STATE_CONFLICT", exception.getBusinessCode());
    }

    @Test
    void feePlanResolvesItsOwningContract()
    {
        Map<String, Object> plan = new HashMap<>();
        plan.put("plan_id", 12L);
        plan.put("contract_id", 9L);
        plan.put("contract_no", "HT-009");
        plan.put("confirm_status", "0");
        plan.put("invoice_status", "0");
        plan.put("contractStatus", "1");
        plan.put("receivable_amount", new BigDecimal("1000"));
        plan.put("received_amount", new BigDecimal("200"));
        when(mapper.selectFeePlanById(12L)).thenReturn(plan);
        when(mapper.selectContractById(9L)).thenReturn(contract(9L, "1", "0"));
        when(actors.current()).thenReturn(actor());
        when(mapper.countContractInDataScope(eq(9L), eq(8L), eq(3L), anyString())).thenReturn(1);

        ContractFeePlanContext context = policy().requireFeePlanOperable(12L);

        assertEquals(9L, context.contractId());
        assertEquals("HT-009", context.contractNo());
        assertEquals(new BigDecimal("1000"), context.receivableAmount());
    }

    @Test
    void missingFeePlanIsReportedAsDataNotFound()
    {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> policy().requireFeePlanOperable(12L));

        assertEquals("DATA_NOT_FOUND", exception.getBusinessCode());
    }

    @Test
    void attachmentResolvesItsOwningContract()
    {
        when(mapper.selectAttachmentContractId(7L)).thenReturn(9L);
        when(mapper.selectContractById(9L)).thenReturn(contract(9L, "1", "0"));
        when(actors.current()).thenReturn(actor());
        when(mapper.countContractInDataScope(eq(9L), eq(8L), eq(3L), anyString())).thenReturn(1);

        assertEquals(9L, policy().requireAttachmentOperable(7L));
    }

    @Test
    void missingAttachmentIsReportedAsDataNotFound()
    {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> policy().requireAttachmentOperable(7L));

        assertEquals("DATA_NOT_FOUND", exception.getBusinessCode());
    }

    private ContractAccessPolicy policy()
    {
        return new ContractAccessPolicy(mapper, actors);
    }

    private BizContract contract(Long id, String status, String delFlag)
    {
        BizContract value = new BizContract();
        value.setContractId(id);
        value.setContractStatus(status);
        value.setDelFlag(delFlag);
        return value;
    }
}

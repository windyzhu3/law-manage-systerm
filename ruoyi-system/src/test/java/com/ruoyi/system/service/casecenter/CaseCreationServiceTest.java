package com.ruoyi.system.service.casecenter;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;

@ExtendWith(MockitoExtension.class)
class CaseCreationServiceTest
{
    @Mock private BizCaseMapper cases;
    @Mock private BizContractMapper contracts;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private ISysNoticeService notices;
    @Mock private BusinessEventPublisher publisher;
    @InjectMocks private CaseCreationService service;

    @Test
    void missingContractIsIgnoredWithoutSideEffects()
    {
        assertEquals(0, service.createFromContract(null, actor()));
        verify(cases, never()).insertCase(anyMap());
    }

    @Test
    void existingContractCaseMakesCreationIdempotent()
    {
        when(cases.selectCaseByContractId(12L)).thenReturn(Map.of("case_id", 30L));

        assertEquals(0, service.createFromContract(12L, actor()));

        verify(cases, never()).insertCase(anyMap());
        verify(publisher, never()).publish(any());
    }

    @Test
    void confirmedPaymentIsRequired()
    {
        when(contracts.selectContractById(12L)).thenReturn(contract());
        when(contracts.countConfirmedFeePlans(12L)).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createFromContract(12L, actor()));

        assertEquals("PRECONDITION_FAILED", error.getBusinessCode());
        verify(cases, never()).insertCase(anyMap());
    }

    @Test
    void createdCasePublishesStableBusinessKey()
    {
        when(contracts.selectContractById(12L)).thenReturn(contract());
        when(contracts.countConfirmedFeePlans(12L)).thenReturn(1);
        when(cases.insertCase(anyMap())).thenAnswer(invocation -> {
            invocation.<Map<String,Object>>getArgument(0).put("caseId", 81L);
            return 1;
        });
        when(cases.insertStatusLog(anyMap())).thenReturn(1);

        assertEquals(1, service.createFromContract(12L, actor()));

        verify(publisher).publish(org.mockito.ArgumentMatchers.argThat(event ->
                "CASE_CREATED:81".equals(event.getIdempotencyKey())
                && Long.valueOf(12L).equals(event.getPayload().get("contractId"))));
    }

    private BizContract contract()
    {
        BizContract value = new BizContract();
        value.setContractId(12L);
        value.setContractNo("HT-12");
        value.setContractName("常年法律顾问合同");
        value.setCustomerId(21L);
        value.setCustomerName("客户甲");
        value.setCaseType("civil");
        value.setSignStatus("1");
        value.setDelFlag("0");
        value.setOwnerId(8L);
        value.setDeptId(3L);
        value.setSignAmount(new BigDecimal("100.00"));
        return value;
    }
}

package com.ruoyi.system.service.contract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.IBizCaseService;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class ContractLifecycleServiceTest
{
    @Mock private BizContractMapper mapper;
    @Mock private ContractQueryService queryService;
    @Mock private IBizCaseService caseService;
    @Mock private ISysDictTypeService dictService;
    @Mock private BusinessEventPublisher eventPublisher;
    private ContractLifecycleService service;

    @BeforeEach
    void setUp()
    {
        service = new ContractLifecycleService(mapper, queryService, caseService, dictService, eventPublisher);
    }

    @Test
    void submitRejectsContractThatIsAlreadyReviewing()
    {
        BizContract contract = contract(ContractAuditStatus.REVIEWING.code(), ContractStatus.DRAFT.code(), ContractSignStatus.UNSIGNED.code());
        when(queryService.contract(10L)).thenReturn(contract);

        assertThrows(ServiceException.class, () -> service.submit(10L));

        verify(mapper, never()).updateAuditStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void approveRequiresOpinionBeforeLoadingContract()
    {
        ServiceException exception = assertThrows(ServiceException.class, () -> service.approve(10L, "pass", ""));
        assertEquals("VALIDATION_FAILED", exception.getBusinessCode());

        verify(queryService, never()).contract(10L);
    }

    @Test
    void signRejectsContractBeforeAuditPasses()
    {
        BizContract contract = contract(ContractAuditStatus.REVIEWING.code(), ContractStatus.DRAFT.code(), ContractSignStatus.UNSIGNED.code());
        when(queryService.contract(10L)).thenReturn(contract);

        assertThrows(ServiceException.class, () -> service.sign(10L, ContractSignStatus.SIGNED.code()));

        verify(mapper, never()).updateLifecycleStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(caseService, never()).createCaseFromContract(org.mockito.ArgumentMatchers.any());
    }

    private BizContract contract(String auditStatus, String contractStatus, String signStatus)
    {
        BizContract contract = new BizContract();
        contract.setContractId(10L);
        contract.setContractNo("HT-10");
        contract.setAuditStatus(auditStatus);
        contract.setContractStatus(contractStatus);
        contract.setSignStatus(signStatus);
        return contract;
    }
}

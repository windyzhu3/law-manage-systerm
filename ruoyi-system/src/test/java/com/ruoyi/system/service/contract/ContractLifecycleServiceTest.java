package com.ruoyi.system.service.contract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActorProvider;
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
    @Mock private ContractAccessPolicy access;
    @Mock private IBizCaseService caseService;
    @Mock private ISysDictTypeService dictService;
    @Mock private BusinessEventPublisher eventPublisher;
    @Mock private ContractActionLogService actionLogs;
    @Mock private BusinessActorProvider actors;
    private ContractLifecycleService service;

    @BeforeEach
    void setUp()
    {
        service = new ContractLifecycleService(mapper, access, caseService, dictService,
                eventPublisher, actionLogs, actors);
    }

    @Test
    void submitRejectsContractThatIsAlreadyReviewing()
    {
        BizContract contract = contract(ContractAuditStatus.REVIEWING.code(), ContractStatus.DRAFT.code(), ContractSignStatus.UNSIGNED.code());
        when(access.requireOperable(10L)).thenReturn(contract);

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

        verify(access, never()).requireOperable(10L);
    }

    @Test
    void signRejectsContractBeforeAuditPasses()
    {
        BizContract contract = contract(ContractAuditStatus.REVIEWING.code(), ContractStatus.DRAFT.code(), ContractSignStatus.UNSIGNED.code());
        when(access.requireOperable(10L)).thenReturn(contract);

        assertThrows(ServiceException.class, () -> service.sign(10L, ContractSignStatus.SIGNED.code()));

        verify(mapper, never()).updateLifecycleStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(caseService, never()).createCaseFromContract(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void submitUsesPersistedLogId()
    {
        BizContract contract = contract(ContractAuditStatus.PENDING.code(), ContractStatus.DRAFT.code(),
                ContractSignStatus.UNSIGNED.code());
        when(access.requireOperable(10L)).thenReturn(contract);
        when(actors.current()).thenReturn(com.ruoyi.system.support.BusinessFixtures.actor());
        when(mapper.updateAuditStatus(eq(10L), eq(ContractAuditStatus.REVIEWING.code()),
                eq(ContractStatus.DRAFT.code()), eq(ContractAuditStatus.PENDING.code()),
                eq(ContractStatus.DRAFT.code()), eq("alice"))).thenReturn(1);
        when(dictService.selectDictDataByType("law_contract_status_action"))
                .thenReturn(java.util.List.of(dict("submit")));
        when(actionLogs.record(eq(10L), eq(ContractAuditStatus.PENDING.code()),
                eq(ContractAuditStatus.REVIEWING.code()), eq("submit"),
                org.mockito.ArgumentMatchers.anyString(), eq(com.ruoyi.system.support.BusinessFixtures.actor())))
                .thenReturn(91L);

        service.submit(10L);

        verify(eventPublisher).publish(argThat(event ->
                "CONTRACT_SUBMITTED:10:91".equals(event.getIdempotencyKey())
                        && Integer.valueOf(1).equals(event.getPayload().get("schemaVersion"))
                        && Long.valueOf(91L).equals(event.getPayload().get("logId"))));
    }

    @Test
    void approvalUsesGeneratedApprovalId()
    {
        BizContract contract = contract(ContractAuditStatus.REVIEWING.code(), ContractStatus.DRAFT.code(),
                ContractSignStatus.UNSIGNED.code());
        when(access.requireOperable(10L)).thenReturn(contract);
        when(dictService.selectDictDataByType("law_contract_approval_action"))
                .thenReturn(java.util.List.of(dict("pass")));
        when(dictService.selectDictDataByType("law_contract_status_action"))
                .thenReturn(java.util.List.of(dict("approval")));
        when(mapper.updateAuditStatus(eq(10L), eq(ContractAuditStatus.PASSED.code()),
                eq(ContractStatus.DRAFT.code()), eq(ContractAuditStatus.REVIEWING.code()),
                eq(ContractStatus.DRAFT.code()), eq("alice"))).thenReturn(1);
        when(mapper.insertApproval(org.mockito.ArgumentMatchers.anyMap())).thenAnswer(invocation -> {
            invocation.<java.util.Map<String, Object>>getArgument(0).put("approvalId", 72L);
            return 1;
        });
        when(actionLogs.record(eq(10L), eq(ContractAuditStatus.REVIEWING.code()),
                eq(ContractAuditStatus.PASSED.code()), eq("approval"), eq("同意"),
                eq(com.ruoyi.system.support.BusinessFixtures.actor()))).thenReturn(92L);

        service.approve(10L, "pass", "同意", com.ruoyi.system.support.BusinessFixtures.actor());

        verify(eventPublisher).publish(argThat(event ->
                "CONTRACT_APPROVED:10:72".equals(event.getIdempotencyKey())
                        && Long.valueOf(72L).equals(event.getPayload().get("approvalId"))));
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

    private com.ruoyi.common.core.domain.entity.SysDictData dict(String value)
    {
        com.ruoyi.common.core.domain.entity.SysDictData data = new com.ruoyi.common.core.domain.entity.SysDictData();
        data.setDictValue(value);
        return data;
    }
}

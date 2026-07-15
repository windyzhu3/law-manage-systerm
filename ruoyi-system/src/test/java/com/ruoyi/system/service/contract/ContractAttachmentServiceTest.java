package com.ruoyi.system.service.contract;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.contract.dto.ContractAttachmentCreateCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

@ExtendWith(MockitoExtension.class)
class ContractAttachmentServiceTest
{
    @Mock private BizContractMapper mapper;
    @Mock private ContractAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ContractActionLogService actionLogs;
    private ContractAttachmentService service;

    @BeforeEach
    void setUp()
    {
        service = new ContractAttachmentService(mapper, access, actors, actionLogs);
    }

    @Test
    void attachmentCreateUsesOwningContractPolicy()
    {
        when(access.requireOperable(10L)).thenReturn(contract(10L, ContractStatus.PERFORMING.code()));
        when(actors.current()).thenReturn(actor());
        when(mapper.insertAttachment(org.mockito.ArgumentMatchers.anyMap())).thenReturn(1);

        service.create(command(10L));

        verify(access).requireOperable(10L);
        verify(mapper).insertAttachment(argThat(row -> "GENERAL".equals(row.get("fileType"))
                && "alice".equals(row.get("createBy"))));
    }

    @Test
    void terminalContractAttachmentIsRejectedByPolicy()
    {
        ServiceException expected = new ServiceException("终态合同", "STATE_CONFLICT");
        when(access.requireOperable(10L)).thenThrow(expected);

        ServiceException actual = assertThrows(ServiceException.class, () -> service.create(command(10L)));

        assertEquals("STATE_CONFLICT", actual.getBusinessCode());
        verify(mapper, never()).insertAttachment(org.mockito.ArgumentMatchers.anyMap());
    }

    private ContractAttachmentCreateCommand command(Long contractId)
    {
        ContractAttachmentCreateCommand command = new ContractAttachmentCreateCommand();
        command.setContractId(contractId);
        command.setFileName("委托书.pdf");
        command.setFileUrl("/files/authorization.pdf");
        command.setFileType("GENERAL");
        command.setFileSize(100L);
        return command;
    }

    private BizContract contract(Long id, String status)
    {
        BizContract contract = new BizContract();
        contract.setContractId(id);
        contract.setContractStatus(status);
        contract.setDelFlag("0");
        return contract;
    }
}

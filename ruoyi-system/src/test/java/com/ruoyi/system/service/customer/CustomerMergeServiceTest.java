package com.ruoyi.system.service.customer;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.customer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.customer.dto.CustomerMergeCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.service.contract.ContractActionLogService;
import com.ruoyi.system.mapper.BizCustomerMapper;

@ExtendWith(MockitoExtension.class)
class CustomerMergeServiceTest
{
    @Mock private BizCustomerMapper mapper;
    @Mock private CustomerAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ContractActionLogService actionLogs;
    private CustomerMergeService service;

    @BeforeEach
    void setUp()
    {
        service = new CustomerMergeService(mapper, access, actors, actionLogs);
    }

    @Test
    void sameCustomerCannotBeMerged()
    {
        CustomerMergeCommand command = command(7L, 7L);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.merge(command));

        assertEquals("VALIDATION_FAILED", exception.getBusinessCode());
        verify(access, never()).requireOperable(any());
    }

    @Test
    void concurrentMergeIsRejectedBeforeRelationshipsMove()
    {
        BizCustomer main = customer(7L, "0", "0");
        main.setCustomerName("主客户");
        BizCustomer merged = customer(8L, "0", "0");
        merged.setCustomerName("重复客户");
        when(access.requireOperable(7L)).thenReturn(main);
        when(access.requireOperable(8L)).thenReturn(merged);
        when(actors.current()).thenReturn(actor());
        when(mapper.selectContractIdsByCustomerId(8L)).thenReturn(List.of());
        when(mapper.markMergedConditionally(8L, "alice", "0")).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.merge(command(7L, 8L)));

        assertEquals("CONCURRENT_MODIFICATION", exception.getBusinessCode());
        verify(mapper, never()).moveContacts(any(), any(), any());
        verify(mapper, never()).insertMergeLog(any(), any(), any(), any());
    }

    private CustomerMergeCommand command(Long mainId, Long mergedId)
    {
        CustomerMergeCommand command = new CustomerMergeCommand();
        command.setMainCustomerId(mainId);
        command.setMergedCustomerId(mergedId);
        command.setContent("重复客户合并");
        return command;
    }
}

package com.ruoyi.system.service.customer;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.customer.dto.CustomerContactUpdateCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class CustomerContactServiceTest
{
    @Mock private BizCustomerMapper mapper;
    @Mock private CustomerAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    private CustomerContactService service;

    @BeforeEach
    void setUp()
    {
        service = new CustomerContactService(mapper, access, actors, dictionaries);
    }

    @Test
    void contactCannotBeMovedToAnotherCustomer()
    {
        when(mapper.selectContactCustomerId(5L)).thenReturn(10L);
        CustomerContactUpdateCommand command = command(5L, 11L);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.update(command));

        assertEquals("ACCESS_DENIED", exception.getBusinessCode());
        verify(access, never()).requireOperable(any());
        verify(mapper, never()).updateContact(any());
    }

    private CustomerContactUpdateCommand command(Long contactId, Long customerId)
    {
        CustomerContactUpdateCommand command = new CustomerContactUpdateCommand();
        command.setContactId(contactId);
        command.setCustomerId(customerId);
        command.setContactName("张三");
        command.setMobile("13800000000");
        command.setRelationType("daily");
        return command;
    }
}

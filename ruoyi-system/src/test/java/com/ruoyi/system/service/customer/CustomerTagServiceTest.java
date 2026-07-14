package com.ruoyi.system.service.customer;

import static com.ruoyi.system.support.BusinessFixtures.customer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.customer.dto.CustomerTagAssignCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class CustomerTagServiceTest
{
    @Mock private BizCustomerMapper mapper;
    @Mock private CustomerAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    private CustomerTagService service;

    @BeforeEach
    void setUp()
    {
        service = new CustomerTagService(mapper, access, actors, dictionaries);
    }

    @Test
    void identicalTagSetDoesNotRewriteRelations()
    {
        CustomerTagAssignCommand command = new CustomerTagAssignCommand();
        command.setCustomerId(10L);
        command.setTagIds(new Long[] { 3L, 2L, 3L });
        when(access.requireOperable(10L)).thenReturn(customer(10L, "0", "0"));
        when(mapper.countEnabledTag(2L)).thenReturn(1);
        when(mapper.countEnabledTag(3L)).thenReturn(1);
        when(mapper.selectCustomerTagIds(10L)).thenReturn(List.of(2L, 3L));

        assertEquals(1, service.assign(command));

        verify(mapper, never()).deleteCustomerTags(10L);
        verify(mapper, never()).insertCustomerTag(10L, 2L);
        verify(mapper, never()).insertCustomerTag(10L, 3L);
    }
}

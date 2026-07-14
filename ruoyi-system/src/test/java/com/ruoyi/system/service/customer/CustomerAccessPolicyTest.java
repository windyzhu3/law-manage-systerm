package com.ruoyi.system.service.customer;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.customer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizCustomerMapper;

@ExtendWith(MockitoExtension.class)
class CustomerAccessPolicyTest
{
    @Mock private BizCustomerMapper mapper;
    @Mock private BusinessActorProvider actors;

    @Test
    void inaccessibleCustomerIsRejected()
    {
        BizCustomer customer = customer(9L, "0", "0");
        when(mapper.selectCustomerById(9L)).thenReturn(customer);
        when(actors.current()).thenReturn(actor());
        when(mapper.countCustomerInDataScope(eq(9L), eq(8L), eq(3L), anyString())).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> new CustomerAccessPolicy(mapper, actors).requireOperable(9L));

        assertEquals("ACCESS_DENIED", exception.getBusinessCode());
    }

    @Test
    void mergedCustomerCannotBeOperated()
    {
        when(mapper.selectCustomerById(9L)).thenReturn(customer(9L, "2", "0"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> new CustomerAccessPolicy(mapper, actors).requireOperable(9L));

        assertEquals("DATA_NOT_FOUND", exception.getBusinessCode());
    }
}

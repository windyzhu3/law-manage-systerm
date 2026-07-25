package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.customer;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.customer.CustomerCommandService;

@ExtendWith(MockitoExtension.class)
class LeadConversionServiceTest
{
    @Mock private BizLeadMapper mapper;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private BusinessEventPublisher events;
    @Mock private CustomerCommandService customers;
    private LeadConversionService service;

    @BeforeEach
    void setUp()
    {
        service = new LeadConversionService(mapper, access, actors, events, customers);
    }

    @Test
    void convertedLeadReturnsExistingCustomerWithoutWriting()
    {
        BizLead lead = lead(7L, LeadStatus.CONVERTED.code(), "0");
        lead.setCustomerId(31L);
        when(access.requireOperable(7L)).thenReturn(lead);

        assertEquals(31L, service.convert(7L, false));

        verify(customers, never()).createFromLead(any());
        verify(mapper, never()).bindCustomerConditionally(any(), any(), any(), any(), any());
        verify(events, never()).publish(any());
    }

    @Test
    void conversionConditionallyBindsCustomerAndPublishesStableEvent()
    {
        BizLead lead = lead(7L, LeadStatus.FOLLOWING.code(), "0");
        lead.setLeadNo("XS0007");
        lead.setOwnerId(8L);
        lead.setRowVersion(7);
        BizCustomer converted = customer(31L, "0", "0");
        when(access.requireOperable(7L)).thenReturn(lead);
        when(actors.current()).thenReturn(actor());
        when(customers.createFromLead(lead)).thenReturn(converted.getCustomerId());
        when(mapper.bindCustomerConditionally(7L, 31L, "alice", LeadStatus.FOLLOWING.code(), 7)).thenReturn(1);

        assertEquals(31L, service.convert(7L, false));

        verify(events).publish(argThat(event -> "LEAD_CONVERTED:7:31".equals(event.getIdempotencyKey())
                && Integer.valueOf(1).equals(event.getPayload().get("schemaVersion"))
                && Long.valueOf(8L).equals(event.getPayload().get("operatorId"))
                && Long.valueOf(31L).equals(event.getPayload().get("customerId"))));
    }

    @Test
    void concurrentConversionIsRejectedBeforePublishingEvent()
    {
        BizLead lead = lead(7L, LeadStatus.FOLLOWING.code(), "0");
        lead.setOwnerId(8L);
        lead.setRowVersion(7);
        when(access.requireOperable(7L)).thenReturn(lead);
        when(actors.current()).thenReturn(actor());
        when(customers.createFromLead(lead)).thenReturn(customer(31L, "0", "0").getCustomerId());
        when(mapper.bindCustomerConditionally(7L, 31L, "alice", LeadStatus.FOLLOWING.code(), 7)).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.convert(7L, false));

        assertEquals("CONCURRENT_MODIFICATION", exception.getBusinessCode());
        verify(events, never()).publish(any());
    }
}

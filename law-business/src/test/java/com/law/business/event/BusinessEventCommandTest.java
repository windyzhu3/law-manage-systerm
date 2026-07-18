package com.law.business.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class BusinessEventCommandTest
{
    @Test void defaultsPayloadVersionToOne()
    {
        BusinessEventCommand command=new BusinessEventCommand(BusinessEventType.LEAD_CREATED,
                "LEAD",1L,"L-1","event-1",Map.of());
        assertEquals(1,command.getPayloadVersion());
    }

    @Test void carriesExplicitPositivePayloadVersion()
    {
        BusinessEventCommand command=new BusinessEventCommand(BusinessEventType.LEAD_CREATED,
                "LEAD",1L,"L-1","event-2",Map.of(),3);
        assertEquals(3,command.getPayloadVersion());
    }

    @Test void rejectsNonPositivePayloadVersion()
    {
        assertThrows(IllegalArgumentException.class,()->new BusinessEventCommand(BusinessEventType.LEAD_CREATED,
                "LEAD",1L,"L-1","event-3",Map.of(),0));
    }
}

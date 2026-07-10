package com.law.business.event;

public interface BusinessEventPublisher
{
    void publish(BusinessEventCommand command);
}

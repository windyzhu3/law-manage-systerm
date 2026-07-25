package com.law.business.event;

import com.law.business.security.BusinessActor;

public interface BusinessEventPublisher
{
    void publish(BusinessEventCommand command);

    /**
     * Publishes with an already authenticated or capability-controlled business actor.
     * Implementations that persist audit metadata should prefer this actor over ambient web state.
     */
    default void publish(BusinessEventCommand command, BusinessActor actor)
    {
        publish(command);
    }
}

package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.law.todo.domain.TodoException;

class TodoTemplateEventPolicyTest
{
    private final TodoTemplateEventPolicy policy=new TodoTemplateEventPolicy();

    @Test void exposesTheCanonicalEventsForAllFourGovernedLeadTemplates()
    {
        assertPolicy("TD-001","LEAD_ASSIGNED");
        assertPolicy("TD-002","LEAD_SUSPECT_INVALID_MARKED");
        assertPolicy("TD-003","LEAD_RETRY_WINDOW_DUE");
        assertPolicy("TD-004","LEAD_FIRST_CONTACT_VALID");
    }

    @Test void td002RejectsTheUnreachableEventAndAcceptsItsReviewEvent()
    {
        policy.requireCompatible("TD-002","LEAD","LEAD_SUSPECT_INVALID_MARKED",1);

        assertThatThrownBy(()->policy.requireCompatible(
                "TD-002","LEAD","LEAD_FIRST_CONTACT_UNREACHABLE",1))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_TEMPLATE_EVENT_INCOMPATIBLE"));
    }

    @Test void ungovernedTemplatesRemainUnrestricted()
    {
        var view=policy.view("CUSTOM-TEMPLATE","LEAD");

        assertThat(view.locked()).isFalse();
        assertThat(view.allowedEventTypes()).isEmpty();
        policy.requireCompatible("CUSTOM-TEMPLATE","LEAD","ANY_ACTIVE_EVENT",7);
    }

    private void assertPolicy(String templateCode,String eventType)
    {
        var view=policy.view(templateCode,"LEAD");
        assertThat(view.locked()).isTrue();
        assertThat(view.recommendedEventType()).isEqualTo(eventType);
        assertThat(view.payloadVersion()).isEqualTo(1);
        assertThat(view.allowedEventTypes()).containsExactly(eventType);
        policy.requireCompatible(templateCode,"LEAD",eventType,1);
    }
}

package com.law.business.customer.dto;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CustomerCommandValidationTest
{
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void contactRequiresCustomerNameMobileAndRelation()
    {
        CustomerContactCreateCommand command = new CustomerContactCreateCommand();

        assertTrue(fields(command).containsAll(Set.of("customerId", "contactName", "mobile", "relationType")));
    }

    @Test
    void contactUpdateRequiresContactId()
    {
        CustomerContactUpdateCommand command = new CustomerContactUpdateCommand();
        command.setContactName("张三");
        command.setMobile("13800000000");
        command.setRelationType("daily");

        assertEquals(Set.of("contactId"), fields(command));
    }

    @Test
    void followupRequiresCustomerTypeAndContent()
    {
        assertEquals(Set.of("customerId", "followType", "content"),
                fields(new CustomerFollowupCreateCommand()));
    }

    @Test
    void tagAndAssignmentRequireStableIdentifiers()
    {
        assertTrue(fields(new CustomerTagCreateCommand()).containsAll(Set.of("tagName", "status")));
        assertTrue(fields(new CustomerTagUpdateCommand()).containsAll(Set.of("tagId", "tagName", "status")));
        assertEquals(Set.of("customerId", "tagIds"), fields(new CustomerTagAssignCommand()));
    }

    @Test
    void mergeRejectsMissingCustomers()
    {
        assertEquals(Set.of("mainCustomerId", "mergedCustomerId"), fields(new CustomerMergeCommand()));
    }

    @Test
    void invalidContactEmailAndMobileAreRejected()
    {
        CustomerContactCreateCommand command = new CustomerContactCreateCommand();
        command.setCustomerId(1L);
        command.setContactName("张三");
        command.setRelationType("daily");
        command.setMobile("abc");
        command.setEmail("invalid");

        assertEquals(Set.of("mobile", "email"), fields(command));
    }

    private Set<String> fields(Object command)
    {
        return validator.validate(command).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(toSet());
    }
}

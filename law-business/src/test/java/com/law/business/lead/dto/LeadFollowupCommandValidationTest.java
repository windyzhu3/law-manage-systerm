package com.law.business.lead.dto;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class LeadFollowupCommandValidationTest
{
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequiresLeadTypeResultAndContent()
    {
        LeadFollowupCommand command = new LeadFollowupCommand();

        Set<String> fields = validator.validate(command).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(toSet());

        assertEquals(Set.of("leadId", "followType", "followResult", "content"), fields);
    }
}

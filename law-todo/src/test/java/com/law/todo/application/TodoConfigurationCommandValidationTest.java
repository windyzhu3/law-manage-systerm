package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.law.todo.application.command.TodoConfigurationCommands.DodRuleCommand;
import com.law.todo.application.command.TodoConfigurationCommands.SlaRuleCommand;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class TodoConfigurationCommandValidationTest
{
    private final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();

    @Test void slaThresholdsMustBeOrdered()
    {
        SlaRuleCommand command=new SlaRuleCommand(null,"SLA-A","首联","RESPONSE",30,"MINUTE",
                "DEFAULT","TODO_CREATED",100,80,150,"{}","{}","{}","0","a-1",0);

        assertEquals(Set.of("thresholdsOrdered"),violations(command));
    }

    @Test void dodRuleRequiresJsonObjectsOrArrays()
    {
        DodRuleCommand command=new DodRuleCommand(null,"DOD-A","首联","TASK",
                "not-json","[]","[]","[]","{}","0","a-2",0);

        assertEquals(Set.of("ruleJsonValid"),violations(command));
    }

    private Set<String> violations(Object command)
    {
        return validator.validate(command).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}

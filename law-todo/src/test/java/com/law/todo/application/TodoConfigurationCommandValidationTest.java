package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.DodRuleCommand;
import com.law.todo.application.command.TodoConfigurationCommands.RuleReference;
import com.law.todo.application.command.TodoConfigurationCommands.SlaRuleCommand;
import com.law.todo.application.command.TodoConfigurationCommands.TemplateDraftRuleCommand;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class TodoConfigurationCommandValidationTest
{
    private final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();

    @Test void slaThresholdsMustBeOrdered()
    {
        SlaRuleCommand command=sla(100,80,150,"{}","{}","{}","0");

        assertEquals(Set.of("thresholdsOrdered"),violations(command));
    }

    @Test void dodRuleRequiresJsonObjectsOrArrays()
    {
        DodRuleCommand command=dod("not-json","0");

        assertEquals(Set.of("ruleJsonValid"),violations(command));
    }

    @Test void ruleStatusesAreRequiredForWrites()
    {
        assertTrue(violations(sla(80,100,150,"{}","{}","{}",null)).contains("status"));
        assertTrue(violations(dod("[]",null)).contains("status"));
    }

    @Test void slaPoliciesMustBeValidJsonObjectsOrArrays()
    {
        assertTrue(violations(sla(80,100,150,"not-json","{}","[]","0")).contains("policyJsonValid"));
        assertFalse(violations(sla(80,100,150,"{}","[]","{}","0")).contains("policyJsonValid"));
    }

    @Test void ruleReferenceRejectsBlankAndInvalidJson()
    {
        assertTrue(violations(new RuleReference("SLA",1L,0," ")).contains("configJsonValid"));
        assertTrue(violations(new RuleReference("SLA",1L,0,"not-json")).contains("configJsonValid"));
        assertTrue(violations(new RuleReference("SLA",1L,0,"{}")).isEmpty());
    }

    @Test void draftRuleReferencesAreCascaded()
    {
        TemplateDraftRuleCommand command=new TemplateDraftRuleCommand("action",1L,0,
                List.of(new RuleReference("SLA",1L,0,"not-json")));

        assertTrue(violations(command).contains("ruleReferences[0].configJsonValid"));
    }

    @Test void simulationPayloadIsDeeplyImmutable()
    {
        Map<String,Object> nested=new LinkedHashMap<>(Map.of("value","before"));
        List<Object> values=new ArrayList<>(List.of(nested));
        Map<String,Object> payload=new LinkedHashMap<>(Map.of("values",values));
        ConfigurationSimulationCommand command=new ConfigurationSimulationCommand("request",1L,"TODO_CREATED","LEAD",1L,
                payload,LocalDateTime.of(2026,7,20,9,0),List.of());

        nested.put("value","after");
        values.add("new");
        payload.put("other","after");

        List<?> stored=(List<?>)command.payload().get("values");
        assertEquals(1,stored.size());
        assertEquals("before",((Map<?,?>)stored.get(0)).get("value"));
        assertThrows(UnsupportedOperationException.class,()->((Map<Object,Object>)stored.get(0)).put("x","y"));
    }

    @Test void templateDetailRuleReferencesAreDeeplyImmutable()
    {
        Map<String,Object> nested=new LinkedHashMap<>(Map.of("value","before"));
        Map<String,Object> reference=new LinkedHashMap<>(Map.of("nested",nested));
        TemplateConfigurationDetail detail=new TemplateConfigurationDetail(1L,"T","Template","LEAD",1,2L,"DRAFT",
                List.of(reference));

        nested.put("value","after");
        reference.put("other","after");

        Map<?,?> stored=detail.ruleReferences().get(0);
        assertEquals("before",((Map<?,?>)stored.get("nested")).get("value"));
        assertThrows(UnsupportedOperationException.class,()->((Map<Object,Object>)stored.get("nested")).put("x","y"));
    }

    @Test void validConfigurationCommandsHaveNoViolations()
    {
        assertTrue(violations(sla(80,100,150,"{}","[]","{}","0")).isEmpty());
        assertTrue(violations(dod("[]","1")).isEmpty());
    }

    private SlaRuleCommand sla(int soft,int hard,int escalate,String pause,String escalation,String action,String status)
    {
        return new SlaRuleCommand(null,"SLA-A","首联","RESPONSE",30,"MINUTE","DEFAULT","TODO_CREATED",
                soft,hard,escalate,pause,escalation,action,status,"a-1",0);
    }

    private DodRuleCommand dod(String requiredFields,String status)
    {
        return new DodRuleCommand(null,"DOD-A","首联","TASK",requiredFields,"[]","[]","[]","{}",status,"a-2",0);
    }

    private Set<String> violations(Object command)
    {
        return validator.validate(command).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}

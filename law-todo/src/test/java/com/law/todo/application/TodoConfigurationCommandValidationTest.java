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
import com.law.todo.application.command.TodoConfigurationCommands.JourneyPayloadCommand;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.DodRuleCommand;
import com.law.todo.application.command.TodoConfigurationCommands.RuleReference;
import com.law.todo.application.command.TodoConfigurationCommands.SlaRuleCommand;
import com.law.todo.application.command.TodoConfigurationCommands.TemplateDraftRuleCommand;
import com.law.todo.application.command.TodoDefinitionCommands.VirtualTaskCompletionSample;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.application.view.TodoConfigurationViews.TemplateRuleReference;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class TodoConfigurationCommandValidationTest
{
    private final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();

    @Test void simulationContractIncludesAnExplicitPositivePayloadVersion()
    {
        assertTrue(java.util.Arrays.stream(ConfigurationSimulationCommand.class.getRecordComponents())
                .anyMatch(component->component.getName().equals("payloadVersion")));
    }

    @Test void journeyCommandsAllowNegativeSamplesButRejectZeroBusinessIds()
    {
        JourneyPayloadCommand payload=new JourneyPayloadCommand(1L,2L,"LEAD_CREATED",1,"LEAD",-1001L,
                Map.of(),"hash");
        JourneySimulationCommand sample=new JourneySimulationCommand(1L,2L,"LEAD_CREATED",1,"LEAD",-1001L,
                Map.of(),LocalDateTime.of(2026,7,23,9,0),List.of(),"hash");
        JourneySimulationCommand zero=new JourneySimulationCommand(1L,2L,"LEAD_CREATED",1,"LEAD",0L,
                Map.of(),LocalDateTime.of(2026,7,23,9,0),List.of(),"hash");

        assertTrue(violations(payload).isEmpty());
        assertTrue(violations(sample).isEmpty());
        assertEquals(Set.of("businessIdValid"),violations(zero));
    }

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

    @Test void simulationTaskCompletionPayloadsAreDeeplyImmutable()
    {
        Map<String,Object> nested=new LinkedHashMap<>(Map.of("value","before"));
        List<Object> values=new ArrayList<>(List.of(nested));
        Map<String,Object> payload=new LinkedHashMap<>(Map.of("values",values));
        VirtualTaskCompletionSample completion=new VirtualTaskCompletionSample("task",0,payload,
                LocalDateTime.of(2026,7,20,9,0));
        ConfigurationSimulationCommand command=new ConfigurationSimulationCommand("request",1L,"TODO_CREATED","LEAD",1L,
                Map.of("event","created"),LocalDateTime.of(2026,7,20,9,0),List.of(completion));

        nested.put("value","after");
        values.add("new");

        List<?> stored=(List<?>)command.taskCompletions().get(0).payload().get("values");
        assertEquals(1,stored.size());
        assertEquals("before",((Map<?,?>)stored.get(0)).get("value"));
        assertThrows(UnsupportedOperationException.class,()->((Map<Object,Object>)stored.get(0)).put("x","y"));
    }

    @Test void simulationTaskCompletionNullPayloadRemainsCascadingValidationFailure()
    {
        VirtualTaskCompletionSample completion=new VirtualTaskCompletionSample("task",0,null,
                LocalDateTime.of(2026,7,20,9,0));
        ConfigurationSimulationCommand command=new ConfigurationSimulationCommand("request",1L,"TODO_CREATED","LEAD",1L,
                Map.of("event","created"),LocalDateTime.of(2026,7,20,9,0),List.of(completion));

        assertTrue(violations(command).contains("taskCompletions[0].payload"));
    }

    @Test void templateDetailRuleReferencesAreImmutableTypedProjections()
    {
        TemplateRuleReference reference=new TemplateRuleReference("DOD",2L,0,"DOD-A","A","0","{}");
        TemplateConfigurationDetail detail=new TemplateConfigurationDetail(1L,"T","Template","LEAD","0",0,1,
                2L,"DRAFT",null,null,null,List.of(reference));

        assertEquals("DOD",detail.ruleReferences().get(0).type());
        assertThrows(UnsupportedOperationException.class,()->detail.ruleReferences().add(reference));
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

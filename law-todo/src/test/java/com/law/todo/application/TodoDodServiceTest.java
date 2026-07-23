package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoBusinessValidator;

class TodoDodServiceTest
{
    private final Actor actor=new Actor(7L,"alice",2L);

    @Test void completeRunsOnlyValidatorsSelectedByTheDefinition()
    {
        CountingValidator selected=new CountingValidator("SELECTED");
        CountingValidator unselected=new CountingValidator("UNSELECTED");
        TodoDodService service=new TodoDodService(List.of(selected,unselected));

        service.validate(todo(),definition(Map.of("validatorRefs",List.of("SELECTED"))),
                "COMPLETE",Map.of(),List.of(),actor);

        assertEquals(1,selected.calls);
        assertEquals(0,unselected.calls);
    }

    @Test void explicitEmptyValidatorSelectionRunsNoBusinessValidator()
    {
        CountingValidator validator=new CountingValidator("VALIDATOR");
        TodoDodService service=new TodoDodService(List.of(validator));

        service.validate(todo(),definition(Map.of("validatorRefs",List.of())),
                "COMPLETE",Map.of(),List.of(),actor);

        assertEquals(0,validator.calls);
    }

    @Test void legacyDefinitionWithoutValidatorRefsKeepsExistingRunAllBehavior()
    {
        CountingValidator validator=new CountingValidator("VALIDATOR");
        TodoDodService service=new TodoDodService(List.of(validator));

        service.validate(todo(),definition(Map.of()),"COMPLETE",Map.of(),List.of(),actor);

        assertEquals(1,validator.calls);
    }

    @Test void canonicalConditionalRequirementIsEnforcedAtCompletion()
    {
        TodoDodService service=new TodoDodService(List.of());
        Map<String,Object> rule=Map.of(
                "field","contactResult",
                "when",Map.of("field","connected","equals",true));

        TodoException error=assertThrows(TodoException.class,()->service.validate(todo(),
                definition(Map.of("conditionalRequired",List.of(rule))),
                "COMPLETE",Map.of("connected",true),List.of(),actor));

        assertEquals("TODO_DOD_FIELD_MISSING",error.getBusinessCode());
    }

    private TodoInstance todo()
    {
        TodoInstance todo=new TodoInstance();
        todo.setBusinessType("LEAD");
        todo.setBusinessId(42L);
        return todo;
    }

    private TodoDefinitionDocument definition(Map<String,Object> dod)
    {
        return new TodoDefinitionDocument(1,"LEAD-FIRST-CONTACT",
                new EventRule("LEAD_ASSIGNED",1,Map.of()),
                new OwnerRule(Map.of()),new DodRule(dod),new SlaRule(Map.of()),
                new UiSchema(Map.of()),new RoutingGraph(Map.of()),
                List.of(),List.of(),List.of());
    }

    private static final class CountingValidator implements TodoBusinessValidator
    {
        private final String code;
        private int calls;
        private CountingValidator(String code){this.code=code;}
        @Override public void validate(TodoInstance todo,Map<String,Object> payload){calls++;}
        @Override public String catalogCode(){return code;}
    }
}

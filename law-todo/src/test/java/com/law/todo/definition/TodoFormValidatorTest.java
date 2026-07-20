package com.law.todo.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.law.todo.definition.validation.TodoFormValidator;
import com.law.todo.domain.TodoException;

class TodoFormValidatorTest
{
    private final TodoFormValidator validator = new TodoFormValidator();

    @Test void dodFieldMustExistInUiOrBeSystemDerived()
    {
        TodoDefinitionDocument definition = definition(
                Map.of("requiredFields", List.of("rendered", "missing"),
                        "systemDerivedFields", List.of("derived")),
                Map.of("fields", List.of("rendered")));

        assertEquals("TODO_DOD_FIELD_NOT_RENDERABLE",
                validator.validateDefinition(definition).get(0).code());
    }

    @Test void declaredSystemDerivedFieldSatisfiesParity()
    {
        TodoDefinitionDocument definition = definition(
                Map.of("requiredFields", List.of("derived"),
                        "systemDerivedFields", List.of("derived")),
                Map.of("fields", List.of()));

        assertEquals(List.of(), validator.validateDefinition(definition));
    }

    @Test void rejectionRequiresReasonButCompletionDoesNot()
    {
        TodoDefinitionDocument definition = definition(
                Map.of("actions", Map.of("REJECT", Map.of("requiredFields", List.of("reason")))),
                Map.of("fields", List.of(Map.of("key", "reason"))));

        TodoException error = assertThrows(TodoException.class,
                () -> validator.validateSubmission(definition, "REJECT", Map.of()));
        assertEquals("TODO_DOD_FIELD_MISSING", error.getBusinessCode());
        validator.validateSubmission(definition, "COMPLETE", Map.of());
    }

    @Test void missingAndPresentNullAreDifferentValidationFailures()
    {
        TodoDefinitionDocument definition = definition(
                Map.of("requiredFields", List.of("result")),
                Map.of("fields", List.of("result")));

        assertEquals("TODO_DOD_FIELD_MISSING", assertThrows(TodoException.class,
                () -> validator.validateSubmission(definition, "COMPLETE", Map.of()))
                .getBusinessCode());
        assertEquals("TODO_DOD_FIELD_NULL", assertThrows(TodoException.class,
                () -> validator.validateSubmission(definition, "COMPLETE",
                        java.util.Collections.singletonMap("result", null)))
                .getBusinessCode());
    }

    @Test void conditionalRequiredRuleUsesSubmittedValues()
    {
        TodoDefinitionDocument definition = definition(
                Map.of("conditionalRequired", List.of(Map.of("field", "reason",
                        "when", Map.of("field", "result", "equals", "REJECT")))),
                Map.of("fields", List.of("result", "reason")));

        TodoException error = assertThrows(TodoException.class,
                () -> validator.validateSubmission(definition, "COMPLETE",
                        Map.of("result", "REJECT")));
        assertEquals("TODO_DOD_FIELD_MISSING", error.getBusinessCode());
        validator.validateSubmission(definition, "COMPLETE", Map.of("result", "APPROVE"));
    }

    @Test void aggregateRequiredValidationReturnsEveryRequiredAndConditionalFailure()
    {
        List<TodoFormValidator.ValidationIssue> issues=validator.validateRequiredFields(Map.of(
                "requiredFields",List.of("result"),"conditionalRequired",List.of(
                        Map.of("field","reason","when",Map.of("field","result","equals","REJECT")),
                        Map.of("field","owner","when",Map.of("field","submitted","present",true)))),
                Map.of("result","REJECT","submitted",true));

        assertEquals(List.of("fields.reason","fields.owner"),issues.stream().map(TodoFormValidator.ValidationIssue::path).toList());
    }

    @Test void conditionalSourceFieldAlsoParticipatesInDefinitionParity()
    {
        TodoDefinitionDocument definition = definition(
                Map.of("conditionalRequired", List.of(Map.of("field", "reason",
                        "when", Map.of("field", "hiddenDecision", "equals", "REJECT")))),
                Map.of("fields", List.of("reason")));

        assertEquals("TODO_DOD_FIELD_NOT_RENDERABLE",
                validator.validateDefinition(definition).get(0).code());
    }

    @Test void uiRequiredFlagIsNotRuntimeAuthority()
    {
        TodoDefinitionDocument definition = definition(Map.of(),
                Map.of("fields", List.of(Map.of("key", "clientOnly", "required", true))));

        validator.validateSubmission(definition, "COMPLETE", Map.of());
    }

    @Test void materialRulesValidateTypeAndCountThroughResolver()
    {
        TodoDefinitionDocument definition = definition(
                Map.of("materials", List.of(Map.of("type", "PROOF", "minCount", 2))),
                Map.of("fields", List.of()));

        TodoException error = assertThrows(TodoException.class,
                () -> validator.validateSubmission(definition, "COMPLETE", Map.of(),
                        List.of(11L), ids -> List.of(new TodoFormValidator.Material(11L, "PROOF"))));
        assertEquals("TODO_DOD_MATERIAL_COUNT", error.getBusinessCode());
    }

    @Test void rejectsNonPositiveFileObjectIdsBeforeLookup()
    {
        TodoException error = assertThrows(TodoException.class,
                () -> validator.validateSubmission(definition(Map.of(), Map.of()), "COMPLETE",
                        Map.of(), List.of(0L), ids -> List.of()));
        assertEquals("TODO_FILE_OBJECT_ID_INVALID", error.getBusinessCode());
    }

    private TodoDefinitionDocument definition(Map<String,Object> dod, Map<String,Object> ui)
    {
        return new TodoDefinitionDocument(1, "T", null, null, new DodRule(dod), null,
                new UiSchema(ui), null, List.of(), List.of(), List.of());
    }
}

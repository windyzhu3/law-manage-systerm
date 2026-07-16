package com.law.todo.definition.compiler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.definition.validation.TodoFormValidator;
import com.law.todo.routing.RoutingGraphValidator;

@Component
public class TodoDefinitionCompiler
{
    private final TodoDefinitionCodec codec;
    private final TodoEventCatalogService eventCatalog;
    private final TodoDecisionService decisions;
    private final ConditionValidator conditionValidator;

    public TodoDefinitionCompiler(TodoEventCatalogService eventCatalog, TodoDecisionService decisions)
    {
        this(new TodoDefinitionCodec(), eventCatalog, decisions, new ConditionValidator());
    }

    @Autowired
    public TodoDefinitionCompiler(TodoEventCatalogService eventCatalog, TodoDecisionService decisions,
            ConditionValidator conditionValidator)
    {
        this(new TodoDefinitionCodec(), eventCatalog, decisions, conditionValidator);
    }

    public TodoDefinitionCompiler(TodoDefinitionCodec codec, TodoEventCatalogService eventCatalog,
            TodoDecisionService decisions)
    {
        this(codec, eventCatalog, decisions, new ConditionValidator());
    }

    public TodoDefinitionCompiler(TodoDefinitionCodec codec, TodoEventCatalogService eventCatalog,
            TodoDecisionService decisions, ConditionValidator conditionValidator)
    {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.eventCatalog = Objects.requireNonNull(eventCatalog, "eventCatalog");
        this.decisions = Objects.requireNonNull(decisions, "decisions");
        this.conditionValidator = Objects.requireNonNull(conditionValidator, "conditionValidator");
    }

    public DefinitionValidationReport compile(TodoDefinitionDocument definition)
    {
        return compile(definition, null);
    }

    public DefinitionValidationReport compile(TodoDefinitionDocument definition, CompilationContext context)
    {
        List<ValidationIssue> errors = new ArrayList<>();
        if (definition == null)
        {
            errors.add(issue("TODO_DEFINITION_REQUIRED", "$", "Definition document is required"));
            return new DefinitionValidationReport(errors, List.of(), null, null);
        }

        validateStructure(definition, errors);
        for (TodoFormValidator.ValidationIssue formIssue : new TodoFormValidator().validateDefinition(definition))
            errors.add(issue(formIssue.code(), formIssue.path(), formIssue.message()));
        if (definition.routing() != null && !definition.routing().config().isEmpty())
        {
            errors.addAll(new RoutingGraphValidator(conditionValidator).validate(definition.routing()));
            validateTaskReferences(definition, context, errors);
        }
        for (String code : decisions.unresolvedBlockingDecisions(definition.decisionRefs()))
            errors.add(issue("TODO_DECISION_UNRESOLVED", "decisionRefs",
                    "Decision is missing or unresolved: " + code));

        String compiledJson = codec.canonicalJson(definition);
        return new DefinitionValidationReport(errors, List.of(), compiledJson, sha256(compiledJson));
    }

    private void validateTaskReferences(TodoDefinitionDocument definition, CompilationContext context,
            List<ValidationIssue> errors)
    {
        Object rawNodes = definition.routing().config().get("nodes");
        if (!(rawNodes instanceof List<?> nodes))
            return;
        boolean hasTask = nodes.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(node -> "TASK".equals(String.valueOf(node.get("type"))));
        if (!hasTask)
            return;
        if (context == null)
        {
            errors.add(issue("TODO_ROUTE_TASK_COMPILATION_CONTEXT_REQUIRED", "routing.nodes",
                    "TASK reference validation requires an explicit compilation context"));
            return;
        }
        String start = String.valueOf(definition.routing().config().get("start"));
        Map<Long, TemplateVersion> resolved = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++)
        {
            if (!(nodes.get(index) instanceof Map<?, ?> node)
                    || !"TASK".equals(String.valueOf(node.get("type"))))
                continue;
            Long versionId = longValue(node.get("templateVersionId"));
            if (versionId == null)
                continue;
            TemplateVersion version = resolved.containsKey(versionId) ? resolved.get(versionId)
                    : context.resolver().resolve(versionId);
            resolved.putIfAbsent(versionId, version);
            String path = "routing.nodes[" + index + "].templateVersionId";
            boolean startTask = start.equals(String.valueOf(node.get("key")));
            if (startTask && !versionId.equals(context.currentVersionId()))
            {
                errors.add(issue("TODO_ROUTE_START_TASK_VERSION_INVALID", path,
                        "Start TASK must reference the definition version being compiled"));
                continue;
            }
            if (version == null)
            {
                errors.add(issue("TODO_ROUTE_TASK_VERSION_NOT_FOUND", path, "TASK template version does not exist"));
                continue;
            }
            boolean guardedCurrentStart = startTask && versionId.equals(context.currentVersionId())
                    && context.guardedPublishPreflight() && "DRAFT".equals(version.status());
            if (!"PUBLISHED".equals(version.status()) && !guardedCurrentStart)
                errors.add(issue("TODO_ROUTE_TASK_VERSION_NOT_PUBLISHED", path,
                        "TASK template version must be published"));
        }
    }

    private static Long longValue(Object value)
    {
        try
        {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException invalid)
        {
            return null;
        }
    }

    @FunctionalInterface
    public interface TemplateVersionResolver
    {
        TemplateVersion resolve(long versionId);
    }

    public record TemplateVersion(long versionId, String status) { }

    public record CompilationContext(long currentVersionId, boolean guardedPublishPreflight,
            TemplateVersionResolver resolver)
    {
        public CompilationContext
        {
            Objects.requireNonNull(resolver, "resolver");
        }
    }

    private void validateStructure(TodoDefinitionDocument definition, List<ValidationIssue> errors)
    {
        if (definition.schemaVersion() != 1)
            errors.add(issue("TODO_DEFINITION_SCHEMA_UNSUPPORTED", "schemaVersion",
                    "Only definition schema version 1 is supported"));
        if (blank(definition.templateCode()))
            errors.add(issue("TODO_TEMPLATE_CODE_REQUIRED", "templateCode", "Template code is required"));
        if (definition.event() == null)
            errors.add(issue("TODO_EVENT_REQUIRED", "event", "Event rule is required"));
        else
        {
            if (blank(definition.event().eventType()))
                errors.add(issue("TODO_EVENT_TYPE_REQUIRED", "event.eventType", "Event type is required"));
            if (definition.event().payloadVersion() <= 0)
                errors.add(issue("TODO_EVENT_PAYLOAD_VERSION_INVALID", "event.payloadVersion",
                        "Payload version must be positive"));
            if (!blank(definition.event().eventType()) && definition.event().payloadVersion() > 0)
            {
                String payloadSchema = eventCatalog.payloadSchema(definition.event().eventType(),
                        definition.event().payloadVersion());
                if (payloadSchema == null)
                    errors.add(issue("TODO_EVENT_CATALOG_NOT_FOUND", "event",
                            "Event payload schema is not registered"));
                else
                    validateCondition(definition.event(), payloadSchema, errors);
            }
        }
        requireSection(definition.owner(), "owner", errors);
        requireSection(definition.dod(), "dod", errors);
        requireSection(definition.sla(), "sla", errors);
        requireSection(definition.ui(), "ui", errors);
        requireSection(definition.routing(), "routing", errors);
    }

    private void validateCondition(TodoDefinitionDocument.EventRule event, String payloadSchema,
            List<ValidationIssue> errors)
    {
        if (event.condition().isEmpty())
            return;
        errors.addAll(conditionValidator.validate(event.condition(), payloadSchema, false).issues());
    }

    private static void requireSection(Object section, String path, List<ValidationIssue> errors)
    {
        if (section == null)
            errors.add(issue("TODO_DEFINITION_SECTION_REQUIRED", path,
                    "Definition section is required: " + path));
    }

    private static ValidationIssue issue(String code, String path, String message)
    {
        return new ValidationIssue(code, path, message);
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}

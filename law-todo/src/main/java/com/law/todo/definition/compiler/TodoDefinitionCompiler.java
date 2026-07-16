package com.law.todo.definition.compiler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.definition.model.TodoDefinitionDocument;

@Component
public class TodoDefinitionCompiler
{
    private final TodoDefinitionCodec codec;
    private final TodoEventCatalogService eventCatalog;
    private final TodoDecisionService decisions;

    @Autowired
    public TodoDefinitionCompiler(TodoEventCatalogService eventCatalog, TodoDecisionService decisions)
    {
        this(new TodoDefinitionCodec(), eventCatalog, decisions);
    }

    public TodoDefinitionCompiler(TodoDefinitionCodec codec, TodoEventCatalogService eventCatalog,
            TodoDecisionService decisions)
    {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.eventCatalog = Objects.requireNonNull(eventCatalog, "eventCatalog");
        this.decisions = Objects.requireNonNull(decisions, "decisions");
    }

    public DefinitionValidationReport compile(TodoDefinitionDocument definition)
    {
        List<ValidationIssue> errors = new ArrayList<>();
        if (definition == null)
        {
            errors.add(issue("TODO_DEFINITION_REQUIRED", "$", "Definition document is required"));
            return new DefinitionValidationReport(errors, List.of(), null, null);
        }

        validateStructure(definition, errors);
        for (String code : decisions.unresolvedBlockingDecisions(definition.decisionRefs()))
            errors.add(issue("TODO_DECISION_UNRESOLVED", "decisionRefs",
                    "Decision is missing or unresolved: " + code));

        String compiledJson = codec.canonicalJson(definition);
        return new DefinitionValidationReport(errors, List.of(), compiledJson, sha256(compiledJson));
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
            if (!blank(definition.event().eventType()) && definition.event().payloadVersion() > 0
                    && eventCatalog.payloadSchema(definition.event().eventType(),
                            definition.event().payloadVersion()) == null)
                errors.add(issue("TODO_EVENT_CATALOG_NOT_FOUND", "event",
                        "Event payload schema is not registered"));
        }
        requireSection(definition.owner(), "owner", errors);
        requireSection(definition.dod(), "dod", errors);
        requireSection(definition.sla(), "sla", errors);
        requireSection(definition.ui(), "ui", errors);
        requireSection(definition.routing(), "routing", errors);
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

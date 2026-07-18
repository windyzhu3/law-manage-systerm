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
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;

@Component
public class TodoDefinitionCompiler
{
    private final TodoDefinitionCodec codec;
    private final TodoEventCatalogService eventCatalog;
    private final TodoDecisionService decisions;
    private final ConditionValidator conditionValidator;
    private final TodoAutoActionCapabilityRegistry autoActions;

    public TodoDefinitionCompiler(TodoEventCatalogService eventCatalog, TodoDecisionService decisions)
    {
        this(new TodoDefinitionCodec(), eventCatalog, decisions, new ConditionValidator(),new TodoAutoActionCapabilityRegistry(List.of()));
    }

    @Autowired
    public TodoDefinitionCompiler(TodoEventCatalogService eventCatalog, TodoDecisionService decisions,
            ConditionValidator conditionValidator,TodoAutoActionCapabilityRegistry autoActions)
    {
        this(new TodoDefinitionCodec(), eventCatalog, decisions, conditionValidator,autoActions);
    }

    public TodoDefinitionCompiler(TodoDefinitionCodec codec, TodoEventCatalogService eventCatalog,
            TodoDecisionService decisions)
    {
        this(codec, eventCatalog, decisions, new ConditionValidator(),new TodoAutoActionCapabilityRegistry(List.of()));
    }

    public TodoDefinitionCompiler(TodoDefinitionCodec codec, TodoEventCatalogService eventCatalog,
            TodoDecisionService decisions, ConditionValidator conditionValidator)
    {this(codec,eventCatalog,decisions,conditionValidator,new TodoAutoActionCapabilityRegistry(List.of()));}
    public TodoDefinitionCompiler(TodoDefinitionCodec codec, TodoEventCatalogService eventCatalog,
            TodoDecisionService decisions, ConditionValidator conditionValidator,TodoAutoActionCapabilityRegistry autoActions)
    {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.eventCatalog = Objects.requireNonNull(eventCatalog, "eventCatalog");
        this.decisions = Objects.requireNonNull(decisions, "decisions");
        this.conditionValidator = Objects.requireNonNull(conditionValidator, "conditionValidator");
        this.autoActions=Objects.requireNonNull(autoActions,"autoActions");
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
        validateAutoActions(definition, errors);
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

    private void validateAutoActions(TodoDefinitionDocument definition,List<ValidationIssue> errors)
    {
        java.util.Set<String> keys=new java.util.HashSet<>();
        for(int index=0;index<definition.autoActions().size();index++)
        {
            Map<String,Object> config=definition.autoActions().get(index).config();String path="autoActions["+index+"]";
            String type=text(config.containsKey("actionType")?config.get("actionType"):config.get("action"));
            TodoAutoActionCapability registered=autoActions.capability(type);if(registered==null)errors.add(issue("TODO_AUTO_ACTION_NOT_ALLOWED",path+".actionType","Auto action capability is not registered"));
            String key=text(config.get("ruleKey"));if(blank(key))errors.add(issue("TODO_AUTO_ACTION_RULE_KEY_REQUIRED",path+".ruleKey","ruleKey is required"));else if(key.length()>96)errors.add(issue("TODO_AUTO_ACTION_RULE_KEY_INVALID",path+".ruleKey","ruleKey is limited to 96 characters"));else if(!keys.add(key))errors.add(issue("TODO_AUTO_ACTION_RULE_KEY_DUPLICATE",path+".ruleKey","ruleKey must be unique"));
            String capability=text(config.get("capability"));if(capability==null||!capability.equals(type))errors.add(issue("TODO_AUTO_ACTION_CAPABILITY_MISMATCH",path+".capability","Capability is required and must match actionType"));
            if(registered!=null)for(TodoAutoActionCapability.ValidationError validation:registered.descriptor().validate(config))errors.add(issue(validation.code(),path+"."+validation.field(),validation.message()));
            if(config.get("precondition")!=null)
            {
                try{if(!(config.get("precondition") instanceof Map<?,?> condition))throw new IllegalArgumentException("precondition must be an object");Map<String,Object> canonical=new HashMap<>();for(Map.Entry<?,?> entry:condition.entrySet()){if(!(entry.getKey() instanceof String conditionKey))throw new IllegalArgumentException("precondition keys must be strings");canonical.put(conditionKey,entry.getValue());}conditionValidator.decodeCanonical(canonical);}
                catch(IllegalArgumentException invalid){errors.add(issue("TODO_AUTO_ACTION_PRECONDITION_INVALID",path+".precondition",invalid.getMessage()));}
            }
        }
    }
    private String text(Object value){return value==null?null:String.valueOf(value);}

    private void validateTaskReferences(TodoDefinitionDocument definition, CompilationContext context,
            List<ValidationIssue> errors)
    {
        Object rawNodes = definition.routing().config().get("nodes");
        if (!(rawNodes instanceof List<?> nodes))
            return;
        String start = String.valueOf(definition.routing().config().get("start"));
        Map<?, ?> startNode = nodes.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .filter(node -> start.equals(String.valueOf(node.get("key")))).findFirst().orElse(null);
        if (startNode != null && !"TASK".equals(String.valueOf(startNode.get("type"))))
            errors.add(issue("TODO_ROUTE_START_TASK_REQUIRED", "routing.start",
                    "Routing start must be a TASK owned by the definition being compiled"));
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

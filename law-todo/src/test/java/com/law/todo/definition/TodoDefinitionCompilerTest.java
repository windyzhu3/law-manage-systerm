package com.law.todo.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.compiler.TodoDefinitionCompiler.CompilationContext;
import com.law.todo.definition.compiler.TodoDefinitionCompiler.TemplateVersion;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;

@ExtendWith(MockitoExtension.class)
class TodoDefinitionCompilerTest
{
    @Test void compilerRejectsUnknownOrUndeclaredAutoActionCapability()
    {
        TodoDefinitionDocument source=valid();
        TodoDefinitionDocument invalid=new TodoDefinitionDocument(source.schemaVersion(),source.templateCode(),source.event(),source.owner(),source.dod(),source.sla(),source.ui(),source.routing(),List.of(new AutoActionRule(Map.of("ruleKey","unsafe","actionType","SCRIPT","triggerAt","DUE"))),source.decisionRefs(),source.acceptanceRefs());
        var codes=compiler.compile(invalid).errors().stream().map(issue->issue.code()).toList();
        assertTrue(codes.contains("TODO_AUTO_ACTION_NOT_ALLOWED"));assertTrue(codes.contains("TODO_AUTO_ACTION_CAPABILITY_MISMATCH"));
    }
    @Test void compilerRejectsOversizedRuleKeyAndNonPositiveTransferOwner()
    {
        TodoDefinitionDocument source=valid();
        TodoDefinitionDocument invalid=new TodoDefinitionDocument(source.schemaVersion(),source.templateCode(),source.event(),source.owner(),source.dod(),source.sla(),source.ui(),source.routing(),List.of(
                new AutoActionRule(Map.of("ruleKey","x".repeat(97),"actionType","COMPLETE_DEFAULT","capability","COMPLETE_DEFAULT","triggerAt","DUE")),
                new AutoActionRule(Map.of("ruleKey","transfer","actionType","TRANSFER","capability","TRANSFER","triggerAt","SLA_100","targetOwnerId",0))),source.decisionRefs(),source.acceptanceRefs());
        var codes=compiler.compile(invalid).errors().stream().map(issue->issue.code()).toList();
        assertTrue(codes.contains("TODO_AUTO_ACTION_RULE_KEY_INVALID"));assertTrue(codes.contains("TODO_AUTO_ACTION_TRANSFER_OWNER_INVALID"));
    }
    @Test void compilerRejectsDodFieldThatRuntimeFormCannotRender()
    {
        TodoDefinitionDocument source=valid();
        TodoDefinitionDocument mismatched=new TodoDefinitionDocument(source.schemaVersion(),source.templateCode(),source.event(),source.owner(),
                new DodRule(Map.of("requiredFields",List.of("notInUi"))),source.sla(),source.ui(),source.routing(),source.autoActions(),source.decisionRefs(),source.acceptanceRefs());

        assertEquals("TODO_DOD_FIELD_NOT_RENDERABLE",compiler.compile(mismatched).errors().get(0).code());
    }
    @Test void compilerRejectsUnboundedCanonicalRoutingLoop()
    {
        TodoDefinitionDocument source=valid();
        RoutingGraph routing=new RoutingGraph(Map.of("start","loop",
                "nodes",List.of(Map.of("key","loop","type","LOOP"),Map.of("key","end","type","END")),
                "edges",List.of(Map.of("key","body","from","loop","to","end","branchKey","BODY"))));
        TodoDefinitionDocument invalid=new TodoDefinitionDocument(source.schemaVersion(),source.templateCode(),source.event(),source.owner(),
                source.dod(),source.sla(),source.ui(),routing,source.autoActions(),source.decisionRefs(),source.acceptanceRefs());

        assertTrue(compiler.compile(invalid).errors().stream().anyMatch(issue->"TODO_ROUTE_LOOP_UNBOUNDED".equals(issue.code())));
    }
    @Test void preflightRejectsMissingDownstreamTaskVersion()
    {
        TodoDefinitionDocument definition=withRouting(route(9L,22L));
        CompilationContext context=new CompilationContext(9L,true,id->id==9L?new TemplateVersion(9L,"DRAFT"):null);

        assertTrue(compiler.compile(definition,context).errors().stream().anyMatch(issue->"TODO_ROUTE_TASK_VERSION_NOT_FOUND".equals(issue.code())));
    }

    @Test void preflightRejectsDraftDownstreamTaskVersion()
    {
        TodoDefinitionDocument definition=withRouting(route(9L,22L));
        CompilationContext context=new CompilationContext(9L,true,id->new TemplateVersion(id,"DRAFT"));

        assertTrue(compiler.compile(definition,context).errors().stream().anyMatch(issue->"TODO_ROUTE_TASK_VERSION_NOT_PUBLISHED".equals(issue.code())));
    }

    @Test void preflightRejectsStartTaskPointingAtAnotherDraft()
    {
        TodoDefinitionDocument definition=withRouting(route(10L,null));
        CompilationContext context=new CompilationContext(9L,true,id->new TemplateVersion(id,"DRAFT"));

        assertTrue(compiler.compile(definition,context).errors().stream().anyMatch(issue->"TODO_ROUTE_START_TASK_VERSION_INVALID".equals(issue.code())));
    }

    @Test void currentDraftStartIsAllowedOnlyInGuardedPublishPreflight()
    {
        TodoDefinitionDocument definition=withRouting(route(9L,null));
        CompilationContext guarded=new CompilationContext(9L,true,id->new TemplateVersion(id,"DRAFT"));
        CompilationContext ordinary=new CompilationContext(9L,false,id->new TemplateVersion(id,"DRAFT"));

        assertTrue(compiler.compile(definition,guarded).errors().stream().noneMatch(issue->issue.code().startsWith("TODO_ROUTE_TASK_VERSION")||issue.code().startsWith("TODO_ROUTE_START_TASK")));
        assertTrue(compiler.compile(definition,ordinary).errors().stream().anyMatch(issue->"TODO_ROUTE_TASK_VERSION_NOT_PUBLISHED".equals(issue.code())));
    }

    @Test void preflightRejectsEveryNonTaskStartType()
    {
        for (String type : List.of("DECISION","FORK","LOOP","END"))
        {
            TodoDefinitionDocument definition=withRouting(nonTaskStartRoute(type));
            CompilationContext context=new CompilationContext(9L,true,id->new TemplateVersion(id,"PUBLISHED"));

            assertTrue(compiler.compile(definition,context).errors().stream()
                    .anyMatch(issue->"TODO_ROUTE_START_TASK_REQUIRED".equals(issue.code())),type);
        }
    }
    @Mock TodoMapper mapper;
    private TodoDefinitionCompiler compiler;

    @BeforeEach
    void setUp()
    {
        lenient().when(mapper.selectEventCatalog("LEAD_CREATED", 1)).thenReturn(Map.of(
                "event_type", "LEAD_CREATED", "payload_version", 1,
                "payload_schema_json", "{\"type\":\"object\"}", "status", "ACTIVE"));
        TodoAutoActionCapability complete=capability("COMPLETE_DEFAULT",List.of());
        TodoAutoActionCapability transfer=capability("TRANSFER",List.of(new TodoAutoActionCapability.Field("targetOwnerId","number",true,1,null,"Target owner","TODO_AUTO_ACTION_TRANSFER_OWNER_INVALID")));
        compiler = new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(mapper),
                new TodoDecisionService(mapper),new ConditionValidator(),new TodoAutoActionCapabilityRegistry(List.of(complete,transfer)));
    }

    @Test
    void unresolvedBlockingDecisionPreventsPublish()
    {
        when(mapper.selectDecisionByCode("Q-001")).thenReturn(
                Map.of("decision_code", "Q-001", "status", "OPEN", "blocking", "Y"));

        DefinitionValidationReport report = compiler.compile(definitionWithDecision("Q-001"));

        assertTrue(report.errors().stream()
                .anyMatch(error -> error.code().equals("TODO_DECISION_UNRESOLVED")));
        assertTrue(!report.publishable());
    }

    @Test
    void compiledHashIsRepeatable()
    {
        assertEquals(compiler.compile(valid()).definitionHash(),
                compiler.compile(valid()).definitionHash());
    }

    @Test
    void unresolvedNonBlockingDecisionProducesAReviewWarning()
    {
        when(mapper.selectDecisionByCode("Q-ADVISORY")).thenReturn(
                Map.of("decision_code", "Q-ADVISORY", "status", "OPEN", "blocking", "N"));

        DefinitionValidationReport report = compiler.compile(definitionWithDecision("Q-ADVISORY"));

        assertTrue(report.errors().isEmpty());
        assertTrue(report.warnings().stream()
                .anyMatch(warning -> warning.code().equals("TODO_DECISION_REVIEW_REQUIRED")));
    }

    @Test
    void missingEventCatalogEntryIsAStructuralError()
    {
        TodoDefinitionDocument definition = definition("UNKNOWN_EVENT", List.of());

        DefinitionValidationReport report = compiler.compile(definition);

        assertTrue(report.errors().stream()
                .anyMatch(error -> error.code().equals("TODO_EVENT_CATALOG_NOT_FOUND")));
    }

    @Test
    void onlyExactActiveEventCatalogEntriesCompile()
    {
        for (String status : List.of("DRAFT", "DISABLED", "RETIRED", "INVALID", "active"))
        {
            when(mapper.selectEventCatalog("LEAD_CREATED", 1)).thenReturn(Map.of(
                    "event_type", "LEAD_CREATED", "payload_version", 1,
                    "payload_schema_json", "{\"type\":\"object\"}", "status", status));

            DefinitionValidationReport report = compiler.compile(valid());

            assertTrue(report.errors().stream()
                    .anyMatch(error -> error.code().equals("TODO_EVENT_CATALOG_NOT_FOUND")), status);
            assertNull(new TodoEventCatalogService(mapper).payloadSchema("LEAD_CREATED", 1), status);
        }
    }

    @Test
    void catalogsExposePayloadSchemaAndBlockingDecisionState()
    {
        TodoEventCatalogService events = new TodoEventCatalogService(mapper);
        TodoDecisionService decisions = new TodoDecisionService(mapper);
        when(mapper.selectDecisionByCode("Q-002")).thenReturn(
                Map.of("decision_code", "Q-002", "status", "RESOLVED", "blocking", "Y"));

        assertEquals("{\"type\":\"object\"}", events.payloadSchema("LEAD_CREATED", 1));
        assertEquals(List.of("Q-MISSING"),
                decisions.unresolvedBlockingDecisions(List.of("Q-002", "Q-MISSING")));
    }

    @Test
    void preflightRejectsConditionFieldsMissingFromTheEventSchema()
    {
        when(mapper.selectEventCatalog("LEAD_CREATED", 1)).thenReturn(Map.of(
                "event_type", "LEAD_CREATED", "payload_version", 1,
                "payload_schema_json", "{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}",
                "status", "ACTIVE"));
        TodoDefinitionDocument definition = definition("LEAD_CREATED", List.of(),
                Map.of("$expression", Map.of("version", 1, "root",
                        Map.of("field", "class.classLoader", "operator", "EQ", "value", "x"))));

        DefinitionValidationReport report = compiler.compile(definition);

        assertTrue(report.errors().stream()
                .anyMatch(error -> error.code().equals("TODO_CONDITION_FIELD_UNKNOWN")));
    }

    @Test
    void objectLiteralWithJsonNullProducesAValidationIssueInsteadOfThrowing()
    {
        when(mapper.selectEventCatalog("LEAD_CREATED", 1)).thenReturn(Map.of(
                "event_type", "LEAD_CREATED", "payload_version", 1,
                "payload_schema_json", "{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}",
                "status", "ACTIVE"));
        Map<String,Object> objectLiteral = new java.util.LinkedHashMap<>();
        objectLiteral.put("optional", null);
        TodoDefinitionDocument definition = definition("LEAD_CREATED", List.of(),
                Map.of("$expression", Map.of("version", 1, "root",
                        Map.of("field", "amount", "operator", "EQ", "value", objectLiteral))));

        DefinitionValidationReport report = assertDoesNotThrow(() -> compiler.compile(definition));

        assertTrue(report.errors().stream()
                .anyMatch(error -> error.code().equals("TODO_CONDITION_VALUE_TYPE_INVALID")));
    }

    @Test
    void springConstructorIsExplicitWhenCompilerHasMultipleConstructors() throws Exception
    {
        assertTrue(TodoDefinitionCompiler.class
                .getConstructor(TodoEventCatalogService.class,TodoDecisionService.class,
                        ConditionValidator.class,TodoAutoActionCapabilityRegistry.class)
                .isAnnotationPresent(Autowired.class));
    }

    private TodoDefinitionDocument valid()
    {
        return definition("LEAD_CREATED", List.of());
    }
    private TodoAutoActionCapability capability(String type,List<TodoAutoActionCapability.Field> required)
    {
        return new TodoAutoActionCapability(){public String actionType(){return type;}public Descriptor descriptor(){return new Descriptor(type,List.of("DUE","SLA_80","SLA_100","SLA_150"),Descriptor.commonRetryFields(),required);}public AutoActionResult execute(com.law.todo.domain.model.TodoInstance todo,AutoActionRule rule,com.law.todo.application.command.TodoActionCommands.Actor actor){return AutoActionResult.success();}};
    }

    private TodoDefinitionDocument withRouting(RoutingGraph routing)
    {
        TodoDefinitionDocument source=valid();return new TodoDefinitionDocument(source.schemaVersion(),source.templateCode(),source.event(),source.owner(),source.dod(),source.sla(),source.ui(),routing,source.autoActions(),source.decisionRefs(),source.acceptanceRefs());
    }

    private RoutingGraph route(Long startVersion,Long downstreamVersion)
    {
        List<Map<String,Object>> nodes=new java.util.ArrayList<>();List<Map<String,Object>> edges=new java.util.ArrayList<>();
        nodes.add(Map.of("key","start","type","TASK","templateVersionId",startVersion));
        if(downstreamVersion!=null){nodes.add(Map.of("key","next","type","TASK","templateVersionId",downstreamVersion));edges.add(Map.of("key","start-next","from","start","to","next"));edges.add(Map.of("key","next-end","from","next","to","end"));}
        else edges.add(Map.of("key","start-end","from","start","to","end"));
        nodes.add(Map.of("key","end","type","END"));return new RoutingGraph(Map.of("start","start","nodes",nodes,"edges",edges));
    }

    private RoutingGraph nonTaskStartRoute(String type)
    {
        Map<String,Object> start=new java.util.LinkedHashMap<>();start.put("key","start");start.put("type",type);
        if("LOOP".equals(type))start.put("maxOccurrences",1);
        List<Map<String,Object>> nodes=new java.util.ArrayList<>();nodes.add(start);
        List<Map<String,Object>> edges=new java.util.ArrayList<>();
        if(!"END".equals(type))
        {
            nodes.add(Map.of("key","end","type","END"));
            Map<String,Object> edge=new java.util.LinkedHashMap<>();edge.put("key","start-end");edge.put("from","start");edge.put("to","end");
            if("DECISION".equals(type))edge.put("default",true);
            if("FORK".equals(type))edge.put("branchKey","branch");
            if("LOOP".equals(type))edge.put("branchKey","EXIT");
            edges.add(edge);
            if("LOOP".equals(type))
            {
                nodes.add(Map.of("key","body","type","END"));
                edges.add(Map.of("key","start-body","from","start","to","body","branchKey","BODY"));
            }
        }
        return new RoutingGraph(Map.of("start","start","nodes",nodes,"edges",edges));
    }

    private TodoDefinitionDocument definitionWithDecision(String decisionCode)
    {
        return definition("LEAD_CREATED", List.of(decisionCode));
    }

    private TodoDefinitionDocument definition(String eventType, List<String> decisions)
    {
        return definition(eventType, decisions, Map.of());
    }

    private TodoDefinitionDocument definition(String eventType, List<String> decisions,
            Map<String, Object> condition)
    {
        return new TodoDefinitionDocument(1, "TD-001",
                new EventRule(eventType, 1, condition),
                new OwnerRule(Map.of("type", "USER", "userId", 7)),
                new DodRule(Map.of("requiredFields", List.of("summary"))),
                new SlaRule(Map.of()), new UiSchema(Map.of("fields", List.of("summary"))),
                new RoutingGraph(Map.of()), List.of(), decisions, List.of("AC-001"));
    }
}

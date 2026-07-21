package com.law.todo.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.law.todo.definition.codec.LegacyDefinitionAdapter;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;

class TodoDefinitionCodecTest
{
    private final TodoDefinitionCodec codec = new TodoDefinitionCodec();
    private final LegacyDefinitionAdapter adapter = new LegacyDefinitionAdapter();

    @Test
    void canonicalJsonIsStable()
    {
        TodoDefinitionDocument value = fixture("TD-001");

        String canonical = codec.canonicalJson(value);

        assertEquals(canonical, codec.canonicalJson(codec.read(canonical)));
    }

    @Test
    void canonicalJsonSortsMapKeys()
    {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", 1);
        first.put("a", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 2);
        second.put("z", 1);

        assertEquals(codec.canonicalJson(fixture("TD-001", first)),
                codec.canonicalJson(fixture("TD-001", second)));
    }

    @Test
    void readsConfigurationCenterDraftWithEmptyRuleSections()
    {
        String json = """
                {
                  "schemaVersion": 1,
                  "templateCode": "E2E_TODO_CONFIG",
                  "event": {"eventType": "LEAD_ASSIGNED", "payloadVersion": 1, "condition": {}},
                  "owner": {"config": {"type": "BUSINESS_OWNER", "candidates": [], "cc": [], "skipUnavailable": true, "useDelegation": true}},
                  "dod": {"config": {"composition": "ALL", "systemDerivedFields": []}},
                  "sla": {"config": {}},
                  "ui": {"config": {"fields": ["contactResult"], "businessStage": "LEAD", "templateType": "STANDARD", "priority": "NORMAL", "description": ""}},
                  "routing": {"config": {"nodes": [], "edges": []}},
                  "autoActions": [],
                  "decisionRefs": [],
                  "acceptanceRefs": []
                }
                """;

        TodoDefinitionDocument definition = codec.read(json);

        assertEquals("E2E_TODO_CONFIG", definition.templateCode());
        assertEquals("BUSINESS_OWNER", definition.owner().config().get("type"));
        assertEquals(List.of("contactResult"), definition.ui().config().get("fields"));
    }

    @Test
    void nestedConfigurationCannotChangeCanonicalJsonAfterConstruction()
    {
        List<String> candidates = new ArrayList<>(List.of("alice"));
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("candidates", candidates);
        TodoDefinitionDocument definition = fixture("TD-001", Map.of("fallback", fallback));
        String canonical = codec.canonicalJson(definition);

        candidates.add("bob");
        fallback.put("type", "ROLE");

        assertEquals(canonical, codec.canonicalJson(definition));
    }

    @Test
    void legacyColumnsBecomeSchemaVersionOne()
    {
        TodoDefinitionDocument definition = adapter.fromLegacy(legacyRow());

        assertEquals(1, definition.schemaVersion());
        assertEquals("TD-LEGACY", definition.templateCode());
        assertEquals("USER", definition.owner().config().get("type"));
        assertEquals(List.of("summary"), definition.dod().config().get("requiredFields"));
        assertEquals("DEFAULT", definition.sla().config().get("calendarCode"));
        assertEquals("form", definition.ui().config().get("type"));
        assertEquals(2, definition.routing().config().get("templateVersionId"));
    }

    @Test
    void legacyScalarOwnerRulesBecomeTypeAndOperand()
    {
        assertLegacyOwner("\"PAYLOAD:toOwnerId\"", "PAYLOAD", "toOwnerId");
        assertLegacyOwner("\"PAYLOAD:mainLawyerId\"", "PAYLOAD", "mainLawyerId");
        assertLegacyOwner("\"ROLE:7\"", "ROLE", "7");
        assertLegacyOwner("\"USER:8\"", "USER", "8");
        assertLegacyOwner("\"DEPT:9\"", "DEPT", "9");
        assertLegacyOwner("\"POST:10\"", "POST", "10");
    }

    @Test
    void nestedArraysCannotChangeCanonicalJsonAfterConstruction()
    {
        String[] candidates = { "alice" };
        int[] levels = { 1, 2 };
        TodoDefinitionDocument definition = fixture("TD-001",
                Map.of("candidates", candidates, "levels", levels));
        String canonical = codec.canonicalJson(definition);

        candidates[0] = "bob";
        levels[0] = 99;

        assertEquals(canonical, codec.canonicalJson(definition));
    }

    @Test
    void persistenceStoresTheVersionedDocumentAndCompilationArtifacts() throws Exception
    {
        String mapper = Files.readString(Path.of("src", "main", "resources", "mapper", "todo",
                "TodoMapper.xml"));
        Path migrationPath = Path.of("..", "ruoyi-admin", "src", "main", "resources", "db", "migration",
                "V0_20_1__todo_definition_document.sql");
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(migrationPath), migrationPath.toString());
        String migration = Files.readString(migrationPath).toLowerCase();

        for (String column : List.of("definition_schema_version", "definition_json", "compiled_json",
                "definition_hash", "validation_report_json", "published_by", "published_time"))
        {
            org.junit.jupiter.api.Assertions.assertTrue(migration.contains(column), column);
            org.junit.jupiter.api.Assertions.assertTrue(mapper.contains(column), column);
        }
        org.junit.jupiter.api.Assertions.assertTrue(migration.contains("idx_todo_definition_status"));
        org.junit.jupiter.api.Assertions.assertTrue(migration.contains("idx_todo_definition_hash"));
        org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("updateDefinitionDocument"));
        org.junit.jupiter.api.Assertions.assertTrue(
                mapper.contains("definition_json &lt;=&gt; cast(#{expectedDefinitionJson} as json)"),
                "draft optimistic locking must compare JSON values instead of JSON and VARCHAR values");
        for (String sourceColumn : List.of("definition_json:sourceDefinitionJson",
                "owner_rule_json:sourceOwnerRuleJson", "dod_rule_json:sourceDodRuleJson",
                "sla_rule_json:sourceSlaRuleJson", "next_rule_json:sourceNextRuleJson",
                "ui_schema_json:sourceUiSchemaJson"))
        {
            String[] parts = sourceColumn.split(":");
            org.junit.jupiter.api.Assertions.assertTrue(
                    mapper.contains(parts[0] + " &lt;=&gt; cast(#{" + parts[1] + "} as json)"),
                    parts[0] + " compilation guard must compare JSON values");
        }
    }

    private TodoDefinitionDocument fixture(String templateCode)
    {
        return fixture(templateCode, Map.of("source", "payload.ownerId", "type", "PAYLOAD"));
    }

    private TodoDefinitionDocument fixture(String templateCode, Map<String, Object> owner)
    {
        return new TodoDefinitionDocument(1, templateCode,
                new EventRule("LEAD_CREATED", 1, Map.of("stage", "QUALIFIED")),
                new OwnerRule(owner),
                new DodRule(Map.of("requiredFields", List.of("summary"))),
                new SlaRule(Map.of("calendarCode", "DEFAULT", "minutes", 60)),
                new UiSchema(Map.of("type", "form", "fields", List.of("summary"))),
                new RoutingGraph(Map.of("start", "review")),
                List.of(new AutoActionRule(Map.of("action", "REMIND", "threshold", 80))),
                List.of("Q-001"), List.of("AC-001"));
    }

    private Map<String, Object> legacyRow()
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("template_code", "TD-LEGACY");
        row.put("owner_rule_json", "{\"type\":\"USER\",\"userId\":7}");
        row.put("dod_rule_json", "{\"requiredFields\":[\"summary\"]}");
        row.put("sla_rule_json", "{\"calendarCode\":\"DEFAULT\",\"minutes\":60}");
        row.put("next_rule_json", "{\"templateVersionId\":2}");
        row.put("ui_schema_json", "{\"type\":\"form\"}");
        return row;
    }

    private void assertLegacyOwner(String json, String type, String operand)
    {
        TodoDefinitionDocument definition = adapter.fromLegacy(
                Map.of("template_code", "TD-LEGACY", "owner_rule_json", json));
        assertEquals(type, definition.owner().config().get("type"));
        assertEquals(operand, definition.owner().config().get("operand"));
    }
}

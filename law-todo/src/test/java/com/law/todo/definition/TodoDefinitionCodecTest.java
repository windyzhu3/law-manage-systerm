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
}

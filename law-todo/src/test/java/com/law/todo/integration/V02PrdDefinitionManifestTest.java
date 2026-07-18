package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;

class V02PrdDefinitionManifestTest
{
    private static final String RESOURCE_ROOT = "/todo-definitions/v0.2/";
    private static final List<String> EXPECTED_CODES = IntStream.rangeClosed(1, 25)
            .mapToObj(index -> "TD-%03d".formatted(index)).toList();
    private static JSONObject manifest;
    private static List<JSONObject> envelopes;
    private static Path repositoryRoot;
    private static String migrationSql;
    private static final Map<String, String> DECISIONS = new LinkedHashMap<>();

    static
    {
        DECISIONS.put("Q-001", "案管分类标签是否由案管单人确认，还是需案管主管复核？");
        DECISIONS.put("Q-002", "非诉业务是否完全不进入档案管理员归档？是否仍需电子材料留存？");
        DECISIONS.put("Q-003", "执行案件中“一级助理/二级助理/执行部长”的组织角色是否新增？与原一级律师/二级律师/律师助理如何对应？");
        DECISIONS.put("Q-004", "合伙人律师分配与律师助理初评是否并存？如并存，前后顺序是什么？");
        DECISIONS.put("Q-005", "拒接理由①由一级律师审核后，是否仍保留合伙人最终兜底？");
        DECISIONS.put("Q-006", "外呼软件是否确定接入？APP直连ERP是否为P0还是P1？");
        DECISIONS.put("Q-007", "T0拨号次数自定义的默认策略是什么？是否按渠道/业务类型/销售组配置？");
        DECISIONS.put("Q-008", "无效线索/客户分级标签的等级和枚举值如何定义？");
        DECISIONS.put("Q-009", "进度节点型收费的节点清单、金额计算方式、催收Owner如何定义？");
        DECISIONS.put("Q-010", "T10庭审笔录是否必须上传才允许进入裁判文书跟进？");
        DECISIONS.put("Q-011", "裁判文书跟进周期从15日改为30日后，是否所有综法案件统一30日？");
        DECISIONS.put("Q-012", "风险代理费计算规则是否由系统自动算，还是二级律师填写、一级审核？");
    }

    @BeforeAll static void loadPackage() throws IOException
    {
        manifest = readJson("manifest.json");
        repositoryRoot = locateRepositoryRoot();
        migrationSql = Files.readString(repositoryRoot.resolve(
                "ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql"));
        envelopes = new ArrayList<>();
        for (String code : EXPECTED_CODES)
            envelopes.add(readJson(code + ".json"));
    }

    @Test void manifestContainsExactlyTheOrderedPrdTemplateSet() throws IOException
    {
        assertEquals("v0.2", manifest.getString("catalogVersion"));
        assertEquals(1, manifest.getIntValue("definitionSchemaVersion"));
        JSONArray templates = manifest.getJSONArray("templates");
        assertNotNull(templates);
        assertEquals(EXPECTED_CODES, templates.stream().map(JSONObject.class::cast)
                .map(value -> value.getString("templateCode")).toList());
        assertEquals(EXPECTED_CODES, templates.stream().map(JSONObject.class::cast)
                .map(value -> value.getString("resource").replace(".json", "")).toList());

        Path resources = repositoryRoot.resolve("law-todo/src/main/resources/todo-definitions/v0.2");
        try (var files = Files.list(resources))
        {
            assertEquals(26, files.filter(path -> path.getFileName().toString().endsWith(".json")).count());
        }
    }

    @Test void everyEnvelopeIsCompleteTraceableAndInternallyConsistent()
    {
        Set<String> codes = new HashSet<>();
        for (JSONObject envelope : envelopes)
        {
            String code = requiredText(envelope, "templateCode");
            assertTrue(codes.add(code), "duplicate template code " + code);
            requiredText(envelope, "templateName");
            requiredText(envelope, "stage");
            requiredText(envelope, "businessType");
            assertEquals("READY", requiredText(envelope, "definitionPackageState"), code);
            assertTrue(Set.of("READY", "BLOCKED").contains(requiredText(envelope, "foundationState")), code);
            assertTrue(Set.of("READY", "BLOCKED").contains(requiredText(envelope, "productionState")), code);

            JSONObject definition = envelope.getJSONObject("definition");
            assertNotNull(definition, code + " definition");
            assertEquals(code, definition.getString("templateCode"));
            assertEquals(1, definition.getIntValue("schemaVersion"));
            for (String section : List.of("event", "owner", "dod", "sla", "ui", "routing"))
                assertNotNull(definition.getJSONObject(section), code + " missing " + section);
            JSONObject owner = definition.getJSONObject("owner").getJSONObject("config");
            assertNotNull(owner.getJSONObject("fallback"), code + " owner must define an explicit fallback");
            assertNotNull(definition.getJSONArray("autoActions"), code + " autoActions");
            assertNotNull(definition.getJSONArray("decisionRefs"), code + " decisionRefs");
            Set<String> supportedFieldTypes = Set.of("text", "textarea", "number", "date", "datetime",
                    "dict", "user", "department", "file", "materialChecklist");
            for (Object field : definition.getJSONObject("ui").getJSONObject("config").getJSONArray("fields"))
                assertTrue(supportedFieldTypes.contains(((JSONObject) field).getString("type")),
                        code + " uses a field type not implemented by TodoDynamicForm: " + field);

            JSONObject routingPlan = envelope.getJSONObject("routingPlan");
            assertNotNull(routingPlan, code + " routing plan");
            assertFalse(requiredText(routingPlan, "start").isBlank(), code + " routing start");
            assertNotNull(routingPlan.getJSONArray("transitions"), code + " routing transitions");
            JSONObject capability = envelope.getJSONObject("handlerCapability");
            assertNotNull(capability, code + " handler capability");
            requiredText(capability, "requiredCode");
            requiredText(capability, "repositoryStatus");
            assertTrue(capability.getBooleanValue("requireBusinessWriteback"), code);

            JSONArray dependencies = envelope.getJSONArray("businessDependencies");
            JSONArray blockers = envelope.getJSONArray("blockers");
            JSONArray acceptance = envelope.getJSONArray("acceptanceRefs");
            assertNotNull(dependencies, code + " business dependencies");
            assertNotNull(blockers, code + " blockers");
            assertNotNull(acceptance, code + " acceptance refs");
            assertEquals(6, acceptance.size(), code + " acceptance refs");
            assertEquals(acceptance, definition.getJSONArray("acceptanceRefs"), code);
            for (String suffix : List.of("OWNER", "SLA", "DOD", "ROUTE", "HANDLER", "UI"))
                assertTrue(acceptance.contains("AT-" + code + "-" + suffix), code + " " + suffix);

            JSONArray decisions = definition.getJSONArray("decisionRefs");
            for (Object decision : decisions)
                assertTrue(hasBlocker(blockers, "DECISION", String.valueOf(decision)),
                        code + " missing decision blocker " + decision);
            for (Object value : dependencies)
            {
                JSONObject dependency = (JSONObject) value;
                requiredText(dependency, "code");
                requiredText(dependency, "repositoryStatus");
                requiredText(dependency, "reason");
                if (!"PRESENT".equals(dependency.getString("repositoryStatus")))
                    assertTrue(hasBlocker(blockers, "BUSINESS_DEPENDENCY", dependency.getString("code")),
                            code + " missing dependency blocker " + dependency.getString("code"));
            }
            for (Object value : blockers)
            {
                JSONObject blocker = (JSONObject) value;
                requiredText(blocker, "type");
                requiredText(blocker, "ref");
                requiredText(blocker, "reason");
            }
            if ("READY".equals(envelope.getString("foundationState")))
                assertTrue(blockers.isEmpty(), code + " READY cannot carry blockers");
            else
                assertFalse(blockers.isEmpty(), code + " BLOCKED must be explained");
        }
        assertEquals(new HashSet<>(EXPECTED_CODES), codes);
    }

    @Test void allDefinitionsDecodeAndCompileWithoutStructuralErrors()
    {
        TodoMapper mapper = mock(TodoMapper.class);
        for (JSONObject envelope : envelopes)
        {
            JSONObject event = envelope.getJSONObject("definition").getJSONObject("event");
            String eventType = event.getString("eventType");
            String businessType = envelope.getString("businessType");
            assertTrue(migrationSql.contains("select '" + eventType + "',1,'" + businessType + "'"),
                    envelope.getString("templateCode") + " event contract missing from migration");
            when(mapper.selectEventCatalog(eventType, 1)).thenReturn(Map.of(
                    "event_type", eventType, "payload_version", 1,
                    "payload_schema_json", "{\"type\":\"object\",\"additionalProperties\":true}",
                    "status", "ACTIVE"));
        }
        DECISIONS.forEach((code, title) -> {
            assertTrue(migrationSql.contains("select '" + code + "','" + title.replace("'", "''") + "'"), code);
            when(mapper.selectDecisionByCode(code)).thenReturn(Map.of(
                    "decision_code", code, "blocking", "Y", "status", "OPEN"));
        });
        TodoAutoActionCapabilityRegistry capabilities = new TodoAutoActionCapabilityRegistry(
                runtimeAutoActionTypes().stream().map(V02PrdDefinitionManifestTest::capability).toList());
        TodoDefinitionCompiler compiler = new TodoDefinitionCompiler(new TodoDefinitionCodec(),
                new TodoEventCatalogService(mapper), new TodoDecisionService(mapper),
                new ConditionValidator(), capabilities);
        TodoDefinitionCodec codec = new TodoDefinitionCodec();
        for (JSONObject envelope : envelopes)
        {
            String code = envelope.getString("templateCode");
            TodoDefinitionDocument definition = codec.read(envelope.getJSONObject("definition").toJSONString());
            DefinitionValidationReport report = compiler.compile(definition);
            Set<String> expectedDecisionErrors = new HashSet<>(definition.decisionRefs());
            Set<String> actualDecisionErrors = report.errors().stream()
                    .filter(issue -> "TODO_DECISION_UNRESOLVED".equals(issue.code()))
                    .map(issue -> issue.message().substring(issue.message().lastIndexOf(' ') + 1))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(expectedDecisionErrors, actualDecisionErrors, code + " unresolved decisions");
            assertTrue(report.errors().stream().allMatch(issue -> "TODO_DECISION_UNRESOLVED".equals(issue.code())),
                    code + " structural/compiler errors: " + report.errors());
            assertNotNull(report.definitionHash(), code);
            assertNotNull(report.compiledJson(), code);
        }
    }

    @Test void matrixHasExactlyOneRowForEveryTemplate() throws IOException
    {
        String matrix = Files.readString(repositoryRoot.resolve("doc/v0.2-foundation-template-matrix.md"));
        Pattern row = Pattern.compile("(?m)^\\| (TD-\\d{3}) \\|");
        List<String> codes = row.matcher(matrix).results().map(match -> match.group(1)).toList();
        assertEquals(EXPECTED_CODES, codes);
        for (JSONObject envelope : envelopes)
        {
            String code = envelope.getString("templateCode");
            String rowText = matrix.lines().filter(line -> line.startsWith("| " + code + " |"))
                    .findFirst().orElseThrow();
            String[] columns = rowText.split("\\|", -1);
            assertEquals(envelope.getString("templateName"), columns[2].trim(), code + " name");
            assertEquals(envelope.getString("definitionPackageState"), columns[5].trim(), code + " package state");
            assertEquals(envelope.getString("foundationState") + "/" + envelope.getString("productionState"),
                    columns[6].trim(), code + " readiness state");
            String decisions = envelope.getJSONObject("definition").getJSONArray("decisionRefs").isEmpty() ? "—"
                    : String.join("、", envelope.getJSONObject("definition").getJSONArray("decisionRefs").toJavaList(String.class));
            assertEquals(decisions, columns[7].trim(), code + " decisions");
            JSONObject handler = envelope.getJSONObject("handlerCapability");
            assertEquals(handler.getString("requiredCode") + "/" + handler.getString("repositoryStatus"),
                    columns[8].trim(), code + " handler");
            String dependencies = envelope.getJSONArray("businessDependencies").stream().map(JSONObject.class::cast)
                    .filter(value -> !"PRESENT".equals(value.getString("repositoryStatus")))
                    .map(value -> value.getString("code")).collect(java.util.stream.Collectors.joining("；"));
            assertEquals(dependencies.isBlank() ? "—" : dependencies, columns[9].trim(), code + " dependencies");
            for (Object acceptance : envelope.getJSONArray("acceptanceRefs"))
                assertTrue(columns[10].contains(String.valueOf(acceptance)), code + " " + acceptance);
        }
    }

    @Test void discountConflictAndSigningDefinitionsPreservePrdSpecificContracts()
    {
        JSONObject discount = envelope("TD-006");
        assertEquals("approvalOwnerId", discount.getJSONObject("definition").getJSONObject("owner")
                .getJSONObject("config").getString("field"));
        assertTrue(hasDependency(discount, "QUOTE_APPROVAL_OWNER_ROUTING"));

        JSONObject conflict = envelope("TD-007");
        JSONObject conflictDefinition = conflict.getJSONObject("definition");
        Set<String> conflictFields = conflictDefinition.getJSONObject("ui").getJSONObject("config")
                .getJSONArray("fields").stream().map(JSONObject.class::cast)
                .map(value -> value.getString("key")).collect(java.util.stream.Collectors.toSet());
        assertTrue(conflictFields.containsAll(Set.of("subjectType", "naturalPersonName", "idCardNo",
                "opposingPartyName", "enterpriseName", "unifiedSocialCreditCode", "waiverReason",
                "waiverAt", "waiverSignature")));
        assertTrue(conflictDefinition.getJSONObject("dod").getJSONObject("config")
                .getJSONArray("materials").isEmpty(), "no-conflict completion must not require a waiver signature");
        assertTrue(hasDependency(conflict, "CONFLICT_WAIVER_SIGNATURE_BINDING"));

        JSONObject signing = envelope("TD-010");
        Set<String> signingFields = signing.getJSONObject("definition").getJSONObject("ui")
                .getJSONObject("config").getJSONArray("fields").stream().map(JSONObject.class::cast)
                .map(value -> value.getString("key")).collect(java.util.stream.Collectors.toSet());
        assertTrue(signingFields.containsAll(Set.of("signMode", "caseManagerId", "adminId")));
        Set<String> branches = signing.getJSONObject("routingPlan").getJSONArray("transitions").stream()
                .map(JSONObject.class::cast).map(value -> value.getString("on"))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("OFFLINE_SIGNED", "ONLINE_SIGNED"), branches);
        assertTrue(hasDependency(signing, "SIGN_COLLABORATION_ASSIGNMENT"));
    }

    @Test void migrationImportsAllDefinitionsAsDraftsWithoutTouchingPublishedVersions() throws IOException
    {
        String sql = migrationSql;
        assertTrue(sql.contains("create table if not exists todo_prd_definition_catalog"));
        assertTrue(sql.contains("insert into todo_prd_definition_catalog"));
        assertTrue(sql.contains("insert into todo_template_version"));
        assertTrue(sql.contains("coalesce((select max(existing.version_no)+1"),
                "catalog migration must append a draft after existing history");
        assertTrue(sql.contains("cast(v.definition_json as char)=cast(c.definition_json as char)"),
                "catalog draft import must be idempotent by canonical definition");
        assertEquals(25, count(sql, "'BLOCKED','BLOCKED'"));
        long distinctEvents = envelopes.stream().map(value -> value.getJSONObject("definition")
                .getJSONObject("event").getString("eventType")).distinct().count();
        assertEquals(distinctEvents, count(sql, "'V0.2_PRD_CONTRACT','ACTIVE','migration'"));
        assertEquals(12, count(sql, "'Y','OPEN','migration'"));
        assertFalse(Pattern.compile("(?i)update\\s+todo_template_version").matcher(sql).find());
        assertFalse(Pattern.compile("(?i)insert\\s+into\\s+todo_trigger_rule").matcher(sql).find());
        for (String code : EXPECTED_CODES)
            assertTrue(sql.contains("'" + code + "'"), code + " missing from migration");
        DECISIONS.forEach((code, title) ->
                assertTrue(sql.contains("select '" + code + "','" + title.replace("'", "''") + "'"), code));
    }

    private static Set<String> runtimeAutoActionTypes()
    {
        try
        {
            String source = Files.readString(repositoryRoot.resolve(
                    "law-todo/src/main/java/com/law/todo/application/TodoAutoActionConfiguration.java"));
            Pattern pattern = Pattern.compile("capability\\(\"([A-Z_]+)\"");
            return pattern.matcher(source).results().map(match -> match.group(1))
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        }
        catch (IOException failure)
        {
            throw new IllegalStateException(failure);
        }
    }

    private static int count(String value, String token)
    {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }

    private static TodoAutoActionCapability capability(String type)
    {
        return new TodoAutoActionCapability()
        {
            @Override public String actionType() { return type; }
            @Override public AutoActionResult execute(TodoInstance todo, AutoActionRule rule, Actor actor)
            { return AutoActionResult.success(); }
        };
    }

    private static boolean hasBlocker(JSONArray blockers, String type, String ref)
    {
        return blockers.stream().map(JSONObject.class::cast)
                .anyMatch(value -> type.equals(value.getString("type")) && ref.equals(value.getString("ref")));
    }

    private static boolean hasDependency(JSONObject envelope, String code)
    {
        return envelope.getJSONArray("businessDependencies").stream().map(JSONObject.class::cast)
                .anyMatch(value -> code.equals(value.getString("code")));
    }

    private static JSONObject envelope(String code)
    {
        return envelopes.stream().filter(value -> code.equals(value.getString("templateCode")))
                .findFirst().orElseThrow();
    }

    private static String requiredText(JSONObject object, String key)
    {
        String value = object.getString(key);
        assertNotNull(value, "missing " + key);
        assertFalse(value.isBlank(), "blank " + key);
        return value;
    }

    private static JSONObject readJson(String resource) throws IOException
    {
        try (InputStream input = V02PrdDefinitionManifestTest.class.getResourceAsStream(RESOURCE_ROOT + resource))
        {
            assertNotNull(input, "missing resource " + resource);
            return JSON.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Path locateRepositoryRoot()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null)
        {
            if (Files.isDirectory(current.resolve("law-todo")) && Files.isDirectory(current.resolve("doc")))
                return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found from " + Path.of("").toAbsolutePath());
    }
}

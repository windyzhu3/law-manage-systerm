# Todo Engine Configuration Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing tabbed Todo configuration page with six production-ready menu pages and a drawer-based template workflow that reuses the existing Todo Engine while adding reusable SLA and DoD rule libraries.

**Architecture:** Keep the existing immutable template-version runtime, event catalogue, owner resolver, simulator, routing engine, Outbox integration, and Foundation publish gates. Add reusable SLA/DoD rule aggregates, a configuration read model, template draft references, simulation audit summaries, and release metadata; compile all references into the existing published version snapshots. Rebuild the Vue 2/Element UI configuration experience as six focused pages sharing a common page shell and right-side drawer system.

**Tech Stack:** Java 17, Spring Boot 3.5, Jakarta Validation, MyBatis, Flyway, MySQL 8, Vue 2.6, Element UI, RuoYi permissions/dictionaries, Node contract tests, Playwright.

## Global Constraints

- Work from commit `fb3753820d0f80b24a04494cbaf997744f65fa62` plus the approved design commit.
- Preserve Foundation governance tables, APIs, evidence, and publish guards.
- Do not modify historical published definitions or existing Todo instances.
- All create, edit, view, copy, enable/disable, simulate, publish, compare, and rollback content opens in a right-side drawer; confirmation prompts may remain modal confirmations.
- Fixed UI options must come from Flyway-managed dictionaries; events, handlers, validators, and automatic actions come from runtime capability catalogues.
- Published template versions store immutable SLA, DoD, trigger, UI, and routing snapshots.
- Every production behavior starts with a failing automated test and follows red-green-refactor.
- Keep Vue 2, Element UI, existing RuoYi layout components, and existing icon assets; add no new UI framework.
- Use forward-only migrations beginning with `V0_20_30`.
- Do not include local `ruoyi-ui/vue.config.js` or `.runtime-logs/` changes in commits.

---

## File Structure

### Backend additions

- `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java` — strong commands for SLA, DoD, simulation, and release metadata.
- `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationViews.java` — stable list/detail records consumed by controllers.
- `law-todo/src/main/java/com/law/todo/application/TodoSlaRuleManagementService.java` — reusable SLA rule lifecycle and validation.
- `law-todo/src/main/java/com/law/todo/application/TodoDodRuleManagementService.java` — reusable DoD rule lifecycle and validation.
- `law-todo/src/main/java/com/law/todo/application/TodoConfigurationQueryService.java` — dashboard, paginated list, detail, and release projections.
- `law-todo/src/main/java/com/law/todo/application/TodoConfigurationSimulationService.java` — read-only simulation facade and sanitized result persistence.
- `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java` — isolated mapper for configuration-centre persistence.
- `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml` — SQL for new rule tables and projections.
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java` — six-page API boundary.
- `ruoyi-admin/src/main/resources/db/migration/V0_20_30__todo_configuration_center.sql` — dictionaries, menus, permissions, tables, indexes, and release metadata.

### Frontend additions

- `ruoyi-ui/src/api/todo-config.js` — configuration-centre API client.
- `ruoyi-ui/src/views/todo/config/shared/ConfigPageShell.vue` — shared page layout.
- `ruoyi-ui/src/views/todo/config/shared/ConfigMetricCard.vue` — metric cards.
- `ruoyi-ui/src/views/todo/config/shared/ConfigDetailDrawer.vue` — drawer framing, footer, and dirty-close handling.
- `ruoyi-ui/src/views/todo/config/template/*` — template list and eight-step drawer.
- `ruoyi-ui/src/views/todo/config/trigger/*` — trigger list and drawer.
- `ruoyi-ui/src/views/todo/config/sla/*` — SLA list, drawer, and timeline.
- `ruoyi-ui/src/views/todo/config/dod/*` — DoD list and drawer.
- `ruoyi-ui/src/views/todo/config/simulation/*` — simulation workbench and result flow.
- `ruoyi-ui/src/views/todo/config/release/*` — release list, detail, diff, and rollback drawer.
- `ruoyi-ui/src/views/todo/config/styles/config-center.scss` — scoped shared visual tokens matching current business pages.
- `ruoyi-ui/scripts/check-todo-config-center.js` — source/API/menu/dictionary/drawer contract test.
- `ruoyi-ui/tests/e2e/todo-config-center.spec.js` — real browser workflow.

---

### Task 1: Add schema, dictionaries, menus, and permission contracts

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_30__todo_configuration_center.sql`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoConfigurationCenterMigrationContractTest.java`

**Interfaces:**
- Consumes: existing `todo_template`, `todo_template_version`, `todo_trigger_rule`, `sys_menu`, `sys_dict_type`, and `sys_dict_data`.
- Produces: `todo_sla_rule`, `todo_dod_rule`, `todo_template_draft_rule_ref`, `todo_simulation_record`, version release metadata, six page menus, button permissions, and fourteen dictionary types.

- [ ] **Step 1: Write the failing migration contract test**

```java
class TodoConfigurationCenterMigrationContractTest {
    private final String sql = resource("db/migration/V0_20_30__todo_configuration_center.sql");

    @Test void definesReusableRulesAndDrawerMenus() {
        assertTrue(sql.contains("create table todo_sla_rule"));
        assertTrue(sql.contains("create table todo_dod_rule"));
        assertTrue(sql.contains("create table todo_template_draft_rule_ref"));
        assertTrue(sql.contains("create table todo_simulation_record"));
        for (String component : List.of(
                "todo/config/template/index", "todo/config/trigger/index",
                "todo/config/sla/index", "todo/config/dod/index",
                "todo/config/simulation/index", "todo/config/release/index")) {
            assertTrue(sql.contains(component));
        }
        for (String dictionary : List.of(
                "law_todo_business_stage", "law_todo_business_type", "law_todo_template_type",
                "law_todo_publish_status", "law_todo_trigger_mode", "law_todo_condition_operator",
                "law_todo_owner_rule_type", "law_todo_sla_type", "law_todo_sla_unit",
                "law_todo_sla_start_strategy", "law_todo_timeout_strategy",
                "law_todo_dod_rule_type", "law_todo_rule_status", "law_todo_version_status")) {
            assertTrue(sql.contains(dictionary));
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails because the migration does not exist**

Run: `mvn -pl ruoyi-admin -am -Dtest=TodoConfigurationCenterMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `V0_20_30__todo_configuration_center.sql` cannot be loaded.

- [ ] **Step 3: Implement the forward-only migration**

```sql
create table todo_sla_rule (
  sla_rule_id bigint not null auto_increment,
  rule_code varchar(64) not null,
  rule_name varchar(128) not null,
  sla_type varchar(32) not null,
  duration_value int not null,
  duration_unit varchar(16) not null,
  calendar_code varchar(64) not null,
  start_strategy varchar(32) not null,
  soft_remind_percent int not null default 80,
  hard_remind_percent int not null default 100,
  escalate_percent int not null default 150,
  pause_policy_json json null,
  escalation_policy_json json null,
  auto_action_json json null,
  status char(1) not null default '0',
  version int not null default 0,
  create_by varchar(64) not null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (sla_rule_id),
  unique key uk_todo_sla_rule_code (rule_code),
  key idx_todo_sla_rule_list (status,sla_type,update_time,sla_rule_id),
  check (duration_value > 0),
  check (soft_remind_percent > 0 and soft_remind_percent <= hard_remind_percent
         and hard_remind_percent <= escalate_percent)
) engine=innodb comment='Todo可复用SLA规则';

create table todo_dod_rule (
  dod_rule_id bigint not null auto_increment,
  rule_code varchar(64) not null,
  rule_name varchar(128) not null,
  rule_type varchar(32) not null,
  required_fields_json json not null,
  required_attachments_json json not null,
  conditional_rules_json json not null,
  validator_refs_json json not null,
  error_messages_json json not null,
  status char(1) not null default '0',
  version int not null default 0,
  create_by varchar(64) not null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (dod_rule_id),
  unique key uk_todo_dod_rule_code (rule_code),
  key idx_todo_dod_rule_list (status,rule_type,update_time,dod_rule_id)
) engine=innodb comment='Todo可复用完成条件';
```

In the same migration, create and alter the following objects exactly:

- `todo_template_draft_rule_ref(ref_id,version_id,ref_type,ref_id_value,sort_order,config_json,create_time)` and unique key `(version_id,ref_type,ref_id_value,sort_order)`.
- `todo_simulation_record(simulation_id,request_id,template_version_id,event_type,business_type,business_id,input_summary_json,result_json,duration_ms,operator_id,create_time)` and indexes on `(operator_id,create_time)` and `(template_version_id,create_time)`.
- `todo_template_version.change_summary`, `impact_scope`, and `rollback_source_version_id` nullable columns.
- Idempotent menu creation and relocation of the old `todo/config/index` entry under a new `Todo Engine` directory menu.
- Exact button permissions for list/create/edit/copy/toggle/simulate/publish/diff/rollback operations.
- Dictionary types and rows using `insert ... select ... where not exists`.

- [ ] **Step 4: Extend the real Flyway test invariants**

```java
assertEquals(4L, count(connection,
    "select count(*) from information_schema.tables where table_schema=database() " +
    "and table_name in ('todo_sla_rule','todo_dod_rule','todo_template_draft_rule_ref','todo_simulation_record')"));
assertEquals(6L, count(connection,
    "select count(*) from sys_menu where component in " +
    "('todo/config/template/index','todo/config/trigger/index','todo/config/sla/index'," +
    "'todo/config/dod/index','todo/config/simulation/index','todo/config/release/index')"));
assertEquals(14L, count(connection,
    "select count(*) from sys_dict_type where dict_type like 'law_todo_%' and dict_type in " +
    "('law_todo_business_stage','law_todo_business_type','law_todo_template_type'," +
    "'law_todo_publish_status','law_todo_trigger_mode','law_todo_condition_operator'," +
    "'law_todo_owner_rule_type','law_todo_sla_type','law_todo_sla_unit'," +
    "'law_todo_sla_start_strategy','law_todo_timeout_strategy'," +
    "'law_todo_dod_rule_type','law_todo_rule_status','law_todo_version_status')"));
```

- [ ] **Step 5: Run migration tests**

Run: `mvn -pl ruoyi-admin -am -Dtest=FlywayMigrationTest,TodoConfigurationCenterMigrationContractTest test`

Expected: PASS with the schema at version `0.20.30` and all existing Foundation invariants unchanged.

- [ ] **Step 6: Commit**

```bash
git add ruoyi-admin/src/main/resources/db/migration/V0_20_30__todo_configuration_center.sql \
  ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java \
  ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoConfigurationCenterMigrationContractTest.java
git commit -m "feat(todo-config): add configuration center schema and dictionaries"
```

### Task 2: Define configuration commands, views, and persistence mapper

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationViews.java`
- Create: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Create: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Test: `law-todo/src/test/java/com/law/todo/mapper/TodoConfigurationMapperXmlContractTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationCommandValidationTest.java`

**Interfaces:**
- Consumes: dictionary codes, existing template/version IDs, runtime event/validator catalogues.
- Produces: `SlaRuleCommand`, `DodRuleCommand`, `TemplateDraftRuleCommand`, `ConfigurationSimulationCommand`, list/detail view records, and mapper CRUD methods.

- [ ] **Step 1: Write failing validation tests**

```java
@Test void slaThresholdsMustBeOrdered() {
    SlaRuleCommand command = new SlaRuleCommand(null,"SLA-A","首联", "RESPONSE",30,"MINUTE",
        "DEFAULT","TODO_CREATED",100,80,150,"{}","{}","{}","0","a-1",0);
    assertEquals(Set.of("thresholdsOrdered"), violations(command));
}

@Test void dodRuleRequiresJsonObjectsOrArrays() {
    DodRuleCommand command = new DodRuleCommand(null,"DOD-A","首联","TASK",
        "not-json","[]","[]","[]","{}","0","a-2",0);
    assertEquals(Set.of("ruleJsonValid"), violations(command));
}
```

- [ ] **Step 2: Run validation tests and confirm missing types fail compilation**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationCommandValidationTest test`

Expected: FAIL because `TodoConfigurationCommands` does not exist.

- [ ] **Step 3: Add immutable command records**

```java
public record SlaRuleCommand(Long slaRuleId,@NotBlank String ruleCode,@NotBlank String ruleName,
        @NotBlank String slaType,@NotNull @Positive Integer durationValue,@NotBlank String durationUnit,
        @NotBlank String calendarCode,@NotBlank String startStrategy,
        @NotNull @Positive Integer softRemindPercent,@NotNull @Positive Integer hardRemindPercent,
        @NotNull @Positive Integer escalatePercent,String pausePolicyJson,String escalationPolicyJson,
        String autoActionJson,@Pattern(regexp="0|1") String status,@NotBlank String actionId,
        @NotNull @PositiveOrZero Integer expectedVersion) {
    @AssertTrue(message="SLA thresholds must be ordered")
    public boolean isThresholdsOrdered() {
        return softRemindPercent <= hardRemindPercent && hardRemindPercent <= escalatePercent;
    }
}

public record DodRuleCommand(Long dodRuleId,@NotBlank String ruleCode,@NotBlank String ruleName,
        @NotBlank String ruleType,@NotBlank String requiredFieldsJson,
        @NotBlank String requiredAttachmentsJson,@NotBlank String conditionalRulesJson,
        @NotBlank String validatorRefsJson,@NotBlank String errorMessagesJson,
        @Pattern(regexp="0|1") String status,@NotBlank String actionId,
        @NotNull @PositiveOrZero Integer expectedVersion) {
    @AssertTrue(message="DoD rule JSON is invalid")
    public boolean isRuleJsonValid() {
        return JSON.isValidArray(requiredFieldsJson) && JSON.isValidArray(requiredAttachmentsJson)
            && JSON.isValidArray(conditionalRulesJson) && JSON.isValidArray(validatorRefsJson)
            && JSON.isValidObject(errorMessagesJson);
    }
}
```

- [ ] **Step 4: Write the failing mapper XML contract test**

```java
@Test void mapperDefinesRuleAndProjectionStatements() {
    String xml = resource("mapper/todo/TodoConfigurationMapper.xml");
    for (String id : List.of("selectSlaRules","selectSlaRule","insertSlaRule","updateSlaRuleConditionally",
            "selectDodRules","selectDodRule","insertDodRule","updateDodRuleConditionally",
            "replaceDraftRuleRefs","selectTemplateConfiguration","selectReleaseRecords",
            "insertSimulationRecord")) {
        assertTrue(xml.contains("id=\"" + id + "\""));
    }
}
```

- [ ] **Step 5: Add mapper methods and SQL**

```java
public interface TodoConfigurationMapper {
    List<Map<String,Object>> selectSlaRules(Map<String,Object> query);
    Map<String,Object> selectSlaRule(Long id);
    int insertSlaRule(Map<String,Object> row);
    int updateSlaRuleConditionally(Map<String,Object> row);
    int countSlaRuleReferences(Long id);
    List<Map<String,Object>> selectDodRules(Map<String,Object> query);
    Map<String,Object> selectDodRule(Long id);
    int insertDodRule(Map<String,Object> row);
    int updateDodRuleConditionally(Map<String,Object> row);
    int countDodRuleReferences(Long id);
    int deleteDraftRuleRefs(Long versionId);
    int insertDraftRuleRef(Map<String,Object> row);
    List<Map<String,Object>> selectDraftRuleRefs(Long versionId);
    Map<String,Object> selectTemplateConfiguration(Long templateId);
    List<Map<String,Object>> selectReleaseRecords(Map<String,Object> query);
    int insertSimulationRecord(Map<String,Object> row);
}
```

Use parameterized MyBatis SQL only. List queries must apply exact filters and stable order `update_time desc, id desc`; updates must include `where id=#{id} and version=#{expectedVersion}`.

- [ ] **Step 6: Run command and mapper tests**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationCommandValidationTest,TodoConfigurationMapperXmlContractTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java \
  law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationViews.java \
  law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java \
  law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml \
  law-todo/src/test/java/com/law/todo/application/TodoConfigurationCommandValidationTest.java \
  law-todo/src/test/java/com/law/todo/mapper/TodoConfigurationMapperXmlContractTest.java
git commit -m "feat(todo-config): define configuration contracts"
```

### Task 3: Implement reusable SLA rule management

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoSlaRuleManagementService.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoSlaRuleManagementServiceTest.java`

**Interfaces:**
- Consumes: `TodoConfigurationMapper`, `TodoMapper.selectCalendarByCode`, dictionary validation port, `SlaRuleCommand`.
- Produces: `list`, `detail`, `save`, `copy`, `toggle`, and `testCalculation` operations.

- [ ] **Step 1: Write failing service tests**

```java
@Test void rejectsUnknownCalendarAndDictionaryValues() {
    when(todoMapper.selectCalendarByCode("UNKNOWN")).thenReturn(null);
    TodoException error = assertThrows(TodoException.class,
        () -> service.save(command("UNKNOWN"), actor));
    assertEquals("TODO_SLA_RULE_CALENDAR_NOT_FOUND", error.getBusinessCode());
}

@Test void staleUpdateReturnsStableConflictCode() {
    when(mapper.updateSlaRuleConditionally(anyMap())).thenReturn(0);
    TodoException error = assertThrows(TodoException.class,
        () -> service.save(existingCommand(4), actor));
    assertEquals("TODO_SLA_RULE_VERSION_CONFLICT", error.getBusinessCode());
}

@Test void disabledReferencedRuleKeepsPublishedSnapshotsUntouched() {
    when(mapper.countSlaRuleReferences(9L)).thenReturn(3);
    service.toggle(9L,"1","toggle-9",2,actor);
    verify(mapper).updateSlaRuleConditionally(argThat(row -> "1".equals(row.get("status"))));
    verifyNoInteractions(definitionMapper);
}
```

- [ ] **Step 2: Run tests and confirm they fail because the service is missing**

Run: `mvn -pl law-todo -am -Dtest=TodoSlaRuleManagementServiceTest test`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement validation and lifecycle**

```java
@Service
public class TodoSlaRuleManagementService {
    @Transactional
    public long save(SlaRuleCommand command, Actor actor) {
        dictionaries.requireEnabled("law_todo_sla_type", command.slaType());
        dictionaries.requireEnabled("law_todo_sla_unit", command.durationUnit());
        dictionaries.requireEnabled("law_todo_sla_start_strategy", command.startStrategy());
        if (todoMapper.selectCalendarByCode(command.calendarCode()) == null)
            throw new TodoException("TODO_SLA_RULE_CALENDAR_NOT_FOUND", "工作日历不存在或已停用");
        Map<String,Object> row = rows.sla(command, actor);
        int changed = command.slaRuleId() == null
            ? mapper.insertSlaRule(row) : mapper.updateSlaRuleConditionally(row);
        if (changed == 0) throw new TodoException("TODO_SLA_RULE_VERSION_CONFLICT", "SLA规则已变化，请刷新后重试");
        return command.slaRuleId() == null ? number(row.get("slaRuleId")) : command.slaRuleId();
    }
}
```

Implement copy with a new unique code, toggle through optimistic locking, reference counts, and a read-only calculation returning creation, 80%, 100%, and 150% timestamps using `WorkingTimeCalculator`.

- [ ] **Step 4: Run SLA tests plus existing runtime SLA tests**

Run: `mvn -pl law-todo -am -Dtest=TodoSlaRuleManagementServiceTest,TodoSlaServiceTest,TodoCalendarServiceTest test`

Expected: PASS and no existing SLA regression.

- [ ] **Step 5: Commit**

```bash
git add law-todo/src/main/java/com/law/todo/application/TodoSlaRuleManagementService.java \
  law-todo/src/test/java/com/law/todo/application/TodoSlaRuleManagementServiceTest.java
git commit -m "feat(todo-config): manage reusable SLA rules"
```

### Task 4: Implement reusable DoD rule management

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoDodRuleManagementService.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoDodRuleManagementServiceTest.java`

**Interfaces:**
- Consumes: `TodoConfigurationMapper`, validator catalogue, dictionaries, `TodoFormValidator`, and `DodRuleCommand`.
- Produces: rule CRUD, copy, toggle, reference counts, and sample validation.

- [ ] **Step 1: Write failing DoD behavior tests**

```java
@Test void rejectsUnknownExternalValidator() {
    when(catalogue.validatorCodes()).thenReturn(Set.of("BUSINESS_STATE"));
    TodoException error = assertThrows(TodoException.class,
        () -> service.save(commandWithValidators("[\"MISSING\"]"), actor));
    assertEquals("TODO_DOD_VALIDATOR_NOT_FOUND", error.getBusinessCode());
}

@Test void sampleTestReturnsEveryFailedRequirement() {
    when(mapper.selectDodRule(7L)).thenReturn(ruleRequiring("result","CALL_RECORD"));
    DodTestResult result = service.test(7L, Map.of(), List.of(), actor);
    assertEquals(List.of("result"), result.missingFields());
    assertEquals(List.of("CALL_RECORD"), result.missingAttachments());
    assertFalse(result.passed());
}
```

- [ ] **Step 2: Run tests and verify the service is absent**

Run: `mvn -pl law-todo -am -Dtest=TodoDodRuleManagementServiceTest test`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement rule validation and sample execution**

```java
@Transactional(readOnly=true)
public DodTestResult test(Long id, Map<String,Object> payload, List<String> attachments, Actor actor) {
    Map<String,Object> rule = requireRule(id);
    List<String> missingFields = required(rule,"required_fields_json").stream()
        .filter(field -> !present(payload.get(field))).toList();
    List<String> missingAttachments = required(rule,"required_attachments_json").stream()
        .filter(type -> !attachments.contains(type)).toList();
    List<ValidationIssue> validatorIssues = validators.validate(refs(rule), payload);
    return new DodTestResult(missingFields.isEmpty() && missingAttachments.isEmpty()
        && validatorIssues.isEmpty(), missingFields, missingAttachments, validatorIssues);
}
```

Implement dictionary validation, validator capability validation, optimistic updates, copy, toggle, and published-reference protection without modifying snapshots.

- [ ] **Step 4: Run DoD and existing form-validator tests**

Run: `mvn -pl law-todo -am -Dtest=TodoDodRuleManagementServiceTest,TodoFormValidatorTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add law-todo/src/main/java/com/law/todo/application/TodoDodRuleManagementService.java \
  law-todo/src/test/java/com/law/todo/application/TodoDodRuleManagementServiceTest.java
git commit -m "feat(todo-config): manage reusable completion rules"
```

### Task 5: Add configuration projections, template rule binding, and release metadata

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationQueryService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoDefinitionCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationQueryServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoDefinitionRuleBindingTest.java`

**Interfaces:**
- Consumes: SLA/DoD rule IDs, trigger definition, owner rule, next-route graph, existing compiler and Foundation preflight.
- Produces: dashboard/list/detail/release projections and immutable published rule snapshots.

- [ ] **Step 1: Write failing snapshot tests**

```java
@Test void publishingDraftEmbedsReferencedRuleSnapshots() {
    when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of(
        ref("SLA",8L,0), ref("DOD",11L,0), ref("DOD",12L,1)));
    when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());
    when(configMapper.selectDodRule(11L)).thenReturn(dodRule("A"));
    when(configMapper.selectDodRule(12L)).thenReturn(dodRule("B"));

    service.publish(new PublishDraftCommand("publish-44",44L), actor);

    verify(mapper).publishTemplateVersionConditionally(eq(44L), anyString(), eq("alice"));
    verify(mapper).updateDefinitionDocument(argThat(row ->
        String.valueOf(row.get("slaRuleJson")).contains("SLA-FIRST") &&
        String.valueOf(row.get("dodRuleJson")).contains("DOD-A") &&
        String.valueOf(row.get("dodRuleJson")).contains("DOD-B")));
}
```

- [ ] **Step 2: Run tests and verify missing binding behavior**

Run: `mvn -pl law-todo -am -Dtest=TodoDefinitionRuleBindingTest,TodoConfigurationQueryServiceTest test`

Expected: FAIL because draft rule references are not compiled or projected.

- [ ] **Step 3: Extend draft commands and compile references**

```java
public record RuleReference(@NotBlank String type,@NotNull @Positive Long id,
        @NotNull @PositiveOrZero Integer order) { }

public record UpdateDraftCommand(@NotBlank String actionId,@NotNull @Positive Long versionId,
        String ownerRuleJson,String dodRuleJson,String slaRuleJson,String nextRuleJson,String uiSchemaJson,
        String definitionJson,String expectedDefinitionJson,List<@Valid RuleReference> ruleReferences,
        String changeSummary,String impactScope) { }
```

Inside `updateDraft`, replace draft refs transactionally after the definition row is conditionally updated. Inside `publish`, load active referenced rules, reject missing/disabled rules, merge them into the canonical definition, execute existing preflight, persist the snapshot, and publish conditionally.

- [ ] **Step 4: Add stable configuration projections**

```java
public ConfigurationDashboard dashboard() {
    return new ConfigurationDashboard(
        configMapper.countPublishedTemplates(), configMapper.countDraftTemplates(),
        configMapper.countEnabledSlaRules(), configMapper.countTodayTriggeredTodos());
}

public TemplateConfigurationDetail template(Long id) {
    Map<String,Object> row = require(configMapper.selectTemplateConfiguration(id));
    return views.template(row, configMapper.selectDraftRuleRefs(number(row.get("draft_version_id"))));
}
```

Release queries must aggregate `todo_template_version` and `todo_definition_action`; use version fields for change summary, impact scope, and rollback source.

- [ ] **Step 5: Run definition, compiler, parity, and query tests**

Run: `mvn -pl law-todo -am -Dtest=TodoDefinitionRuleBindingTest,TodoConfigurationQueryServiceTest,TodoDefinitionServiceTest,TodoDefinitionCompilerTest,ExistingTemplateParityTest,V02PrdDefinitionManifestTest test`

Expected: PASS and all existing PRD draft gates unchanged.

- [ ] **Step 6: Commit**

```bash
git add law-todo/src/main/java/com/law/todo/application/TodoConfigurationQueryService.java \
  law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java \
  law-todo/src/main/java/com/law/todo/application/command/TodoDefinitionCommands.java \
  law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java \
  law-todo/src/main/resources/mapper/todo/TodoMapper.xml \
  law-todo/src/test/java/com/law/todo/application/TodoConfigurationQueryServiceTest.java \
  law-todo/src/test/java/com/law/todo/application/TodoDefinitionRuleBindingTest.java
git commit -m "feat(todo-config): compile reusable rules into template versions"
```

### Task 6: Add read-only simulation facade and sanitized simulation records

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationSimulationService.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationSimulationServiceTest.java`

**Interfaces:**
- Consumes: existing `TodoDefinitionSimulationService`, event catalogue, rule services, and `ConfigurationSimulationCommand`.
- Produces: ordered execution trace, card preview, rule summaries, duration, and sanitized audit row.

- [ ] **Step 1: Write failing no-side-effect tests**

```java
@Test void simulationDoesNotCreateTodoOrAdvanceRoundRobin() {
    service.simulate(command(), actor);
    verify(todoMapper, never()).insertInstance(any());
    verify(organization, never()).claimRoundRobin(anyString(), anyList());
    verify(configMapper).insertSimulationRecord(argThat(row ->
        !String.valueOf(row.get("inputSummaryJson")).contains("customerPhone")));
}
```

- [ ] **Step 2: Run test and confirm the facade is missing**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationSimulationServiceTest test`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement facade with explicit sanitization**

```java
@Transactional
public ConfigurationSimulationResult simulate(ConfigurationSimulationCommand command, Actor actor) {
    long started = System.nanoTime();
    TodoSimulationView result = definitions.simulate(command.versionId(), command.toDefinitionCommand());
    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    Map<String,Object> audit = new LinkedHashMap<>();
    audit.put("requestId", command.requestId());
    audit.put("templateVersionId", command.versionId());
    audit.put("eventType", command.eventType());
    audit.put("businessType", command.businessType());
    audit.put("businessId", command.businessId());
    audit.put("inputSummaryJson", JSON.toJSONString(sanitizer.keysOnly(command.payload())));
    audit.put("resultJson", JSON.toJSONString(result));
    audit.put("durationMs", durationMs);
    audit.put("operatorId", actor.userId());
    configMapper.insertSimulationRecord(audit);
    return views.simulation(result,durationMs);
}
```

- [ ] **Step 4: Run simulation, owner-preview, and routing tests**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationSimulationServiceTest,TodoDefinitionSimulationServiceTest,CompositeOwnerResolverTest,TodoRoutingEngineTest test`

Expected: PASS with no round-robin mutation or Todo insert.

- [ ] **Step 5: Commit**

```bash
git add law-todo/src/main/java/com/law/todo/application/TodoConfigurationSimulationService.java \
  law-todo/src/test/java/com/law/todo/application/TodoConfigurationSimulationServiceTest.java
git commit -m "feat(todo-config): add read-only configuration simulation"
```

### Task 7: Expose typed configuration-centre APIs

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerValidationTest.java`

**Interfaces:**
- Consumes: query, SLA, DoD, template, trigger, simulation, and definition services.
- Produces: `/todo/config/*` endpoints with permission checks and validated commands.

- [ ] **Step 1: Write failing controller validation and permission tests**

```java
@Test void slaCreateRejectsInvalidThresholdOrder() throws Exception {
    mvc.perform(post("/todo/config/sla-rules").with(user(admin()))
        .contentType(APPLICATION_JSON)
        .content("{\"ruleCode\":\"SLA-A\",\"ruleName\":\"A\",\"slaType\":\"RESPONSE\","
            + "\"durationValue\":30,\"durationUnit\":\"MINUTE\",\"calendarCode\":\"DEFAULT\","
            + "\"startStrategy\":\"TODO_CREATED\",\"softRemindPercent\":100,"
            + "\"hardRemindPercent\":80,\"escalatePercent\":150,\"status\":\"0\","
            + "\"actionId\":\"a\",\"expectedVersion\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(500));
}
```

- [ ] **Step 2: Run test and confirm endpoints are absent**

Run: `mvn -pl ruoyi-admin -am -Dtest=TodoConfigurationControllerValidationTest test`

Expected: FAIL with no handler mapping or missing controller.

- [ ] **Step 3: Implement controller routes**

```java
@RestController
@RequestMapping("/todo/config")
public class TodoConfigurationController extends BaseController {
    @PreAuthorize("@ss.hasPermi('todo:sla-rule:list')")
    @GetMapping("/sla-rules") public TableDataInfo slaRules(@RequestParam Map<String,Object> query) {
        startPage(); return getDataTable(sla.list(query));
    }

    @PreAuthorize("@ss.hasPermi('todo:sla-rule:create')")
    @PostMapping("/sla-rules") public AjaxResult createSla(@Valid @RequestBody SlaRuleCommand command) {
        return success(sla.save(command, actor()));
    }

    @PreAuthorize("@ss.hasPermi('todo:dod-rule:edit')")
    @PutMapping("/dod-rules/{id}") public AjaxResult updateDod(@PathVariable Long id,
            @Valid @RequestBody DodRuleCommand command) {
        requireSameId(id,command.dodRuleId()); return success(dod.save(command,actor()));
    }
}
```

Add dashboard, list, detail, create, update, copy, toggle, test, template aggregate, trigger list/save/toggle/sort/simulate, simulation, release list/detail/diff, publish, and rollback-draft routes. Keep existing `/todo/template` aliases operational.

- [ ] **Step 4: Run controller and full backend tests**

Run: `mvn -pl ruoyi-admin -am -Dtest=TodoConfigurationControllerValidationTest,TodoTemplateControllerValidationTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java \
  ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerValidationTest.java
git commit -m "feat(todo-config): expose configuration center APIs"
```

### Task 8: Add frontend API, shared shell, and drawer contract

**Files:**
- Create: `ruoyi-ui/src/api/todo-config.js`
- Create: `ruoyi-ui/src/views/todo/config/shared/ConfigPageShell.vue`
- Create: `ruoyi-ui/src/views/todo/config/shared/ConfigMetricCard.vue`
- Create: `ruoyi-ui/src/views/todo/config/shared/ConfigDetailDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/styles/config-center.scss`
- Create: `ruoyi-ui/scripts/check-todo-config-center.js`
- Modify: `ruoyi-ui/package.json`

**Interfaces:**
- Consumes: `/todo/config/*`, RuoYi `request`, `pagination`, `dict-tag`, `v-hasPermi`, and Element UI drawer.
- Produces: shared page/list/drawer components and API functions used by all six pages.

- [ ] **Step 1: Write the failing source contract**

```js
const fs = require('fs')
const required = [
  'src/api/todo-config.js',
  'src/views/todo/config/shared/ConfigPageShell.vue',
  'src/views/todo/config/shared/ConfigMetricCard.vue',
  'src/views/todo/config/shared/ConfigDetailDrawer.vue'
]
required.forEach(file => { if (!fs.existsSync(file)) throw new Error(`missing ${file}`) })
const drawer = fs.readFileSync(required[3], 'utf8')
if (!drawer.includes('<el-drawer') || !drawer.includes('before-close'))
  throw new Error('configuration actions must use a guarded right drawer')
```

- [ ] **Step 2: Run contract and confirm missing files fail**

Run: `npm run test:todo-config`

Expected: FAIL with `missing src/api/todo-config.js`.

- [ ] **Step 3: Add API functions and shared components**

```js
export const listSlaRules = params => request({ url: '/todo/config/sla-rules', method: 'get', params })
export const createSlaRule = data => request({ url: '/todo/config/sla-rules', method: 'post', data })
export const updateSlaRule = (id, data) => request({ url: `/todo/config/sla-rules/${id}`, method: 'put', data })
export const testSlaRule = (id, data) => request({ url: `/todo/config/sla-rules/${id}/test`, method: 'post', data })
export const listDodRules = params => request({ url: '/todo/config/dod-rules', method: 'get', params })
export const simulateConfiguration = data => request({ url: '/todo/config/simulations', method: 'post', data })
export const listReleaseRecords = params => request({ url: '/todo/config/release-records', method: 'get', params })
```

```vue
<el-drawer :title="title" :visible.sync="opened" direction="rtl" :size="size"
  :before-close="requestClose" append-to-body destroy-on-close>
  <div class="config-drawer__body"><slot /></div>
  <div class="config-drawer__footer"><slot name="footer" /></div>
</el-drawer>
```

`requestClose` must call the parent-provided dirty predicate and use `$confirm` only when unsaved state exists.

- [ ] **Step 4: Add the package script and pass the contract**

```json
"test:todo-config": "node scripts/check-todo-config-center.js"
```

Run: `npm run test:todo-config`

Expected: PASS with `todo configuration center contract ok`.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/api/todo-config.js ruoyi-ui/src/views/todo/config/shared \
  ruoyi-ui/src/views/todo/config/styles/config-center.scss \
  ruoyi-ui/scripts/check-todo-config-center.js ruoyi-ui/package.json
git commit -m "feat(todo-config): add shared configuration UI shell"
```

### Task 9: Build SLA and completion-condition pages

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/sla/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/sla/SlaRuleDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/sla/SlaTimeline.vue`
- Create: `ruoyi-ui/src/views/todo/config/dod/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/dod/DodRuleDrawer.vue`
- Modify: `ruoyi-ui/scripts/check-todo-config-center.js`

**Interfaces:**
- Consumes: shared components, dictionaries, SLA/DoD APIs.
- Produces: screenshot-3 and screenshot-4 equivalent list/detail/create/edit/test flows.

- [ ] **Step 1: Extend the failing frontend contract**

```js
for (const page of [
  'src/views/todo/config/sla/index.vue',
  'src/views/todo/config/sla/SlaRuleDrawer.vue',
  'src/views/todo/config/sla/SlaTimeline.vue',
  'src/views/todo/config/dod/index.vue',
  'src/views/todo/config/dod/DodRuleDrawer.vue'
]) if (!fs.existsSync(page)) throw new Error(`missing ${page}`)

const sla = fs.readFileSync('src/views/todo/config/sla/index.vue','utf8')
for (const dict of ['law_todo_sla_type','law_todo_timeout_strategy','law_todo_rule_status'])
  if (!sla.includes(dict)) throw new Error(`SLA page must use ${dict}`)
```

- [ ] **Step 2: Run and confirm missing pages fail**

Run: `npm run test:todo-config`

Expected: FAIL on the first missing SLA page.

- [ ] **Step 3: Implement SLA list and drawer**

```vue
<config-page-shell title="SLA规则管理" :metrics="metrics" :loading="loading">
  <template #filters>
    <el-select v-model="query.slaType" clearable placeholder="SLA类型">
      <el-option v-for="item in dict.type.law_todo_sla_type" :key="item.value"
        :label="item.label" :value="item.value" />
    </el-select>
    <el-button type="primary" v-hasPermi="['todo:sla-rule:create']" @click="openCreate">新增规则</el-button>
  </template>
  <el-table :data="rows" @row-click="openDetail">
    <el-table-column prop="ruleName" label="规则名称" min-width="180" />
    <el-table-column prop="slaTypeLabel" label="SLA 类型" width="130" />
    <el-table-column prop="durationText" label="时限" width="120" />
    <el-table-column prop="calendarName" label="工作日历" min-width="150" />
    <el-table-column prop="bindingCount" label="引用模板" width="100" />
    <el-table-column prop="statusLabel" label="状态" width="90" />
    <el-table-column prop="updateTime" label="更新时间" width="170" />
  </el-table>
  <sla-rule-drawer :visible.sync="drawerOpen" :value="selected" @saved="reload" />
</config-page-shell>
```

The drawer must show basic fields, 80/100/150 timeline, calculation example, bindings, create/edit/copy/toggle/test actions, and fixed footer.

- [ ] **Step 4: Implement DoD list and drawer**

Use dictionary-driven rule type/status selectors, collapsible groups for required fields, attachments, conditional requirements, validators, and error copy. Implement sample validation inside the drawer and display every failed requirement.

- [ ] **Step 5: Run contracts and production build**

Run: `npm run test:todo-config && npm run build:prod`

Expected: PASS; only existing bundle-size warnings are allowed.

- [ ] **Step 6: Commit**

```bash
git add ruoyi-ui/src/views/todo/config/sla ruoyi-ui/src/views/todo/config/dod \
  ruoyi-ui/scripts/check-todo-config-center.js
git commit -m "feat(todo-config): add SLA and completion rule pages"
```

### Task 10: Rebuild trigger-rule management with drawer operations

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/trigger/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/trigger/TriggerRuleDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/trigger/TriggerConditionBuilder.vue`
- Modify: `ruoyi-ui/scripts/check-todo-config-center.js`

**Interfaces:**
- Consumes: event catalogue, template published versions, condition evaluator schema, trigger APIs.
- Produces: screenshot-5 equivalent list/detail/create/edit/toggle/sort/simulate flows.

- [ ] **Step 1: Add failing drawer and dictionary assertions**

```js
const trigger = 'src/views/todo/config/trigger/index.vue'
if (!fs.existsSync(trigger)) throw new Error(`missing ${trigger}`)
const source = fs.readFileSync(trigger,'utf8')
if (source.includes('<el-dialog')) throw new Error('trigger operations may not use el-dialog')
for (const dict of ['law_todo_trigger_mode','law_todo_condition_operator','law_todo_rule_status'])
  if (!source.includes(dict)) throw new Error(`trigger page must use ${dict}`)
```

- [ ] **Step 2: Run and confirm the page is missing**

Run: `npm run test:todo-config`

Expected: FAIL.

- [ ] **Step 3: Implement trigger list and drawer**

The list must expose source event, source template, condition summary, target action, target template/process, trigger mode, status, update time, and actions. The drawer loads published versions after template selection and blocks enable when no published version exists.

```js
async save() {
  await this.$refs.form.validate()
  const payload = {
    ...this.form,
    actionId: `trigger-${Date.now()}`,
    conditionJson: JSON.stringify(this.conditions),
    expectedVersion: Number(this.form.version || 0)
  }
  await (payload.triggerRuleId ? updateTriggerRule(payload.triggerRuleId,payload) : createTriggerRule(payload))
  this.$emit('saved')
}
```

- [ ] **Step 4: Run frontend contracts and build**

Run: `npm run test:todo-config && npm run test:todo-schema && npm run build:prod`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-ui/src/views/todo/config/trigger ruoyi-ui/scripts/check-todo-config-center.js
git commit -m "feat(todo-config): rebuild trigger rule management"
```

### Task 11: Build template list and eight-step template drawer

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/template/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/TemplateDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/steps/TemplateBasicStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/steps/TemplateTriggerStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/steps/TemplateOwnerStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/steps/TemplateSlaStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/steps/TemplateDodStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/steps/TemplateRoutingStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/steps/TemplatePreviewStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/steps/TemplateVersionStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/TemplateSummaryPanel.vue`
- Modify: `ruoyi-ui/scripts/check-todo-config-center.js`

**Interfaces:**
- Consumes: template aggregate API, dictionaries, rule libraries, event/owner/routing builders, simulation and publish APIs.
- Produces: screenshot-1 list/detail and screenshot-2 eight-step drawer workflow.

- [ ] **Step 1: Add failing eight-step contract**

```js
const drawerPath = 'src/views/todo/config/template/TemplateDrawer.vue'
if (!fs.existsSync(drawerPath)) throw new Error(`missing ${drawerPath}`)
const drawer = fs.readFileSync(drawerPath,'utf8')
for (const step of ['basic','trigger','owner','sla','dod','routing','preview','versions'])
  if (!drawer.includes(`name: '${step}'`)) throw new Error(`template drawer missing ${step} step`)
if (!drawer.includes('size="78%"')) throw new Error('template drawer must use the approved wide drawer')
```

- [ ] **Step 2: Run and confirm missing template files fail**

Run: `npm run test:todo-config`

Expected: FAIL.

- [ ] **Step 3: Implement list and summary drawer**

Use metric cards, dictionary filters, paginated table, row click detail, and right-side summary sections for trigger, owner, SLA, DoD, next rules, card preview, and version metadata. Import, create, edit, copy, simulate, publish, toggle, and release-view buttons all open drawers.

- [ ] **Step 4: Implement the eight-step editor state model**

```js
const emptyDraft = () => ({
  templateId: null, versionId: null, templateCode: '', templateName: '',
  businessType: '', businessStage: '', templateType: '', priority: 'NORMAL', description: '',
  trigger: { eventType: '', payloadVersion: 1, condition: {} },
  owner: { type: 'BUSINESS_OWNER', candidates: [], cc: [], skipUnavailable: true, useDelegation: true },
  slaRuleId: null, dodRuleIds: [], routing: { nodes: [], edges: [] },
  changeSummary: '', impactScope: ''
})
```

Each step owns validation through `validate()` and emits a normalized partial draft. `saveDraft` sends all fields plus ordered rule references. SLA and DoD steps open nested right drawers and refresh selection after save.

- [ ] **Step 5: Implement preflight, simulation, and publish flow**

```js
async publish() {
  const valid = await this.validateAllSteps()
  if (!valid) return
  await this.saveDraft()
  const preflight = await preflightTemplate(this.form.versionId)
  if (!preflight.data.publishable) {
    this.openIssues(preflight.data.report.errors)
    return
  }
  await publishTemplate(this.form.versionId, {
    actionId: `publish-${Date.now()}`, versionId: this.form.versionId
  })
  this.$emit('published')
}
```

- [ ] **Step 6: Run frontend contracts, roundtrip, and build**

Run: `npm run test:todo-config && node scripts/check-todo-definition-roundtrip.js && npm run build:prod`

Expected: PASS and no JSON roundtrip differences.

- [ ] **Step 7: Commit**

```bash
git add ruoyi-ui/src/views/todo/config/template ruoyi-ui/scripts/check-todo-config-center.js
git commit -m "feat(todo-config): add drawer-based template workflow"
```

### Task 12: Build simulation and release-record pages

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/simulation/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/simulation/SimulationDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/simulation/SimulationResult.vue`
- Create: `ruoyi-ui/src/views/todo/config/release/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/release/ReleaseRecordDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/release/VersionSemanticDiff.vue`
- Modify: `ruoyi-ui/scripts/check-todo-config-center.js`

**Interfaces:**
- Consumes: simulation and release APIs, dictionaries, existing definition diff data.
- Produces: screenshot-6 simulation flow and screenshot-7 version/release flow.

- [ ] **Step 1: Add failing source contracts**

```js
for (const file of [
  'src/views/todo/config/simulation/index.vue',
  'src/views/todo/config/simulation/SimulationResult.vue',
  'src/views/todo/config/release/index.vue',
  'src/views/todo/config/release/ReleaseRecordDrawer.vue'
]) if (!fs.existsSync(file)) throw new Error(`missing ${file}`)
```

- [ ] **Step 2: Run and confirm missing pages fail**

Run: `npm run test:todo-config`

Expected: FAIL.

- [ ] **Step 3: Implement simulation input and ordered result**

Use event catalogue selection, business type dictionary, business object picker, typed Payload rows, and a start-simulation button. Render result in this order: state, template, owner, SLA, DoD, next route, card preview, technical log. Display a visible “只读模拟，不创建真实待办” notice.

- [ ] **Step 4: Implement release list and drawer**

Provide template/status/publisher/date filters, semantic diff, export, version detail, copy-as-new-version, and rollback-as-new-draft. Never edit an existing published version.

```js
async rollback(row) {
  await rollbackRelease(row.versionId, {
    actionId: `rollback-${Date.now()}`,
    newVersionNo: Number(row.latestVersionNo) + 1
  })
  this.$modal.msgSuccess('已生成回滚草稿')
  await this.load()
}
```

- [ ] **Step 5: Run frontend contracts and build**

Run: `npm run test:todo-config && npm run test:todo-schema && npm run build:prod`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ruoyi-ui/src/views/todo/config/simulation ruoyi-ui/src/views/todo/config/release \
  ruoyi-ui/scripts/check-todo-config-center.js
git commit -m "feat(todo-config): add simulation and release pages"
```

### Task 13: Remove old configuration page entry and protect Foundation capabilities

**Files:**
- Delete: `ruoyi-ui/src/views/todo/config/index.vue`
- Retain but unroute: existing Foundation governance components under `ruoyi-ui/src/views/todo/config/components/`
- Modify: `ruoyi-ui/scripts/check-todo-ui.js`
- Modify: `ruoyi-ui/scripts/check-todo-config-center.js`
- Test: `law-todo/src/test/java/com/law/todo/integration/TodoConfigurationCenterFoundationBoundaryTest.java`

**Interfaces:**
- Consumes: new menu components and old Foundation services/controllers.
- Produces: no old tab page, no deleted governance backend capability, and no accidental publish-gate bypass.

- [ ] **Step 1: Write failing boundary assertions**

```java
@Test void rebuildDoesNotRemoveFoundationPublishGate() {
    assertNotNull(TodoFoundationAdmissionReadinessService.class);
    assertNotNull(TodoAdmissionEvidenceService.class);
    String definition = source("application/TodoDefinitionService.java");
    assertTrue(definition.contains("TODO_PRD_TEMPLATE_BLOCKED"));
}
```

```js
if (fs.existsSync('src/views/todo/config/index.vue'))
  throw new Error('old tabbed configuration page must be removed')
for (const governance of ['FoundationAdmissionOverview.vue','FoundationResourceReadiness.vue',
  'HistoricalMigrationReadiness.vue','FileSecurityReadiness.vue','FinanceReadiness.vue','AcceptanceReadiness.vue'])
  if (!fs.existsSync(`src/views/todo/config/components/${governance}`))
    throw new Error(`Foundation component must be retained: ${governance}`)
```

- [ ] **Step 2: Run and confirm the old page assertion fails**

Run: `npm run test:todo-config`

Expected: FAIL because `src/views/todo/config/index.vue` still exists.

- [ ] **Step 3: Delete only the old route page and update contracts**

Remove old page imports from `check-todo-ui.js`; add all six new page paths. Do not delete Foundation APIs, services, mapper XML, migrations, or governance components.

- [ ] **Step 4: Run Foundation and frontend regression tests**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationCenterFoundationBoundaryTest,TodoFoundationAdmissionReadinessServiceTest,V02PrdDefinitionManifestTest test`

Run: `npm run test:todo && npm run test:todo-config && npm run test:foundation-docs && npm run build:prod`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A ruoyi-ui/src/views/todo/config/index.vue ruoyi-ui/scripts/check-todo-ui.js \
  ruoyi-ui/scripts/check-todo-config-center.js \
  law-todo/src/test/java/com/law/todo/integration/TodoConfigurationCenterFoundationBoundaryTest.java
git commit -m "refactor(todo-config): replace legacy configuration tabs"
```

### Task 14: Add real E2E, visual QA, and full release verification

**Files:**
- Create: `ruoyi-ui/tests/e2e/todo-config-center.spec.js`
- Create: `design-qa.md`
- Modify: `.github/workflows/ci.yml`
- Modify: `doc/reviews/v0.2-foundation-admission-report.md`

**Interfaces:**
- Consumes: built backend/frontend, MySQL 8 migration, seven reference screenshots, test roles/users.
- Produces: real end-to-end evidence, visual comparison report, and green quality gate.

- [ ] **Step 1: Write the failing Playwright journey**

```js
test('creates, simulates, publishes, and reviews a template through drawers', async ({ page }) => {
  await loginAs(page, 'todo_config_admin')
  await page.goto('/todo-template')
  await page.getByRole('button', { name: '新建模板' }).click()
  await expect(page.locator('.el-drawer')).toBeVisible()
  await fillTemplateBasic(page, { name: '验收首联待办', code: 'ACCEPT_FIRST_CONTACT' })
  await bindTrigger(page, 'LEAD_ASSIGNED')
  await bindSla(page, 'SLA-FIRST-CONTACT-30M')
  await bindDod(page, 'DOD-FIRST-CONTACT')
  await page.getByRole('button', { name: '保存草稿' }).click()
  await page.getByRole('button', { name: '模拟测试' }).click()
  await expect(page.getByText('模拟执行成功')).toBeVisible()
  await page.getByRole('button', { name: '发布' }).click()
  await expect(page.getByText('版本已发布')).toBeVisible()
  await page.goto('/todo-release-record')
  await expect(page.getByText('ACCEPT_FIRST_CONTACT')).toBeVisible()
})
```

- [ ] **Step 2: Run the E2E and confirm it fails on missing runtime wiring or behavior**

Run: `npm run test:e2e -- --grep "creates, simulates, publishes"`

Expected: FAIL before the full backend and menu wiring is complete.

- [ ] **Step 3: Wire CI to the new tests**

Use Node 20 in the frontend jobs, create MySQL databases with explicit `utf8mb4` and `utf8mb4_unicode_ci`, run `npm run test:todo-config`, and include the new Playwright spec in the real-backend E2E job.

- [ ] **Step 4: Run the full local verification matrix**

Run:

```bash
mvn verify
cd ruoyi-ui
npm run test:todo
npm run test:todo-config
npm run test:todo-schema
npm run test:encoding
npm run test:foundation-ci
npm run test:foundation-docs
npm run build:prod
npm run test:e2e
```

Expected: all commands PASS; MySQL-dependent tests run with configured credentials and no core Todo test is skipped.

- [ ] **Step 5: Capture and compare all seven target states**

At the reference desktop viewport, capture:

1. template list with detail drawer;
2. template eight-step drawer;
3. SLA list with detail drawer;
4. DoD list with detail drawer;
5. trigger list with detail drawer;
6. simulation input/result state;
7. release list with version drawer.

Write `design-qa.md` with a table containing reference image, implementation capture, P0/P1/P2/P3 findings, fixes, and `final result: passed`. Fix every P0/P1/P2 and repeat the same-state comparison before proceeding.

- [ ] **Step 6: Update the Foundation report with exact boundaries**

Record that the configuration UI and reusable rule libraries are complete while PRD production readiness remains governed by existing Foundation states. Do not change Q/G decisions or claim business approval.

- [ ] **Step 7: Commit verification evidence and workflow**

```bash
git add ruoyi-ui/tests/e2e/todo-config-center.spec.js design-qa.md \
  .github/workflows/ci.yml doc/reviews/v0.2-foundation-admission-report.md
git commit -m "test(todo-config): verify configuration center end to end"
```

- [ ] **Step 8: Final branch audit before publishing**

Run:

```bash
git status --short
git diff --check v0.2-Foundation...HEAD
git log --oneline v0.2-Foundation..HEAD
```

Expected: only the pre-existing local runtime files remain uncommitted; no whitespace errors; commits map one-to-one to Tasks 1–14.

---

## Plan Self-Review Checklist

- Every approved design requirement maps to at least one task.
- Schema and dictionary work precede backend and frontend consumers.
- SLA and DoD rules are reusable but published versions remain immutable snapshots.
- All primary operations use right drawers.
- The old tabbed page is removed without deleting Foundation governance capability.
- Simulation is explicitly read-only and does not mutate round-robin or Todo state.
- Release history has one source of truth: template versions plus the definition action ledger.
- Tests cover validation, persistence, permissions, migrations, compatibility, real browser flows, and visual fidelity.
- Final verification does not treat Mock E2E or page presence as business acceptance.

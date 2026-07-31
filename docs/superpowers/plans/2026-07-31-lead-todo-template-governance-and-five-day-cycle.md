# Lead Todo Template Governance and Five-Day Cycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make TD-002, TD-003 and TD-004 fully configurable, simulatable and publishable, guarantee one active first-contact ingress, and make TD-004 persist substantive progress and create the next five-day Todo exactly once.

**Architecture:** Extend the existing governed configuration catalogs, immutable template versions, scenario evidence and persisted schedule subsystem. Treat TD-001 through TD-004 as one coordinated release bundle: downstream versions publish first, a new TD-001 binds them, and a single active entry slot is switched transactionally. TD-004 writes a lead follow-up fact and creates a one-window schedule plan; the existing materializer creates the next TD-004.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis XML, MySQL 8, Flyway, JUnit 5, Mockito, Vue 2.6, Element UI, Node-based UI contract tests, Playwright CLI.

## Global Constraints

- Do not modify already executed Flyway migrations; add only `V0_20_70` and later forward migrations.
- Do not mutate or delete published template versions, historical Todo instances, actions, evidence or schedule occurrences.
- Keep `TD-001` as the only active first-contact ingress; `LEAD_FIRST_CONTACT` remains history-compatible but retired.
- Do not create independent external trigger rules for TD-002, TD-003 or TD-004.
- Person and department values render as names; dictionary values render as Chinese labels; raw IDs remain available only in technical details.
- A simulation result is valid only for the exact current draft `definition_hash`.
- TD-004 refreshes the five-day cycle only after substantive progress; the 30-day no-progress/TD-005 flow is outside this plan.
- Use existing `todo_schedule_plan`, `todo_schedule_window` and `todo_schedule_occurrence`; do not introduce another scheduler.
- All user-visible text added by this plan is UTF-8 Chinese; stable internal codes remain uppercase English.
- Preserve the user-owned dirty files currently present in the worktree; stage only files listed by the active task.

---

## File and Responsibility Map

### Database and migration files

- `ruoyi-admin/src/main/resources/db/migration/V0_20_70__todo_trigger_entry_slots.sql`: active-entry uniqueness, legacy replacement metadata and backfill.
- `ruoyi-admin/src/main/resources/db/migration/V0_20_71__lead_todo_guided_resources.sql`: semantic event schemas, LEAD fields, materials and DoD recipes.
- `ruoyi-admin/src/main/java/db/migration/V0_20_72__SeedTd002GuidedDraft.java`: corrected TD-002 draft and governed scenarios.
- `ruoyi-admin/src/main/java/db/migration/V0_20_73__SeedTd003GuidedDraft.java`: corrected TD-003 draft and governed scenarios.
- `ruoyi-admin/src/main/resources/db/migration/V0_20_74__todo_schedule_purpose_and_idempotency.sql`: schedule purpose and schedule-plan idempotency.
- `ruoyi-admin/src/main/resources/db/migration/V0_20_75__lead_progress_cycle.sql`: TD-004 follow-up provenance and uniqueness.
- `ruoyi-admin/src/main/java/db/migration/V0_20_76__SeedTd004GuidedDraft.java`: corrected TD-004 draft and governed scenarios.

### Todo Engine backend

- `law-todo/src/main/java/com/law/todo/application/TodoTemplateService.java`: trigger validation and entry-slot switching primitive.
- `law-todo/src/main/java/com/law/todo/application/LeadTodoReleaseService.java`: coordinated release validation and activation.
- `law-todo/src/main/java/com/law/todo/application/TodoConfigurationQueryService.java`: exact draft hash and lifecycle/read-model output.
- `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`: business-scoped resources and effect-aware journey output.
- `law-todo/src/main/java/com/law/todo/application/TodoBusinessOutcomeCatalogService.java`: governed outcomes and effect kinds.
- `law-todo/src/main/java/com/law/todo/application/TodoSimulationEffectResolver.java`: normalize expected and actual simulation effects.
- `law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioCatalog.java`: parse backward-compatible scenario resources.
- `law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioService.java`: compare effects rather than only next-template codes.
- `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java`: persist effect-aware evidence bound to a draft hash.
- `law-todo/src/main/java/com/law/todo/application/TodoSimulationReadinessService.java`: publish gate and actionable issue coordinates.
- `law-todo/src/main/java/com/law/todo/application/view/TodoSimulationScenarioViews.java`: scenario/effect API records.
- `law-todo/src/main/java/com/law/todo/schedule/TodoScheduleService.java`: purpose-aware, idempotent schedule-plan creation.
- `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`: entry slots, schedule idempotency and release locking SQL.
- `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`: exact-version hashes, scoped resource and activation read models.

### Lead business backend

- `law-business/src/main/java/com/law/business/lead/dto/LeadProgressCompleteCommand.java`: typed TD-004 completion input.
- `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadProgressCycleService.java`: progress fact and next-cycle transaction.
- `ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadProgressHandoffTodoHandler.java`: typed TD-004 adapter and routing effect.
- `ruoyi-system/src/main/java/com/ruoyi/system/domain/BizLeadFollowup.java`: follow-up provenance fields.
- `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java`: progress insert/replay/link methods.
- `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`: idempotent progress SQL.

### API and frontend

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`: coordinated release endpoint.
- `ruoyi-ui/src/api/todo-config.js`: release API.
- `ruoyi-ui/src/views/todo/config/journey/journey-step-model.js`: effect-aware summaries, scoped targets and stable step state.
- `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`: effect labels and exact-hash evidence.
- `ruoyi-ui/src/views/todo/config/journey/components/SemanticValueRenderer.vue`: person, department, dictionary and object labels.
- `ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue`: governed outcome cards.
- `ruoyi-ui/src/views/todo/config/journey/components/ScenarioSelector.vue`: effect-aware scenario text.
- `ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue`: actionable fix locations.
- `ruoyi-ui/src/views/todo/config/journey/steps/EventStep.vue`: meaningful event fields.
- `ruoyi-ui/src/views/todo/config/journey/steps/TriggerStep.vue`: system-preset trigger explanation.
- `ruoyi-ui/src/views/todo/config/journey/steps/OwnerStep.vue`: non-destructive owner editing.
- `ruoyi-ui/src/views/todo/config/journey/steps/DodStep.vue`: recipe-first completion conditions.
- `ruoyi-ui/src/views/todo/config/journey/steps/SlaStep.vue`: retry timeline and five-day summary.
- `ruoyi-ui/src/views/todo/config/journey/steps/RoutingStep.vue`: effect-aware route editing.
- `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`: coordinated release and fix navigation.

---

### Task 1: Enforce One Active First-Contact Entry Slot

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_70__todo_trigger_entry_slots.sql`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoTriggerEntrySlotMigrationContractTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoTemplateService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoManagementCommands.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoTemplateServiceTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java`

**Interfaces:**
- Consumes: existing trigger optimistic locking and `TodoTemplateService.Actor`.
- Produces: `EntrySlotBinding switchEntrySlot(String entrySlotCode, long triggerRuleId, int expectedVersion, Actor actor)` and database uniqueness on the active slot.

- [ ] **Step 1: Write the migration contract test**

```java
@Test
void migrationDefinesOneEnabledBindingPerEntrySlot()
{
    String sql = migration("V0_20_70__todo_trigger_entry_slots.sql");
    assertThat(sql).contains("entry_slot_code", "active_entry_slot_code",
            "uk_todo_trigger_active_entry_slot", "LEAD_FIRST_CONTACT_ENTRY");
    assertThat(sql).contains("replacement_template_code", "TD-001");
}
```

- [ ] **Step 2: Run the contract test and verify it fails because the migration is absent**

Run:

```powershell
mvn -pl ruoyi-admin -am -Dtest=TodoTriggerEntrySlotMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `V0_20_70__todo_trigger_entry_slots.sql` cannot be loaded.

- [ ] **Step 3: Add the forward migration**

Use the following database contract:

```sql
alter table todo_trigger_rule
  add column entry_slot_code varchar(64) null after business_type;

alter table todo_template
  add column replacement_template_code varchar(64) null after status;

update todo_trigger_rule r
join todo_template t on t.template_id=r.template_id
set r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY'
where r.event_type='LEAD_ASSIGNED'
  and t.template_code in ('LEAD_FIRST_CONTACT','TD-001');

update todo_trigger_rule
set enabled='N',update_by='flyway-v0.20.70',update_time=sysdate(),version=version+1
where entry_slot_code='LEAD_FIRST_CONTACT_ENTRY'
  and trigger_rule_id<>(select selected_id from (
    select r.trigger_rule_id selected_id
    from todo_trigger_rule r
    join todo_template t on t.template_id=r.template_id
    join todo_template_version v on v.version_id=r.template_version_id
    where r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY'
      and t.template_code='TD-001' and v.status='PUBLISHED'
    order by v.version_no desc,r.trigger_rule_id desc limit 1
  ) selected);

alter table todo_trigger_rule
  add column active_entry_slot_code varchar(64)
    generated always as (case when enabled='Y' then entry_slot_code else null end) stored,
  add unique key uk_todo_trigger_active_entry_slot(active_entry_slot_code);

update todo_template
set status='1',replacement_template_code='TD-001',update_by='flyway-v0.20.70',update_time=sysdate()
where template_code='LEAD_FIRST_CONTACT';
```

- [ ] **Step 4: Write failing service tests for slot switching**

```java
@Test
void switchEntrySlotDisablesTheOldRuleBeforeEnablingTheNewRule()
{
    when(mapper.selectEntrySlotBindingsForUpdate("LEAD_FIRST_CONTACT_ENTRY"))
            .thenReturn(List.of(binding(41L,"Y",7),binding(52L,"N",3)));

    EntrySlotBinding result = service.switchEntrySlot(
            "LEAD_FIRST_CONTACT_ENTRY",52L,3,actor);

    InOrder order=inOrder(mapper);
    order.verify(mapper).disableEntrySlotBindings("LEAD_FIRST_CONTACT_ENTRY",52L,"alice");
    order.verify(mapper).enableEntrySlotBinding(52L,3,"alice");
    assertThat(result.triggerRuleId()).isEqualTo(52L);
}
```

Also add tests for an unknown slot, a trigger not belonging to the slot, stale version and a non-published target version.

- [ ] **Step 5: Run the service test and verify it fails on missing mapper/service methods**

Run:

```powershell
mvn -pl law-todo -am -Dtest=TodoTemplateServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL with missing `switchEntrySlot` contract.

- [ ] **Step 6: Implement the mapper and service primitive**

Add optional `entrySlotCode` to `TodoManagementCommands.TriggerCommand`, preserving overloads for existing callers. Include it in trigger insert/update SQL. Add these mapper signatures:

```java
List<Map<String,Object>> selectEntrySlotBindingsForUpdate(String entrySlotCode);
int disableEntrySlotBindings(@Param("entrySlotCode") String entrySlotCode,
        @Param("exceptTriggerRuleId") Long exceptTriggerRuleId,@Param("operator") String operator);
int enableEntrySlotBinding(@Param("triggerRuleId") Long triggerRuleId,
        @Param("expectedVersion") int expectedVersion,@Param("operator") String operator);
```

The service record is:

```java
public record EntrySlotBinding(String entrySlotCode,long triggerRuleId,
        long templateVersionId,String templateCode) { }
```

Lock all rows in the slot, validate the requested rule and version, disable old bindings, then enable the requested rule in the same transaction.

- [ ] **Step 7: Add the MySQL invariant test**

Assert:

```java
assertEquals(1, count(statement,
        "select count(*) from todo_trigger_rule where entry_slot_code='LEAD_FIRST_CONTACT_ENTRY' and enabled='Y'"));
assertEquals("TD-001", scalar(statement,
        "select t.template_code from todo_trigger_rule r join todo_template t on t.template_id=r.template_id " +
        "where r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY' and r.enabled='Y'"));
assertEquals("1", scalar(statement,
        "select status from todo_template where template_code='LEAD_FIRST_CONTACT'"));
```

- [ ] **Step 8: Run targeted and migration tests**

```powershell
mvn -pl law-todo,ruoyi-admin -am -Dtest=TodoTemplateServiceTest,TodoTriggerEntrySlotMigrationContractTest,LeadTemplateConfigurationMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 9: Commit the entry-slot change**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_70__todo_trigger_entry_slots.sql ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoTriggerEntrySlotMigrationContractTest.java ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java law-todo/src/main/resources/mapper/todo/TodoMapper.xml law-todo/src/main/java/com/law/todo/application/TodoTemplateService.java law-todo/src/main/java/com/law/todo/application/command/TodoManagementCommands.java law-todo/src/test/java/com/law/todo/application/TodoTemplateServiceTest.java
git commit -m "feat(todo): enforce unique lead entry slot"
```

---

### Task 2: Govern Lead Event Fields, Recipes, Hashes and Routing Targets

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_71__lead_todo_guided_resources.sql`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoGuidedResourcesMigrationTest.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationQueryService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoBusinessOutcomeCatalogService.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationQueryServiceTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoBusinessOutcomeCatalogServiceTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/mapper/TodoConfigurationMapperXmlContractTest.java`

**Interfaces:**
- Consumes: existing event schema and configuration resource tables.
- Produces: exact-version draft hash, business-scoped routing targets, meaningful LEAD fields and three recipe codes: `LEAD_INVALID_REVIEW_READY`, `LEAD_RETRY_READY`, `LEAD_PROGRESS_READY`.

- [ ] **Step 1: Write failing read-model tests**

```java
@Test
void draftNeverBorrowsThePublishedDefinitionHash()
{
    Map<String,Object> row=row("draft_version_id",35L,"draft_definition_hash",null,
            "published_definition_hash","published-hash");
    assertThat(service.template(row).definitionHash()).isNull();
}

@Test
void leadJourneyDoesNotExposeCaseRoutingTargets()
{
    when(mapper.selectPublishedRoutingTargetCatalog("LEAD"))
            .thenReturn(List.of(target("TD-004","LEAD")));
    assertThat(service.journey(20L).resources().routingTargets())
            .extracting("templateCode").containsExactly("TD-004");
}
```

- [ ] **Step 2: Run the tests and verify the current hash fallback and target query fail them**

```powershell
mvn -pl law-todo -am -Dtest=TodoConfigurationQueryServiceTest,TodoConfigurationJourneyServiceTest,TodoConfigurationMapperXmlContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the mapper coalesces the published hash and target selection is not business-scoped.

- [ ] **Step 3: Fix exact-version selection and routing target scope**

Replace hash/report fallback with version-aware selection:

```sql
case when draft.version_id is not null then draft.definition_hash
     else published.definition_hash end definition_hash,
case when draft.version_id is not null then draft.validation_report_json
     else published.validation_report_json end validation_report_json
```

Change the target query contract to:

```java
List<Map<String,Object>> selectPublishedRoutingTargetCatalog(String businessType);
```

and require `t.business_type=#{businessType}` and `t.status='0'` in SQL.

- [ ] **Step 4: Write the resource migration contract test**

Assert the migration contains meaningful metadata for:

```text
LEAD_SUSPECT_INVALID_MARKED: leadId, reviewId, reasonCode, ownerId, reviewerId, operatorId
LEAD_RETRY_WINDOW_DUE: leadId, planId, windowCode, occurrenceNo, ownerId
LEAD_FIRST_CONTACT_VALID: leadId, followupId, ownerId, operatorId, contactResult
```

Require `x-semantic-type`, `x-option-source`, `x-dict-type`, `title`, `description` and `x-owner-eligible` where applicable.

- [ ] **Step 5: Add governed LEAD resources**

Seed or update these resources idempotently:

```text
FIELD: reviewResult, reviewOpinion, contactResult, name, city, demand, visited,
       progressType, progressAt, remark
MATERIAL: CONTACT_PROOF, FOLLOWUP_PROOF
DOD_RECIPE: LEAD_INVALID_REVIEW_READY, LEAD_RETRY_READY, LEAD_PROGRESS_READY
```

The TD-004 recipe value must be:

```json
{
  "requiredFields": ["progressType", "progressAt"],
  "requiredAttachments": ["FOLLOWUP_PROOF"],
  "validatorRefs": [],
  "conditionalRules": []
}
```

- [ ] **Step 6: Add governed outcome sets with explicit effect kinds**

Return these exact values:

```java
Map.of(
  "TD-002", List.of(outcome("TRUE_INVALID","确认无效","END",null),
                     outcome("MISJUDGED_VALID","误判有效","NEXT_TEMPLATE","TD-001")),
  "TD-003", List.of(outcome("CONNECTED","联系成功","NEXT_TEMPLATE","TD-004"),
                     outcome("CONTINUE_CURRENT_WINDOW","本窗口继续","RETAIN_CURRENT",null),
                     outcome("NEXT_WINDOW","进入下一窗口","SCHEDULE_NEXT",null),
                     outcome("EXHAUSTED","全部重试耗尽","END",null)),
  "TD-004", List.of(outcome("PROGRESS_RECORDED","已记录实质进展","SCHEDULE_SELF","TD-004"))
);
```

Outcome records must carry `effectKind` and optional `targetTemplateCode`; arbitrary labels or cross-business targets are rejected.

- [ ] **Step 7: Run backend and migration contract tests**

```powershell
mvn -pl law-todo,ruoyi-admin -am -Dtest=TodoConfigurationQueryServiceTest,TodoConfigurationJourneyServiceTest,TodoBusinessOutcomeCatalogServiceTest,TodoConfigurationMapperXmlContractTest,LeadTodoGuidedResourcesMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 8: Commit the governed resource change**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_71__lead_todo_guided_resources.sql ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoGuidedResourcesMigrationTest.java law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationQueryService.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java law-todo/src/main/java/com/law/todo/application/TodoBusinessOutcomeCatalogService.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationQueryServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoBusinessOutcomeCatalogServiceTest.java law-todo/src/test/java/com/law/todo/mapper/TodoConfigurationMapperXmlContractTest.java
git commit -m "feat(todo): govern lead configuration resources"
```

---

### Task 3: Add Effect-Aware Simulation and Exact Draft Evidence

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoSimulationEffectResolver.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoSimulationEffectResolverTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoSimulationScenarioViews.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioCatalog.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationReadinessService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceManagementService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioServiceTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoSimulationReadinessServiceTest.java`

**Interfaces:**
- Consumes: governed outcome `effectKind`, routing simulation result and exact definition hash.
- Produces: `SimulationEffect`, backward-compatible scenario parsing and effect-aware evidence.

- [ ] **Step 1: Define tests for every supported effect**

```java
@ParameterizedTest
@CsvSource({
  "NEXT_TEMPLATE,TD-004",
  "END,",
  "RETAIN_CURRENT,",
  "SCHEDULE_NEXT,",
  "SCHEDULE_SELF,TD-004",
  "EXPECTED_VALIDATION_FAILURE,"
})
void parsesGovernedExpectedEffects(String kind,String target)
{
    SimulationEffect effect=resolver.expected(json(kind,target));
    assertThat(effect.kind()).isEqualTo(EffectKind.valueOf(kind));
    assertThat(effect.targetTemplateCode()).isEqualTo(blankToNull(target));
}
```

Add a compatibility test proving legacy `expectedNextTemplateCode: "TD-004"` becomes `NEXT_TEMPLATE/TD-004`.

- [ ] **Step 2: Run tests and verify they fail because effects do not exist**

```powershell
mvn -pl law-todo -am -Dtest=TodoSimulationEffectResolverTest,TodoSimulationScenarioCatalogTest,TodoSimulationScenarioServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL on missing effect types and the current non-blank next-template requirement.

- [ ] **Step 3: Add the effect model and compatibility parser**

```java
public enum EffectKind
{
    NEXT_TEMPLATE, END, RETAIN_CURRENT, SCHEDULE_NEXT, SCHEDULE_SELF,
    EXPECTED_VALIDATION_FAILURE
}

public record SimulationEffect(EffectKind kind,String targetTemplateCode,String actionCode)
{
    public SimulationEffect
    {
        if ((kind==EffectKind.NEXT_TEMPLATE||kind==EffectKind.SCHEDULE_SELF)
                && (targetTemplateCode==null||targetTemplateCode.isBlank()))
            throw new TodoException("TODO_SIMULATION_EFFECT_TARGET_REQUIRED",
                    "Simulation effect requires a target template");
    }
}
```

Add `SimulationEffect expectedEffect` to `SimulationScenario` and keep `expectedNextTemplateCode` in the serialized view during the compatibility window.

- [ ] **Step 4: Resolve actual effects**

`TodoSimulationEffectResolver.actual(...)` follows this order:

1. a generated next Todo means `NEXT_TEMPLATE`;
2. an outcome catalog entry with `RETAIN_CURRENT`, `SCHEDULE_NEXT`, `SCHEDULE_SELF` or `END` returns that effect;
3. a normal graph ending without metadata is `END`;
4. a thrown validation exception matches only an `EXPECTED_VALIDATION_FAILURE` scenario with the same `expectedErrorCode`.

- [ ] **Step 5: Compare effects and persist effect-aware evidence**

The comparison is:

```java
boolean passed = expected.kind()==actual.kind()
        && Objects.equals(expected.targetTemplateCode(),actual.targetTemplateCode())
        && simulationHashMatches;
```

Evidence trace summary must include `expectedEffect`, `actualEffect`, `definitionHash`, `scenarioCode` and `scenarioVersion`, but continue to redact business payload values.

- [ ] **Step 6: Make resource validation accept terminal and scheduled scenarios**

Replace the current unconditional `expectedNextTemplateCode` requirement with:

```java
SimulationEffect effect=resolver.expected(valueJson);
require(effect!=null,"TODO_SIMULATION_EXPECTED_EFFECT_REQUIRED");
```

Require `expectedErrorCode` only for `EXPECTED_VALIDATION_FAILURE`.

- [ ] **Step 7: Add actionable readiness coordinates**

Extend the existing `JourneyIssue` with a resource key while retaining an overload for existing six-argument callers:

```java
public record JourneyIssue(String code,String severity,String stepCode,String fieldPath,
        String message,String repairAction,String resourceKey)
{
    public JourneyIssue(String code,String severity,String stepCode,String fieldPath,
            String message,String repairAction)
    {this(code,severity,stepCode,fieldPath,message,repairAction,null);}
}
```

Map scenario gaps to `SIMULATION`, route gaps to `ROUTING`, SLA gaps to `SLA`, and field/condition gaps to their exact step.

- [ ] **Step 8: Run all simulation tests**

```powershell
mvn -pl law-todo -am -Dtest=TodoSimulationEffectResolverTest,TodoSimulationScenarioCatalogTest,TodoSimulationScenarioServiceTest,TodoSimulationEvidenceServiceTest,TodoSimulationReadinessServiceTest,TodoConfigurationResourceManagementServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 9: Commit the simulation model**

```powershell
git add law-todo/src/main/java/com/law/todo/application/TodoSimulationEffectResolver.java law-todo/src/main/java/com/law/todo/application/view/TodoSimulationScenarioViews.java law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioCatalog.java law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioService.java law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java law-todo/src/main/java/com/law/todo/application/TodoSimulationReadinessService.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceManagementService.java law-todo/src/test/java/com/law/todo/application/TodoSimulationEffectResolverTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationReadinessServiceTest.java
git commit -m "feat(todo): support governed simulation effects"
```

---

### Task 4: Make the Seven-Step Journey Preserve and Explain Business Configuration

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/journey/business-effect-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/index.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/journey-step-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/SemanticValueRenderer.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/ScenarioSelector.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/EventStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/TriggerStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/OwnerStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/DodStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SlaStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/RoutingStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Modify: `ruoyi-ui/scripts/check-todo-journey-model.js`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: semantic fields, business outcome `effectKind`, `ReadinessIssue` coordinates and exact draft hash.
- Produces: Chinese summaries, recipe-first editing, stable step drafts, effect cards and working “返回修复”.

- [ ] **Step 1: Add failing Node contract cases**

Add assertions equivalent to:

```js
assert.strictEqual(renderSemantic({ semanticType: 'USER_ID', displayValue: '张三', departmentName: '销售一部' }), '张三 / 销售一部')
assert.deepStrictEqual(effectPresentation({ kind: 'SCHEDULE_SELF', targetTemplateCode: 'TD-004' }), {
  label: '完成后开启下一轮5天待办', needsTarget: false, tone: 'primary'
})
assert.strictEqual(routeTargetsFor('LEAD', [{ businessType: 'LEAD' }, { businessType: 'CASE' }]).length, 1)
assert.strictEqual(fixLocation({ stepKey: 'ROUTING', fieldPath: 'routing.businessOutcomes' }).step, 6)
```

Also mount or structurally check that changing owner source does not emit an empty owner before explicit confirmation.

- [ ] **Step 2: Run UI contracts and verify failures**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
npm run test:todo-config
```

Expected: FAIL on effect presentation, fix location or non-destructive owner handling.

- [ ] **Step 3: Implement the shared effect presentation model**

```js
export const EFFECT_LABELS = Object.freeze({
  NEXT_TEMPLATE: '生成下一待办',
  END: '结束当前路径',
  RETAIN_CURRENT: '保留当前待办',
  SCHEDULE_NEXT: '等待系统计划下一窗口',
  SCHEDULE_SELF: '完成后开启下一周期',
  EXPECTED_VALIDATION_FAILURE: '预期校验失败'
})

export function effectPresentation(effect = {}) {
  const kind = effect.kind || 'END'
  return { kind, label: EFFECT_LABELS[kind], needsTarget: kind === 'NEXT_TEMPLATE',
    targetTemplateCode: effect.targetTemplateCode || '' }
}
```

- [ ] **Step 4: Render semantic values with business labels**

Use `displayValue`, `userName`, `departmentName`, `dictLabel` and `businessDisplayName` in that order. Put `rawValue` in a collapsed “技术详情” line, never as the main option label.

- [ ] **Step 5: Preserve step state until an explicit replacement**

Maintain one deep-cloned `journeyDraft` keyed by template/version. Child steps emit patches, not full replacements. `OwnerStep` uses:

```js
confirmOwnerReplacement(nextOwner) {
  this.$emit('patch', { owner: { config: clone(nextOwner) } })
}
```

Navigating between steps must not reconstruct owner, DoD, SLA or routing from defaults.

- [ ] **Step 6: Make preset-only steps explicit**

- `TriggerStep`: show “该业务事件发生即触发，无需附加条件” when condition is empty and governed.
- `DodStep`: show recipe card first and move validator IDs into an advanced drawer.
- `SlaStep`: show retry windows as a timeline for TD-003 and “每 5 天循环” for TD-004.
- `RoutingStep`: show effect cards and suppress target selection for END, RETAIN_CURRENT, SCHEDULE_NEXT and SCHEDULE_SELF.

- [ ] **Step 7: Implement working fix navigation and exact-hash evidence**

`ConfigurationHealthPanel` emits the full issue. `SimulationPublishStep` forwards it. `index.vue` changes the active step and opens the referenced control using `resourceKey` or `fieldPath`. Never mark evidence successful when `readiness.definitionHash !== template.definitionHash`.

- [ ] **Step 8: Run UI contracts and production build**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
npm run test:todo-config
npm run build:prod
```

Expected: all commands PASS; existing size warnings are allowed, compilation errors are not.

- [ ] **Step 9: Commit the shared journey UX**

```powershell
git add ruoyi-ui/src/views/todo/config/journey ruoyi-ui/scripts/check-todo-journey-model.js ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo-ui): clarify governed lead configuration"
```

---

### Task 5: Seed and Verify the TD-002 Guided Draft

**Files:**
- Create: `ruoyi-admin/src/main/java/db/migration/V0_20_72__SeedTd002GuidedDraft.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/Td002GuidedDraftMigrationTest.java`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/TD-002.json`
- Modify: `law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java`

**Interfaces:**
- Consumes: `LEAD_INVALID_REVIEW_READY`, effect-aware scenarios and existing `LeadInvalidReviewTodoHandler`.
- Produces: a new TD-002 draft with no independent trigger and three governed scenarios.

- [ ] **Step 1: Write the canonical-definition assertions**

```java
assertDefinition("TD-002", definition -> {
    assertThat(definition.event().eventType()).isEqualTo("LEAD_SUSPECT_INVALID_MARKED");
    assertThat(definition.owner().config()).containsEntry("type","PAYLOAD")
            .containsEntry("field","reviewerId");
    assertThat(definition.sla().config()).containsEntry("minutes",1440)
            .containsEntry("onDue","COMPLETE_DEFAULT");
    assertOutcomes(definition,"TRUE_INVALID","END","MISJUDGED_VALID","NEXT_TEMPLATE");
});
```

- [ ] **Step 2: Run the contract test and verify the current definition lacks guided outcomes/scenarios**

```powershell
mvn -pl law-todo -am -Dtest=LeadTodoPublishedTemplateContractTest,TodoSimulationScenarioCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL on the new governed-effect assertions.

- [ ] **Step 3: Update the canonical TD-002 definition**

The definition must contain:

```json
"businessOutcomes": [
  {"field":"reviewResult","value":"TRUE_INVALID","label":"确认无效","effectKind":"END","businessAction":"MOVE_DEAD_POOL"},
  {"field":"reviewResult","value":"MISJUDGED_VALID","label":"误判有效","effectKind":"NEXT_TEMPLATE","targetTemplateCode":"TD-001","businessAction":"REOPEN_FIRST_CONTACT"}
]
```

Keep the controlled due action with `reviewResult=TRUE_INVALID` and the existing real handler capability.

- [ ] **Step 4: Create a forward Java migration that inserts a fresh TD-002 draft**

The migration reads the canonical resource, resolves the current published TD-001 version for the MISJUDGED_VALID target, writes a new DRAFT version, and inserts or updates these required scenario resources:

```json
{"scenarioCode":"TD002_TRUE_INVALID","expectedEffect":{"kind":"END"},"completionPayload":{"reviewResult":"TRUE_INVALID","reviewOpinion":"确认无效"}}
{"scenarioCode":"TD002_MISJUDGED_VALID","expectedEffect":{"kind":"NEXT_TEMPLATE","targetTemplateCode":"TD-001"},"completionPayload":{"reviewResult":"MISJUDGED_VALID","reviewOpinion":"复核为误判"}}
{"scenarioCode":"TD002_OVERDUE_DEFAULT","expectedEffect":{"kind":"END"},"completionPayload":{"reviewResult":"TRUE_INVALID","reviewOpinion":"系统超时默认确认"},"automatic":true}
```

Do not publish or enable a trigger in the migration.

- [ ] **Step 5: Add MySQL assertions**

Verify the newest TD-002 draft has event, owner, DoD, SLA, route effects and no enabled trigger rule. Verify all three scenario resources are active and required for publish.

- [ ] **Step 6: Run TD-002 tests**

```powershell
mvn -pl law-todo,ruoyi-admin -am -Dtest=LeadTodoPublishedTemplateContractTest,TodoSimulationScenarioCatalogTest,Td002GuidedDraftMigrationTest,LeadTemplateConfigurationMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit TD-002**

```powershell
git add law-todo/src/main/resources/todo-definitions/v0.2/TD-002.json law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java ruoyi-admin/src/main/java/db/migration/V0_20_72__SeedTd002GuidedDraft.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/Td002GuidedDraftMigrationTest.java ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java
git commit -m "feat(todo): prepare governed TD-002 draft"
```

---

### Task 6: Seed and Verify the TD-003 Retry-Window Draft

**Files:**
- Create: `ruoyi-admin/src/main/java/db/migration/V0_20_73__SeedTd003GuidedDraft.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/Td003GuidedDraftMigrationTest.java`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/TD-003.json`
- Modify: `law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java`

**Interfaces:**
- Consumes: existing retry plan/service/handler, `LEAD_RETRY_READY` and effect-aware scenarios.
- Produces: a new TD-003 draft bound to `LEAD_RETRY_WINDOW_DUE` with four governed runtime outcomes.

- [ ] **Step 1: Write failing TD-003 assertions**

```java
assertDefinition("TD-003", definition -> {
    assertThat(definition.event().eventType()).isEqualTo("LEAD_RETRY_WINDOW_DUE");
    assertThat(definition.owner().config()).containsEntry("type","BUSINESS_OWNER")
            .containsEntry("businessType","LEAD");
    assertThat(requiredFields(definition)).containsExactly("contactResult");
    assertThat(readOnlyUiFields(definition)).contains("attemptStage","attemptCount");
    assertOutcomes(definition,"CONNECTED","NEXT_TEMPLATE",
            "CONTINUE_CURRENT_WINDOW","RETAIN_CURRENT",
            "NEXT_WINDOW","SCHEDULE_NEXT","EXHAUSTED","END");
});
```

- [ ] **Step 2: Run tests and verify the current draft/event behavior fails**

```powershell
mvn -pl law-todo -am -Dtest=LeadTodoPublishedTemplateContractTest,TodoSimulationScenarioCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL on missing governed effects or scenario count.

- [ ] **Step 3: Update the canonical TD-003 definition**

Keep T0/T+1/T+2 schedule windows. Remove `attemptStage` and `attemptCount` from required fields while retaining them as read-only UI context. Add:

```json
"businessOutcomes": [
  {"value":"CONNECTED","label":"联系成功","effectKind":"NEXT_TEMPLATE","targetTemplateCode":"TD-004"},
  {"value":"CONTINUE_CURRENT_WINDOW","label":"本窗口继续","effectKind":"RETAIN_CURRENT"},
  {"value":"NEXT_WINDOW","label":"进入下一窗口","effectKind":"SCHEDULE_NEXT"},
  {"value":"EXHAUSTED","label":"全部重试耗尽","effectKind":"END"}
]
```

- [ ] **Step 4: Seed a new draft and four scenarios**

Use scenario codes and expected effects:

```text
TD003_CONNECTED             NEXT_TEMPLATE/TD-004
TD003_CONTINUE_WINDOW       RETAIN_CURRENT
TD003_NEXT_WINDOW           SCHEDULE_NEXT
TD003_EXHAUSTED             END
```

The connected payload includes controlled name, city, demand and visited values plus `CONTACT_PROOF`. The other cases include contact proof and the authoritative attempt/window context in the sample, not in editable fields.

- [ ] **Step 5: Assert no independent TD-003 event trigger is enabled**

```java
assertEquals(0,count(statement,
        "select count(*) from todo_trigger_rule r join todo_template t on t.template_id=r.template_id " +
        "where t.template_code='TD-003' and r.enabled='Y'"));
```

- [ ] **Step 6: Run TD-003 and retry regression tests**

```powershell
mvn -pl law-todo,ruoyi-system,ruoyi-admin -am -Dtest=LeadTodoPublishedTemplateContractTest,TodoSimulationScenarioCatalogTest,Td003GuidedDraftMigrationTest,LeadTemplateConfigurationMySqlIT,LeadTodoProductionPortsExternalMysqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit TD-003**

```powershell
git add law-todo/src/main/resources/todo-definitions/v0.2/TD-003.json law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java ruoyi-admin/src/main/java/db/migration/V0_20_73__SeedTd003GuidedDraft.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/Td003GuidedDraftMigrationTest.java ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java
git commit -m "feat(todo): prepare governed TD-003 draft"
```

---

### Task 7: Generalize Schedule Plans for Idempotent Progress Cycles

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_74__todo_schedule_purpose_and_idempotency.sql`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSchedulePurposeMigrationTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/schedule/TodoScheduleService.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Modify: `law-todo/src/test/java/com/law/todo/schedule/TodoScheduleServiceTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadFirstContactService.java`

**Interfaces:**
- Consumes: existing schedule materializer and occurrence fencing.
- Produces: `SchedulePurpose`, idempotent `createPlan`, and support for published TD-003 or TD-004 targets.

- [ ] **Step 1: Write failing schedule tests**

```java
@Test
void progressPlanCanTargetPublishedTd004AndReplayByIdempotencyKey()
{
    CreateSchedulePlanCommand command=progressCommand("LEAD_PROGRESS_5D:91:7001");
    when(mapper.selectSchedulePlanByIdempotencyKey(command.idempotencyKey()))
            .thenReturn(null,Map.of("planId",81L));
    assertThat(service.createPlan(command)).isEqualTo(81L);
    assertThat(service.createPlan(command)).isEqualTo(81L);
    verify(mapper,times(1)).insertSchedulePlan(anyMap());
}
```

Also keep a regression test proving the old retry constructor still produces `LEAD_RETRY`.

- [ ] **Step 2: Run the schedule tests and verify failure**

```powershell
mvn -pl law-todo -am -Dtest=TodoScheduleServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because template validation is hard-coded to TD-003 and plans have no idempotency key.

- [ ] **Step 3: Add the database columns**

```sql
alter table todo_schedule_plan
  add column schedule_purpose varchar(32) not null default 'LEAD_RETRY' after business_id,
  add column idempotency_key varchar(192) null after schedule_purpose;

update todo_schedule_plan
set idempotency_key=concat('LEAD_RETRY:',business_id,':',previous_todo_id)
where idempotency_key is null;

alter table todo_schedule_plan
  modify column idempotency_key varchar(192) not null,
  add unique key uk_todo_schedule_plan_idempotency(idempotency_key),
  add key idx_todo_schedule_plan_purpose(schedule_purpose,business_type,business_id,status);
```

- [ ] **Step 4: Extend the command and purpose enum without breaking retry callers**

```java
public enum SchedulePurpose
{
    LEAD_RETRY("TD-003"), LEAD_PROGRESS_5D("TD-004");
    private final String requiredTemplateCode;
}

public record CreateSchedulePlanCommand(
        Long previousTodoId,Long templateVersionId,String businessType,Long businessId,
        LocalDateTime firstContactCompletedAt,String timezone,Long ruleVersionId,
        Long assignmentPolicyId,Integer assignmentPolicyVersion,List<ScheduleWindowRule> windows,
        SchedulePurpose purpose,String idempotencyKey) { }
```

Keep the existing constructors and have them derive `LEAD_RETRY:{businessId}:{previousTodoId}`.

- [ ] **Step 5: Implement idempotent create and purpose-aware template validation**

Before insert, select by idempotency key. After a duplicate-key exception, select again and verify template version, business identity and purpose match; otherwise raise `TODO_SCHEDULE_IDEMPOTENCY_CONFLICT`.

- [ ] **Step 6: Run schedule and existing retry tests**

```powershell
mvn -pl law-todo,ruoyi-system,ruoyi-admin -am -Dtest=TodoScheduleServiceTest,LeadTodoProductionPortsExternalMysqlIT,TodoSchedulePurposeMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit schedule generalization**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_74__todo_schedule_purpose_and_idempotency.sql ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSchedulePurposeMigrationTest.java law-todo/src/main/java/com/law/todo/schedule/TodoScheduleService.java law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java law-todo/src/main/resources/mapper/todo/TodoMapper.xml law-todo/src/test/java/com/law/todo/schedule/TodoScheduleServiceTest.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadFirstContactService.java
git commit -m "feat(todo): support idempotent schedule purposes"
```

---

### Task 8: Persist TD-004 Progress and Create the Next Five-Day Plan

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_75__lead_progress_cycle.sql`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadProgressCycleMigrationTest.java`
- Create: `law-business/src/main/java/com/law/business/lead/dto/LeadProgressCompleteCommand.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadProgressCycleService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadProgressCycleServiceTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadProgressHandoffTodoHandler.java`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadProgressHandoffTodoHandlerTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/domain/BizLeadFollowup.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAssignmentPolicyService.java`

**Interfaces:**
- Consumes: `TodoScheduleService.createPlan`, current lead owner, TD-004 template version and existing Todo attachments.
- Produces: `ProgressCycleOutcome(long followupId,long schedulePlanId,LocalDateTime nextDueAt,boolean replayed)` and handler routing result `PROGRESS_RECORDED`.

- [ ] **Step 1: Write failing service tests**

```java
@Test
void completionWritesOneProgressFactAndOneFiveDayPlan()
{
    ProgressCycleOutcome result=service.complete(command(7001L,"PHONE",NOW),todo(7001L,88L));
    assertThat(result.nextDueAt()).isEqualTo(NOW.plusDays(5));
    verify(mapper).insertProgressFollowupIfAbsent(argThat(row ->
            "LEAD_PROGRESS:7001".equals(row.getIdempotencyKey())));
    verify(schedules).createPlan(argThat(plan ->
            plan.purpose()==SchedulePurpose.LEAD_PROGRESS_5D
            && plan.templateVersionId()==88L
            && plan.windows().get(0).durationMinutes()==7200));
}

@Test
void replayReturnsTheExistingFactAndPlanWithoutCreatingAnotherPlan()
{
    when(mapper.selectProgressFollowupByIdempotencyKey("LEAD_PROGRESS:7001"))
            .thenReturn(existingFollowup(90L,81L));
    assertThat(service.complete(command(7001L,"PHONE",NOW),todo(7001L,88L)).replayed()).isTrue();
    verifyNoInteractions(schedules);
}
```

- [ ] **Step 2: Run tests and verify failure on missing service**

```powershell
mvn -pl ruoyi-system -am -Dtest=LeadProgressCycleServiceTest,LeadProgressHandoffTodoHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `LeadProgressCycleService` and typed command do not exist.

- [ ] **Step 3: Add follow-up provenance columns**

```sql
alter table biz_lead_followup
  add column progress_at datetime null after follow_result,
  add column source_todo_id bigint null after follow_user_id,
  add column schedule_plan_id bigint null after source_todo_id,
  add column idempotency_key varchar(192) null after schedule_plan_id,
  add unique key uk_biz_lead_followup_idempotency(idempotency_key),
  add key idx_biz_lead_followup_progress(lead_id,progress_at);
```

Existing records remain nullable and unchanged.

- [ ] **Step 4: Add the typed command**

```java
public class LeadProgressCompleteCommand
{
    @NotNull private Long leadId;
    @NotNull private Long todoId;
    @NotBlank private String progressType;
    @NotNull private LocalDateTime progressAt;
    private String remark;
}
```

- [ ] **Step 5: Implement idempotent mapper methods**

```java
int insertProgressFollowupIfAbsent(BizLeadFollowup followup);
BizLeadFollowup selectProgressFollowupByIdempotencyKey(String idempotencyKey);
int linkProgressFollowupSchedule(@Param("followupId") Long followupId,
        @Param("schedulePlanId") Long schedulePlanId,@Param("operator") String operator);
```

The insert stores `follow_type=progressType`, `follow_result=SUBSTANTIVE_PROGRESS`, `progress_at`, `next_follow_time=progressAt+5 days`, owner, source Todo and idempotency key.

- [ ] **Step 6: Implement `LeadProgressCycleService`**

Use idempotency key `LEAD_PROGRESS:{sourceTodoId}`. Validate:

- Todo code is TD-004 and business type is LEAD;
- current actor is the lead owner or administrator;
- lead remains active;
- `law_lead_progress_type` contains the submitted value;
- `progressAt` is not materially in the future;
- DoD has already guaranteed at least one `FOLLOWUP_PROOF` attachment.

Resolve the active assignment-policy snapshot, then create one schedule window:

```java
new ScheduleWindowRule("P5D",0,0,null,null,0,7200,1,1)
```

Use purpose `LEAD_PROGRESS_5D`, current TD-004 version and idempotency key `LEAD_PROGRESS_5D:{leadId}:{sourceTodoId}`.

- [ ] **Step 7: Replace the no-op handler**

```java
@Override
public CompletionResult handle(CompletionContext context)
{
    ProgressCycleOutcome outcome=cycles.complete(map(context),context.todo());
    return CompletionResult.completeTodo(Map.of(
            "result","PROGRESS_RECORDED",
            "followupId",outcome.followupId(),
            "schedulePlanId",outcome.schedulePlanId(),
            "nextDueAt",outcome.nextDueAt().toString(),
            "replayed",outcome.replayed()));
}
```

- [ ] **Step 8: Run service, mapper and transaction tests**

```powershell
mvn -pl law-business,ruoyi-system,ruoyi-admin -am -Dtest=LeadProgressCycleServiceTest,LeadProgressHandoffTodoHandlerTest,LeadProgressCycleMigrationTest,LeadTodoTransactionRollbackTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 9: Commit TD-004 runtime**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_75__lead_progress_cycle.sql ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadProgressCycleMigrationTest.java law-business/src/main/java/com/law/business/lead/dto/LeadProgressCompleteCommand.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadProgressCycleService.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadProgressCycleServiceTest.java ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadProgressHandoffTodoHandler.java ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadProgressHandoffTodoHandlerTest.java ruoyi-system/src/main/java/com/ruoyi/system/domain/BizLeadFollowup.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAssignmentPolicyService.java
git commit -m "feat(lead): refresh five-day progress cycle"
```

---

### Task 9: Seed and Verify the TD-004 Guided Draft

**Files:**
- Create: `ruoyi-admin/src/main/java/db/migration/V0_20_76__SeedTd004GuidedDraft.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/Td004GuidedDraftMigrationTest.java`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/TD-004.json`
- Modify: `law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java`

**Interfaces:**
- Consumes: `LEAD_PROGRESS_READY`, `SCHEDULE_SELF`, and `TD-004_COMPLETE` runtime capability.
- Produces: a new TD-004 draft with a real five-day recurrence effect and three governed scenarios.

- [ ] **Step 1: Write failing canonical assertions**

```java
assertDefinition("TD-004", definition -> {
    assertThat(definition.event().eventType()).isEqualTo("LEAD_FIRST_CONTACT_VALID");
    assertThat(definition.owner().config()).containsEntry("type","BUSINESS_OWNER")
            .containsEntry("businessType","LEAD");
    assertThat(requiredFields(definition)).containsExactly("progressType","progressAt");
    assertThat(materialTypes(definition)).containsExactly("FOLLOWUP_PROOF");
    assertOutcomes(definition,"PROGRESS_RECORDED","SCHEDULE_SELF");
});
```

- [ ] **Step 2: Run the test and verify the current END-only definition fails**

```powershell
mvn -pl law-todo -am -Dtest=LeadTodoPublishedTemplateContractTest,TodoSimulationScenarioCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because TD-004 still declares the old stage-3 handoff boundary.

- [ ] **Step 3: Update the canonical definition**

Replace the old handoff note and END-only business outcome with:

```json
"businessOutcomes": [
  {
    "field":"result",
    "value":"PROGRESS_RECORDED",
    "label":"已记录实质进展",
    "effectKind":"SCHEDULE_SELF",
    "targetTemplateCode":"TD-004",
    "businessAction":"REFRESH_FIVE_DAY_WINDOW"
  }
]
```

The routing graph itself still ends after the current task. The schedule effect, not a graph self-loop, creates the next TD-004.

- [ ] **Step 4: Seed a new draft and three scenarios**

```json
{"scenarioCode":"TD004_PROGRESS_RECORDED","expectedEffect":{"kind":"SCHEDULE_SELF","targetTemplateCode":"TD-004"},"completionPayload":{"progressType":"PHONE","progressAt":"2026-07-31T10:00:00","remark":"完成有效沟通"},"requiredMaterials":["FOLLOWUP_PROOF"]}
{"scenarioCode":"TD004_IDEMPOTENT_REPLAY","expectedEffect":{"kind":"SCHEDULE_SELF","targetTemplateCode":"TD-004"},"completionPayload":{"progressType":"WECHAT","progressAt":"2026-07-31T11:00:00"},"requiredMaterials":["FOLLOWUP_PROOF"],"replay":true}
{"scenarioCode":"TD004_PROOF_REQUIRED","expectedEffect":{"kind":"EXPECTED_VALIDATION_FAILURE"},"expectedErrorCode":"TODO_DOD_ATTACHMENT_MISSING","completionPayload":{"progressType":"PHONE","progressAt":"2026-07-31T10:00:00"}}
```

- [ ] **Step 5: Run TD-004 tests**

```powershell
mvn -pl law-todo,ruoyi-system,ruoyi-admin -am -Dtest=LeadTodoPublishedTemplateContractTest,TodoSimulationScenarioCatalogTest,Td004GuidedDraftMigrationTest,LeadTemplateConfigurationMySqlIT,LeadProgressCycleServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit TD-004 configuration**

```powershell
git add law-todo/src/main/resources/todo-definitions/v0.2/TD-004.json law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java ruoyi-admin/src/main/java/db/migration/V0_20_76__SeedTd004GuidedDraft.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/Td004GuidedDraftMigrationTest.java ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java
git commit -m "feat(todo): prepare recurring TD-004 draft"
```

---

### Task 10: Add Coordinated Lead Release Activation

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/LeadTodoReleaseService.java`
- Create: `law-todo/src/test/java/com/law/todo/application/LeadTodoReleaseServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java`
- Modify: `ruoyi-ui/src/api/todo-config.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: four published versions with current-hash evidence, TD-001 numeric route targets and `switchEntrySlot`.
- Produces: `POST /todo/config/lead-release/activate`, `LeadReleaseView`, current active binding display and atomic entry switch.

- [ ] **Step 1: Write failing release-service tests**

```java
@Test
void activatesOnlyWhenTd001TargetsTheApprovedDownstreamVersions()
{
    LeadReleaseView view=service.activate(new LeadReleaseCommand(
            "release-20260731",88L,"hash-88",80L,89L,79L,3),actor);
    assertThat(view.entrySlotCode()).isEqualTo("LEAD_FIRST_CONTACT_ENTRY");
    assertThat(view.activeTd001VersionId()).isEqualTo(88L);
    verify(templates).switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,actor);
}
```

Add failures for missing passing evidence, a draft/unpublished version, a TD-001 graph pointing to different downstream IDs, a legacy template target and stale trigger version.

- [ ] **Step 2: Run tests and verify failure on missing service/API**

```powershell
mvn -pl law-todo,ruoyi-admin -am -Dtest=LeadTodoReleaseServiceTest,TodoConfigurationControllerJourneyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the coordinated release service is absent.

- [ ] **Step 3: Add the release command and view**

```java
public record LeadReleaseCommand(@NotBlank String actionId,
        @NotNull @Positive Long td001VersionId,@NotBlank String td001DefinitionHash,
        @NotNull @Positive Long td002VersionId,@NotNull @Positive Long td003VersionId,
        @NotNull @Positive Long td004VersionId,@NotNull @PositiveOrZero Integer triggerExpectedVersion) { }

public record LeadReleaseView(String entrySlotCode,long activeTriggerRuleId,
        long activeTd001VersionId,Map<String,Long> downstreamVersions,
        LocalDateTime activatedAt,String activatedBy) { }
```

- [ ] **Step 4: Implement release validation and activation**

Within one transaction:

1. lock the `LEAD_FIRST_CONTACT_ENTRY` binding;
2. load all four versions and require `PUBLISHED` plus matching template codes;
3. require current-hash passing evidence for every required scenario and the full simulation;
4. parse TD-001 compiled routing and require exact TD-002/TD-003/TD-004 version IDs;
5. require no cross-business target;
6. find the disabled slot trigger bound to the requested TD-001 version, or create it with deterministic code `TRIGGER_LEAD_ASSIGNED_TD001_V{versionNo}`, `entrySlotCode=LEAD_FIRST_CONTACT_ENTRY` and `enabled=N`;
7. switch the entry slot to that rule;
8. write an idempotent configuration action record;
9. return the active release view.

- [ ] **Step 5: Add the secured endpoint**

```java
@PreAuthorize("@ss.hasPermi('todo:definition:publish')")
@PostMapping("/lead-release/activate")
public AjaxResult activateLeadRelease(@Valid @RequestBody LeadReleaseCommand command)
{
    return AjaxResult.success(leadReleases.activate(command,actor()));
}
```

- [ ] **Step 6: Add the active-binding UI**

`SimulationPublishStep` shows:

```text
当前入口：线索已分配 -> TD-001 v{version}
下游版本：TD-002 v{x} / TD-003 v{y} / TD-004 v{z}
```

The activation button is disabled until all four versions have current-hash passing evidence. The action asks for confirmation and refreshes the journey and template list after success.

- [ ] **Step 7: Run backend, UI contracts and build**

```powershell
mvn -pl law-todo,ruoyi-admin -am -Dtest=LeadTodoReleaseServiceTest,TodoConfigurationControllerJourneyTest -Dsurefire.failIfNoSpecifiedTests=false test
Set-Location ruoyi-ui
npm run test:todo-phase-two
npm run test:todo-config
npm run build:prod
```

Expected: PASS.

- [ ] **Step 8: Commit coordinated activation**

```powershell
git add law-todo/src/main/java/com/law/todo/application/LeadTodoReleaseService.java law-todo/src/test/java/com/law/todo/application/LeadTodoReleaseServiceTest.java law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java ruoyi-ui/src/api/todo-config.js ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo): activate coordinated lead template releases"
```

---

### Task 11: Run the Real MySQL and Browser Acceptance Flow

**Files:**
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoGuidedConfigurationExternalMysqlIT.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoFlowEndToEndTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`
- Modify: `ruoyi-ui/tests/e2e/todo-config-journey.spec.js`
- Modify: `ruoyi-ui/scripts/check-todo-config-real-e2e.js`
- Create: `docs/superpowers/reports/2026-07-31-lead-todo-guided-configuration-acceptance.md`

**Interfaces:**
- Consumes: all previous tasks, real MySQL, running Spring Boot API and Vue UI.
- Produces: repeatable proof that configuration, simulation, publication, activation and runtime flow work together.

- [ ] **Step 1: Write the failing MySQL release-bundle test**

The test must assert:

```java
assertEquals(1,enabledEntrySlotCount("LEAD_FIRST_CONTACT_ENTRY"));
assertEquals("TD-001",activeEntryTemplateCode());
assertTrue(allCurrentDraftsUseOwnHash("TD-002","TD-003","TD-004"));
assertEquals(Set.of("LEAD_INVALID_REVIEW_READY","LEAD_RETRY_READY","LEAD_PROGRESS_READY"),
        activeRecipeCodes());
assertEquals(0,crossBusinessLeadRouteCount());
```

For TD-004, complete one Todo twice with the same action identity and assert exactly one follow-up, one schedule plan, one occurrence and one next Todo.

- [ ] **Step 2: Run the MySQL test and verify any remaining integration gap fails clearly**

Use the existing external MySQL test properties/container convention:

```powershell
mvn -pl ruoyi-admin -am -Dtest=LeadTodoGuidedConfigurationExternalMysqlIT,LeadTodoFlowEndToEndTest,FlywayMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected before final fixes: FAIL only on concrete integration mismatches; do not weaken assertions.

- [ ] **Step 3: Fix integration-only defects with the smallest scoped changes**

Allowed fixes are limited to mapper aliases, transaction ordering, JSON path compatibility, fixture values and missing Spring wiring. Any behavior change requires returning to the owning task and adding its unit test first.

- [ ] **Step 4: Extend the real browser journey**

For each TD-002, TD-003 and TD-004, Playwright must:

```text
open template -> load recommended configuration -> inspect all seven steps
-> verify Chinese field/owner/dictionary labels -> switch away and back
-> confirm state persisted -> run every governed scenario
-> confirm current-hash readiness -> publish -> verify version history
```

Then create or select a real lead and execute these routes:

```text
TD-001 SUSPECT_INVALID -> TD-002 TRUE_INVALID
TD-001 SUSPECT_INVALID -> TD-002 MISJUDGED_VALID -> TD-001
TD-001 UNREACHABLE -> TD-003 CONNECTED -> TD-004
TD-004 PROGRESS_RECORDED -> next TD-004 due in five days
```

- [ ] **Step 5: Run the complete backend verification**

```powershell
mvn clean test -DskipTests=false
```

Expected: reactor build SUCCESS and all tests PASS.

- [ ] **Step 6: Run the complete frontend verification**

```powershell
Set-Location ruoyi-ui
npm run test:todo
npm run test:todo-config
npm run test:todo-phase-two
npm run test:todo-schema
npm run test:encoding
npm run build:prod
```

Expected: all commands PASS.

- [ ] **Step 7: Run Playwright against the real services**

Use the repository's existing real-E2E launcher and the Playwright skill wrapper. Save screenshots under:

```text
output/playwright/lead-todo-guided-configuration/
```

Required screenshots:

```text
td002-seven-steps.png
td003-retry-timeline.png
td004-five-day-cycle.png
lead-release-active-binding.png
lead-runtime-next-td004.png
```

- [ ] **Step 8: Write the acceptance report**

The report records commit, migration version, service URLs, database identity, every command and exit status, published template/version IDs, active trigger rule, Todo IDs, schedule plan/occurrence IDs and screenshot paths. Do not report a scenario as passed without its database and UI evidence.

- [ ] **Step 9: Re-run the verification gate after writing the report**

```powershell
git diff --check
git status --short
```

Confirm only the report and intended test files are uncommitted.

- [ ] **Step 10: Commit the acceptance suite and report**

```powershell
git add ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoGuidedConfigurationExternalMysqlIT.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoFlowEndToEndTest.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java ruoyi-ui/tests/e2e/todo-config-journey.spec.js ruoyi-ui/scripts/check-todo-config-real-e2e.js docs/superpowers/reports/2026-07-31-lead-todo-guided-configuration-acceptance.md
git commit -m "test(todo): verify governed lead template flow"
```

---

## Final Release Gate

Before claiming completion, verify all of the following from the real database and UI:

- [ ] The default template list exposes only `TD-001` for first contact; the historical template is visible only under retired/history filtering.
- [ ] `LEAD_FIRST_CONTACT_ENTRY` has exactly one enabled trigger rule.
- [ ] The active rule targets the coordinated TD-001 version, not an older published version.
- [ ] TD-001 numeric route targets match the newly published TD-002, TD-003 and TD-004 versions.
- [ ] TD-002 has three passing required scenarios.
- [ ] TD-003 has four passing required scenarios.
- [ ] TD-004 has two positive scenarios plus the expected missing-proof failure.
- [ ] No draft borrows a published version's definition hash.
- [ ] “返回修复” navigates to the correct step and control.
- [ ] No primary label exposes `undefined`, an unexplained business field, a raw user ID or an English-only outcome code.
- [ ] TD-004 duplicate completion creates exactly one progress fact, plan and next Todo.
- [ ] Backend, frontend, Flyway, MySQL integration and Playwright verification all pass.

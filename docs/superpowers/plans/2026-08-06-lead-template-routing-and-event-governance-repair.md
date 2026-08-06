# Lead Template Routing and Event Governance Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair TD-001 routing/version governance, restore the canonical TD-002 event, and expose only business-meaningful lead event fields without changing the active runtime entry automatically.

**Architecture:** Treat published definitions as immutable release artifacts and validate their saved references independently from new release candidates. Keep TD-003 as a scheduled dependency rather than an immediate TD-001 graph target, enforce a backend template-event policy, and derive purpose-specific field eligibility from event catalog allowlists. Use forward-only Flyway migrations to retire the bad draft and prepare clean reviewable drafts.

**Tech Stack:** Java 17, Spring Boot, MyBatis, Fastjson2, Flyway Java migrations, MySQL 8, Vue 2, Element UI, Node contract scripts, Playwright.

## Global Constraints

- Work only in the existing linked worktree on `runtime/startup-wiring-fix`.
- Preserve `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, runtime logs, Playwright artifacts, and `test-results/`.
- Do not mutate, delete, or rewrite existing published template versions or audit history.
- Do not change the enabled `LEAD_FIRST_CONTACT_ENTRY` trigger from its current version in a migration.
- Do not publish or activate the newly prepared drafts automatically.
- TD-003 must be created only by `LEAD_RETRY_WINDOW_DUE`, never by an ordinary TD-001 graph transition.
- Every behavior change starts with a failing test and a confirmed RED result.

---

### Task 1: Separate Published Reference Validation from Candidate Routing Policy

**Files:**
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoBusinessOutcomeCatalogServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoBusinessOutcomeCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`

**Interfaces:**
- Consumes: `candidateVersionId == null` for immutable published/read-only journey evaluation and non-null IDs for editable drafts.
- Produces: historical reference validation through `selectTemplateIdentityByVersionId`, and routing targets that include referenced historical published versions.

- [ ] **Step 1: Add failing published-reference tests**

Add tests using literal targets `TD-004 v79`, `TD-002 v80`, and current catalog `TD-004 v95`, `TD-002 v80`. Assert that `validate("TD-001","LEAD",null,definition)` does not produce `TODO_ROUTING_TARGET_VERSION_INVALID` when v79 still resolves to a published TD-004, while `validate("TD-001","LEAD",96L,definition)` rejects v79 for a new candidate.

Add a journey-service test whose definition references v79 and whose current catalog contains only v95; assert `resources.routingTargets` contains both v95 and the referenced historical v79 with the TD-004 name and version number.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
mvn -pl law-todo -am -Dtest=TodoBusinessOutcomeCatalogServiceTest,TodoConfigurationJourneyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: published validation still emits `TODO_ROUTING_TARGET_VERSION_INVALID`, and the journey target catalog omits v79.

- [ ] **Step 3: Implement lifecycle-aware validation**

In `TodoBusinessOutcomeCatalogService`, split validation into:

```java
private List<OutcomeIssue> validatePublishedReferences(
        String businessType, TodoDefinitionDocument definition)

private List<OutcomeIssue> validateCandidateOutcomes(
        String templateCode, String businessType, long candidateVersionId,
        TodoDefinitionDocument definition)
```

The published path validates every saved `targetVersionId` against `selectTemplateIdentityByVersionId`, requiring matching template code, business type, and `PUBLISHED` status. The candidate path keeps governed result/effect/current-candidate validation.

Extend `selectTemplateIdentityByVersionId` to return `template_name`, `version_no`, and version `status`. Add a mapper query accepting referenced version IDs or a single-ID loop in the application service; do not interpolate IDs into SQL.

- [ ] **Step 4: Include historical references in journey resources**

Change `TodoConfigurationJourneyService.routingTargets` to merge the current published catalog with exact published versions referenced by `definition.routing().config.businessOutcomes` and TASK nodes. Deduplicate by `versionId`, keeping current targets first.

- [ ] **Step 5: Run focused and surrounding tests**

```powershell
mvn -pl law-todo -am -Dtest=TodoBusinessOutcomeCatalogServiceTest,TodoConfigurationJourneyServiceTest,TodoConfigurationJourneyEvaluatorTest,TodoDefinitionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass and draft publish gates remain strict.

- [ ] **Step 6: Commit lifecycle validation**

```powershell
git add law-todo/src/main law-todo/src/test
git commit -m "fix: separate published route references from candidate policy"
```

---

### Task 2: Restore TD-001 Scheduled Retry Semantics and Release Binding

**Files:**
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoBusinessOutcomeCatalogServiceTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/LeadTodoReleaseServiceTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoBusinessOutcomeCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/LeadTodoReleaseService.java`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/TD-001.json`

**Interfaces:**
- Consumes: the release command's four version IDs.
- Produces: immediate TD-001 outcomes for TD-002/TD-004 and `routing.config.releaseDependencies.TD-003` for the scheduled dependency.

- [ ] **Step 1: Add failing route-contract tests**

Assert these literal behaviors:

```java
assertThat(outcomesByValue.get("VALID").targetTemplateCode()).isEqualTo("TD-004");
assertThat(outcomesByValue.get("SUSPECT_INVALID").targetTemplateCode()).isEqualTo("TD-002");
assertThat(outcomesByValue.get("UNREACHABLE").effectKind()).isEqualTo("END");
assertThat(outcomesByValue.get("UNREACHABLE").targetVersionId()).isNull();
```

Add release tests for a TD-001 graph containing only immediate TD-002/TD-004 targets and `releaseDependencies: {"TD-003": 94}`. Assert activation accepts it and rejects a graph that contains a TD-003 TASK node or `UNREACHABLE -> TD-003` outcome.

- [ ] **Step 2: Run route tests and verify RED**

```powershell
mvn -pl law-todo -am -Dtest=TodoBusinessOutcomeCatalogServiceTest,LeadTodoReleaseServiceTest,LeadTodoPublishedTemplateContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the outcome catalog still returns TD-003 for `UNREACHABLE`, and the release validator expects TD-003 in immediate outcomes.

- [ ] **Step 3: Correct governed outcomes**

Remove `UNREACHABLE` from `TD001_TARGETS`; materialize it as `END` with no target. Preserve VALID and SUSPECT_INVALID target resolution.

Update the canonical TD-001 routing document so `businessOutcomes` represents all three values, with `UNREACHABLE` ending the graph, and add:

```json
"releaseDependencies": {
  "TD-003": 3
}
```

The repository resource keeps numeric compiler fixture `3`; the Flyway draft migration in Task 4 replaces it with the selected published numeric version.

- [ ] **Step 4: Correct coordinated release validation**

In `LeadTodoReleaseService`:

- compare immediate TD-001 targets against `{TD-002, TD-004}`;
- require TD-001 TASK nodes to contain only self, TD-002, TD-004, and reopened self identities;
- require `releaseDependencies.TD-003 == command.td003VersionId()`;
- keep separate TD-003 `CONNECTED -> TD-004` validation;
- combine immediate targets and `releaseDependencies` when returning the active release downstream map.

- [ ] **Step 5: Run route, schedule, and production-port tests**

```powershell
mvn -pl law-todo,ruoyi-admin -am -Dtest=TodoBusinessOutcomeCatalogServiceTest,LeadTodoReleaseServiceTest,LeadTodoPublishedTemplateContractTest,LeadTodoProductionPortsExternalMysqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: completing an unreachable first contact creates no immediate TD-003; due materialization creates exactly one.

- [ ] **Step 6: Commit route semantics**

```powershell
git add law-todo ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoProductionPortsExternalMysqlIT.java
git commit -m "fix: keep lead retry creation schedule-driven"
```

---

### Task 3: Enforce Template-to-Event Compatibility

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoTemplateEventPolicy.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoTemplateEventPolicyTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java`
- Modify: affected constructor tests in `law-todo/src/test/java/com/law/todo/application/`

**Interfaces:**
- Produces: `TemplateEventPolicyView(recommendedEventType, payloadVersion, locked, allowedEventTypes)` in journey resources.
- Throws: `TODO_TEMPLATE_EVENT_INCOMPATIBLE` for an invalid governed-template binding.

- [ ] **Step 1: Add failing policy tests**

Use a literal table for TD-001 through TD-004. Assert TD-002 accepts `LEAD_SUSPECT_INVALID_MARKED` and rejects `LEAD_FIRST_CONTACT_UNREACHABLE`; ungoverned templates remain unrestricted.

- [ ] **Step 2: Run policy tests and verify RED**

```powershell
mvn -pl law-todo -am -Dtest=TodoTemplateEventPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the policy does not exist.

- [ ] **Step 3: Implement and integrate the policy**

Implement immutable mappings and `requireCompatible`. Invoke it from journey evaluation and all draft preflight/publish paths before business-outcome validation. Add the policy view to `CurrentResources`; preserve overloaded constructors for unaffected tests.

- [ ] **Step 4: Run application tests**

```powershell
mvn -pl law-todo -am -Dtest=TodoTemplateEventPolicyTest,TodoConfigurationJourneyEvaluatorTest,TodoDefinitionServiceTest,TodoConfigurationJourneyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Commit event policy**

```powershell
git add law-todo/src/main law-todo/src/test
git commit -m "feat: govern lead template event compatibility"
```

---

### Task 4: Repair Lead Event Schemas and Prepare Safe Drafts

**Files:**
- Create: `ruoyi-admin/src/main/java/db/migration/V0_20_81__GovernLeadEventConfigurationFields.java`
- Create: `ruoyi-admin/src/main/java/db/migration/V0_20_82__RepairLeadTemplateConfigurationDrafts.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTemplateRoutingGovernanceExternalMysqlIT.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

**Interfaces:**
- V0.20.81 enriches active LEAD event schemas and explicit field allowlists.
- V0.20.82 retires v93-like incompatible drafts and creates one canonical draft each for TD-001 and TD-002 without changing the enabled trigger.

- [ ] **Step 1: Add failing real-MySQL migration assertions**

Assert after migration:

- all seven named events have business titles/descriptions for every non-technical field;
- IDs/codes have semantic and option metadata where selectable;
- condition and owner allowlists contain only approved paths;
- latest TD-002 draft event is `LEAD_SUSPECT_INVALID_MARKED`;
- latest TD-001 draft has `UNREACHABLE` with `END`, no TD-003 TASK, and a numeric TD-003 release dependency;
- the enabled `LEAD_FIRST_CONTACT_ENTRY` version ID is unchanged;
- rerunning migration logic creates no additional drafts.

- [ ] **Step 2: Run the integration test and verify RED**

```powershell
mvn -pl ruoyi-admin -am -Dtest=LeadTemplateRoutingGovernanceExternalMysqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: migrations/classes are absent and assertions fail.

- [ ] **Step 3: Implement V0.20.81 schema governance**

Use Fastjson2 to update each existing schema object without string concatenation. Set `title`, `description`, `x-semantic-type`, `x-option-source`, `x-dict-type`, `x-business-type`, and `x-read-only-business-context` according to field purpose. Store explicit JSON arrays for owner, condition, and default-value eligibility.

- [ ] **Step 4: Implement V0.20.82 draft repair**

Within one transaction:

1. lock TD-001/TD-002 templates and their latest versions;
2. retire incompatible DRAFT/BLOCKED versions with an audit-preserving change summary;
3. load canonical resources, bind current published TD-002/TD-003/TD-004 IDs, compile and hash definitions;
4. insert one DRAFT per template with `source_version_id` pointing to the current published version;
5. leave `todo_template.current_version` and `todo_trigger_rule` unchanged.

- [ ] **Step 5: Run MySQL and Flyway suites**

```powershell
mvn -pl ruoyi-admin -am -Dtest=LeadTemplateRoutingGovernanceExternalMysqlIT,FlywayMigrationTest,LeadTemplateConfigurationMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 6: Commit forward migrations**

```powershell
git add ruoyi-admin/src/main/java/db/migration ruoyi-admin/src/test/java/com/ruoyi/web/migration
git commit -m "fix: repair lead template drafts and event schemas"
```

---

### Task 5: Add Purpose-Specific Field Eligibility

**Files:**
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceCatalogServiceTest.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoEventResourceService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoResourceViews.java`
- Modify: related event resource tests.

**Interfaces:**
- Extends `FieldResource` with `conditionEligible`, `conditionSourceEventVersions`, `defaultValueEligible`, and `defaultValueSourceEventVersions`.
- Extends event views with `configurationReady` and structured governance issue codes.

- [ ] **Step 1: Add failing resource tests**

Build one event fixture containing `leadId`, `ownerId`, `reasonCode`, `planId`, and `schemaVersion`. Assert only `reasonCode` is condition-eligible, only `ownerId` is owner-eligible, and technical fields are not business-configurable. Assert a schema missing business labels is not `configurationReady` even if `schemaStatus` is `READY`.

- [ ] **Step 2: Run resource tests and verify RED**

```powershell
mvn -pl law-todo -am -Dtest=TodoConfigurationResourceCatalogServiceTest,TodoEventResourceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Carry all allowlists through the resource model**

Select `condition_field_paths_json` and `default_value_field_paths_json`, track eligibility per `eventType@payloadVersion`, and expose purpose-specific flags. Add a reusable schema-governance evaluator that checks labels, descriptions, semantic types, selection sources, and read-only technical context.

- [ ] **Step 4: Run resource and journey tests**

```powershell
mvn -pl law-todo -am -Dtest=TodoConfigurationResourceCatalogServiceTest,TodoEventResourceServiceTest,TodoConfigurationJourneyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Commit field governance**

```powershell
git add law-todo/src/main law-todo/src/test
git commit -m "feat: expose purpose-specific event fields"
```

---

### Task 6: Update the Guided Configuration UI

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/EventStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/TriggerStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/journey-step-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/index.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`
- Modify: `ruoyi-ui/tests/e2e/todo-config-journey.spec.js`

**Interfaces:**
- Consumes: `resources.eventPolicy`, historical routing targets, and purpose-specific field flags.
- Produces: locked/recommended core events, no raw route IDs, and business-only configuration fields.

- [ ] **Step 1: Add failing frontend contract tests**

Add model assertions that:

- TD-002 policy filters selection to `LEAD_SUSPECT_INVALID_MARKED`;
- trigger fields require `conditionEligible` for the selected event version;
- `schemaVersion`, system IDs, and unnamed fields are hidden from basic event summaries;
- an unmatched historical route target renders its supplied template name/version rather than `79` or `89`;
- `UNREACHABLE` presents “结束当前待办并建立重试计划” without a target selector.

- [ ] **Step 2: Run frontend contracts and verify RED**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
```

- [ ] **Step 3: Implement event and field UX**

Disable incompatible event cards when `eventPolicy.locked` is true, display the recommendation, and reset trigger/owner/simulation state if an editable ungoverned template changes event. Group selected fields into business context and technical details; never use technical fields as fallback selectable options.

- [ ] **Step 4: Implement route labels and scheduled-result presentation**

Render target labels from the merged target catalog. For `END` plus `UNREACHABLE`, show the scheduled retry explanation and omit the target select. Keep published definitions read-only and show active/latest published identities separately.

- [ ] **Step 5: Run frontend tests and production build**

```powershell
npm run test:todo
npm run test:todo-config
npm run test:todo-phase-two
npm run test:encoding
npm run build:prod
```

- [ ] **Step 6: Commit UI behavior**

```powershell
git add ruoyi-ui/src/views/todo/config/journey ruoyi-ui/scripts/check-todo-phase-two-ux.js ruoyi-ui/tests/e2e/todo-config-journey.spec.js
git commit -m "fix: clarify lead template events and routing"
```

---

### Task 7: Proportional Regression and Real Browser Acceptance

**Files:**
- Modify only if a real defect is reproduced: files already listed in Tasks 1-6.
- Generate but do not stage: `output/playwright/lead-template-routing-governance/`.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: verified backend, migration, frontend, and browser evidence.

- [ ] **Step 1: Run backend regression**

```powershell
mvn -pl law-todo,ruoyi-admin -am test
```

- [ ] **Step 2: Run frontend regression**

```powershell
Set-Location ruoyi-ui
npm run test:todo
npm run test:todo-config
npm run test:todo-phase-two
npm run test:encoding
npm run build:prod
```

- [ ] **Step 3: Run real Chrome acceptance**

Use the existing disposable real-backend Playwright harness and assert:

1. TD-001 published history shows named v79/v89 references instead of bare numbers;
2. the new TD-001 draft shows VALID -> current TD-004, SUSPECT_INVALID -> current TD-002, UNREACHABLE -> scheduled END;
3. TD-002 draft opens on `LEAD_SUSPECT_INVALID_MARKED`;
4. no basic selector contains `业务字段`, `schemaVersion`, `planId`, `reviewId`, or raw person IDs;
5. the active entry version is unchanged.

Save screenshots under `output/playwright/lead-template-routing-governance/` without staging them.

- [ ] **Step 4: Inspect final diff and commits**

```powershell
git diff --check HEAD~6..HEAD
git status --short
git log --oneline -8
```

Expected: only the pre-existing user-owned files and generated artifacts remain dirty; every implementation commit is scoped to this plan.

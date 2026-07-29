# Lead Template P0/P1 Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the TD-001 lead first-contact template understandable, non-destructive to configure, type-safe, route-complete, and verifiably publishable through all three required scenarios.

**Architecture:** Keep the existing immutable definition, resource catalog, journey and simulation architecture. Add event-version-scoped field descriptors, owner eligibility, typed outcome routing, authoritative step issues and hash-bound simulation UX around those boundaries; repair TD-001 through a new Flyway migration without mutating immutable releases or historical evidence.

**Tech Stack:** Java 17, Spring Boot, MyBatis, Flyway, MySQL 8, Vue 2.6, Element UI, Node contract tests, Playwright, Maven

## Global Constraints

- Work only on `runtime/startup-wiring-fix` in the existing linked worktree.
- Preserve unrelated local changes in `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, `.playwright-cli/`, `.runtime-logs/`, `output/`, and `test-results/`.
- Do not edit an already-applied Flyway migration.
- Do not mutate immutable published versions or copy old simulation evidence.
- Canonical definitions store stable IDs and codes; ordinary UI displays governed Chinese labels.
- TD-001 routes are `VALID → TD-004`, `SUSPECT_INVALID → TD-002`, `UNREACHABLE → TD-003`.
- Every production behavior is introduced through a red-green-refactor test cycle.
- Completion requires backend verification, frontend contract tests, production build, MySQL integration and real browser acceptance.

---

### Task 1: Event-version-scoped field metadata and owner eligibility

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoPayloadSchemaDescriptor.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoResourceViews.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceCatalogServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoPayloadSchemaDescriptorTest.java`

**Interfaces:**
- Produces: `FieldResource.fieldKey()`, `eventRole()`, `ownerEligible()`, `sourceEventVersions()`.
- Consumes: event `owner_field_paths_json`, payload property titles and semantic extensions.

- [ ] **Step 1: Add a failing catalog test for placeholder overwrite**

Create two LEAD event schemas with `ownerId`; the selected `LEAD_ASSIGNED@1` title is `线索负责人` and another event title is absent. Assert `fields("LEAD","LEAD_ASSIGNED")` returns `线索负责人`, not `业务字段`.

- [ ] **Step 2: Run the focused backend test and confirm RED**

Run:

```powershell
mvn -pl law-todo -am -Dtest=TodoConfigurationResourceCatalogServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the new field label or event identity assertion fails against the current code.

- [ ] **Step 3: Add a failing owner eligibility test**

Use `owner_field_paths_json=["ownerId"]` with both `ownerId` and `operatorId` typed as `USER_ID`. Assert only `ownerId.ownerEligible()` is true and its `eventRole` is `OWNER`; `operatorId` is `OPERATOR`.

- [ ] **Step 4: Implement event-scoped descriptor merging**

Extend payload descriptors and catalog rows so event-specific queries merge only the selected event/version, reject placeholder labels as override candidates, and expose `fieldKey`, `eventRole`, and `ownerEligible`.

- [ ] **Step 5: Run focused tests and refactor**

Run the command from Step 2. Expected: all focused tests pass.

- [ ] **Step 6: Commit Task 1**

```powershell
git add law-todo/src/main/java/com/law/todo/application/TodoPayloadSchemaDescriptor.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java law-todo/src/main/java/com/law/todo/application/view/TodoResourceViews.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceCatalogServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoPayloadSchemaDescriptorTest.java
git commit -m "fix(todo): scope event fields and owner eligibility"
```

### Task 2: Typed condition validation and Chinese repair issues

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/expression/ConditionTypeChecker.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java`
- Modify: `ruoyi-ui/src/views/todo/config/journey/journey-step-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/TypedConditionBuilder.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/PublishPreflightPanel.vue`
- Test: `law-todo/src/test/java/com/law/todo/expression/ConditionTypeCheckerTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyEvaluatorTest.java`
- Test: `ruoyi-ui/scripts/check-todo-journey-model.js`

**Interfaces:**
- Produces: `conditionDraftBlocker(condition, fields)` and `localizedJourneyIssue(issue, context)`.
- Consumes: the field resources produced by Task 1.

- [ ] **Step 1: Add failing backend tests for missing values**

Assert `NE` with a missing value returns `TODO_CONDITION_VALUE_REQUIRED`, field path `event.condition.assignmentId`, and step `TRIGGER`; assert `NOT_EMPTY` with `value=null` is valid.

- [ ] **Step 2: Verify backend RED**

Run:

```powershell
mvn -pl law-todo -am -Dtest=ConditionTypeCheckerTest,TodoConfigurationJourneyEvaluatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing-value behavior or step metadata assertion fails.

- [ ] **Step 3: Add failing frontend model tests**

Assert:

```javascript
conditionDraftBlocker(assignmentIdNeWithoutValue, integerFields).code
  === 'TODO_CONDITION_VALUE_REQUIRED'
conditionSummary(notEmptyCondition, fields)
  === '当前规则：分配记录ID 不为空。'
conditionSummary(neWithoutValue, fields)
  !== '当前规则：分配记录ID 不等于 undefined。'
```

- [ ] **Step 4: Verify frontend RED**

Run:

```powershell
npm --prefix ruoyi-ui run test:todo-phase-two
```

Expected: the new exported behavior is missing or returns the old summary.

- [ ] **Step 5: Implement shared typed semantics**

Add explicit value-requirement checks, suppress value controls for no-value operators, block incomplete drafts before save, and localize the known preflight errors. Map repair paths to `TRIGGER`, `OWNER`, `ROUTING`, or `SIMULATION_PUBLISH` instead of defaulting to `EVENT`.

- [ ] **Step 6: Run backend and frontend focused tests**

Run Steps 2 and 4 commands. Expected: both pass.

- [ ] **Step 7: Commit Task 2**

```powershell
git add law-todo/src/main/java/com/law/todo/expression/ConditionTypeChecker.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java law-todo/src/test/java/com/law/todo/expression/ConditionTypeCheckerTest.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyEvaluatorTest.java ruoyi-ui/src/views/todo/config/journey/journey-step-model.js ruoyi-ui/src/views/todo/config/journey/components/TypedConditionBuilder.vue ruoyi-ui/src/views/todo/config/journey/components/PublishPreflightPanel.vue ruoyi-ui/scripts/check-todo-journey-model.js
git commit -m "fix(todo): enforce typed journey conditions"
```

### Task 3: Non-destructive owner strategy editor

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/journey/journey-step-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/OwnerStep.vue`
- Modify: `ruoyi-ui/scripts/check-todo-journey-model.js`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Produces: `createOwnerStrategyDrafts(config, fields)`, `updateOwnerStrategyDraft(drafts, strategy, value)`, `applyOwnerStrategyDraft(drafts, strategy, fallback)`.
- Consumes: `ownerEligible` from Task 1.

- [ ] **Step 1: Add failing model tests for retained strategy drafts**

Start with `EVENT_OWNER=ownerId`, edit `USER=104`, switch back, and assert both values remain in independent draft slots while the applied canonical config remains unchanged.

- [ ] **Step 2: Verify RED**

Run:

```powershell
npm --prefix ruoyi-ui run test:todo-phase-two
```

Expected: draft helper exports are missing.

- [ ] **Step 3: Add a failing eligibility test**

Assert `scopeOwnerFields` excludes a `USER_ID` field with `ownerEligible=false` and includes `ownerId` with `ownerEligible=true`.

- [ ] **Step 4: Implement owner strategy drafts and explicit Apply**

Remove save-on-strategy-click. Render current draft selection, applied rule, fallback order and an “应用此负责人规则” action. Keep candidate pool and CC semantics separate.

- [ ] **Step 5: Run frontend tests and build the component**

Run:

```powershell
npm --prefix ruoyi-ui run test:todo-phase-two
npm --prefix ruoyi-ui run build:prod
```

Expected: contract tests pass and Vue compilation succeeds.

- [ ] **Step 6: Commit Task 3**

```powershell
git add ruoyi-ui/src/views/todo/config/journey/journey-step-model.js ruoyi-ui/src/views/todo/config/journey/steps/OwnerStep.vue ruoyi-ui/scripts/check-todo-journey-model.js ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "fix(todo): retain owner strategy drafts"
```

### Task 4: Typed completion-result routing and recommended lead routes

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoBusinessOutcomeCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationViews.java`
- Modify: `ruoyi-ui/src/views/todo/config/journey/journey-step-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/RoutingStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoBusinessOutcomeCatalogServiceTest.java`
- Test: `ruoyi-ui/scripts/check-todo-journey-model.js`

**Interfaces:**
- Produces: `BusinessOutcomeSet(resultField, resultFieldName, options, recommendationCode)`.
- Produces frontend `materializeOutcomeRouting(outcomeSet, targets, currentVersionId, current)`.
- Consumes: governed completion fields and routing target catalog.

- [ ] **Step 1: Add failing backend outcome-set test**

For TD-001 assert the service returns `contactResult` with Chinese options and expected target template codes `TD-004`, `TD-002`, `TD-003`.

- [ ] **Step 2: Verify backend RED**

Run:

```powershell
mvn -pl law-todo -am -Dtest=TodoBusinessOutcomeCatalogServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Add failing frontend materialization tests**

Assert materialization creates three business outcomes with literal conditions:

```text
contactResult EQ VALID
contactResult EQ SUSPECT_INVALID
contactResult EQ UNREACHABLE
```

and targets the resolved published versions of TD-004, TD-002 and TD-003.

- [ ] **Step 4: Verify frontend RED**

Run:

```powershell
npm --prefix ruoyi-ui run test:todo-phase-two
```

- [ ] **Step 5: Implement outcome catalog and selector UI**

Replace the free-text result input in ordinary mode with result-field and dictionary-value selectors. Show a natural-language branch sentence and provide “应用首联推荐路由”. Preserve advanced graph configuration behind the existing advanced section.

- [ ] **Step 6: Enforce route completeness**

Block duplicate values, missing enum values, missing targets, invalid target versions and unknown result fields in both local health and server preflight.

- [ ] **Step 7: Run focused tests and production build**

Run Steps 2 and 4 plus:

```powershell
npm --prefix ruoyi-ui run build:prod
```

- [ ] **Step 8: Commit Task 4**

```powershell
git add law-todo/src/main/java/com/law/todo/application/TodoBusinessOutcomeCatalogService.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationViews.java law-todo/src/test/java/com/law/todo/application/TodoBusinessOutcomeCatalogServiceTest.java ruoyi-ui/src/views/todo/config/journey/journey-step-model.js ruoyi-ui/src/views/todo/config/journey/steps/RoutingStep.vue ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue ruoyi-ui/scripts/check-todo-journey-model.js
git commit -m "feat(todo): configure typed business outcomes"
```

### Task 5: One-click semantic simulation and localized evidence gate

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationSampleCatalog.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoJourneyPayloadPreparationService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioCatalog.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/BusinessObjectPayloadEditor.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/SemanticOptionSelector.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/ScenarioSelector.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/BatchScenarioGate.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoBusinessPayloadHydrationServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java`
- Test: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Produces: scenario display labels and evidence state reasons.
- Consumes: field options, outcome targets and current definition hash.

- [ ] **Step 1: Add failing backend tests for resolvable samples and evidence reasons**

Assert the LEAD sample uses a resolvable actor/default user instead of static missing user 11; assert stale evidence reports `DEFINITION_CHANGED`, not only “missing”.

- [ ] **Step 2: Verify backend RED**

Run:

```powershell
mvn -pl law-todo -am -Dtest=TodoBusinessPayloadHydrationServiceTest,TodoSimulationEvidenceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Add failing frontend tests**

Assert:

- sample click emits one `sample-load` intent rather than search-only;
- a controlled dictionary selector loads options for a non-null raw value without waiting for focus;
- `VALID` projects as `有效`;
- scenario targets display template names with codes secondary;
- gate errors display Chinese scenario names and evidence state.

- [ ] **Step 4: Verify frontend RED**

Run:

```powershell
npm --prefix ruoyi-ui run test:todo-phase-two
```

- [ ] **Step 5: Implement one-click sample load and semantic projection**

Search, select and hydrate the read-only sample in one action. Resolve initial controlled values automatically. Enrich scenario target display and evidence-state copy.

- [ ] **Step 6: Localize and route all simulation blockers**

Map condition blockers to `TRIGGER`, owner blockers to `OWNER`, route blockers to `ROUTING`, and evidence blockers to `SIMULATION_PUBLISH`.

- [ ] **Step 7: Run focused tests and build**

Run Steps 2 and 4 and `npm --prefix ruoyi-ui run build:prod`.

- [ ] **Step 8: Commit Task 5**

```powershell
git add law-todo/src/main/java/com/law/todo/application/TodoSimulationSampleCatalog.java law-todo/src/main/java/com/law/todo/application/TodoJourneyPayloadPreparationService.java law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioCatalog.java law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java law-todo/src/test/java/com/law/todo/application/TodoBusinessPayloadHydrationServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue ruoyi-ui/src/views/todo/config/journey/components/BusinessObjectPayloadEditor.vue ruoyi-ui/src/views/todo/config/journey/components/SemanticOptionSelector.vue ruoyi-ui/src/views/todo/config/journey/components/ScenarioSelector.vue ruoyi-ui/src/views/todo/config/journey/components/BatchScenarioGate.vue ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo): simplify semantic scenario validation"
```

### Task 6: Cross-step dependencies and authoritative health

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoJourneyDependencyService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationViews.java`
- Modify: `ruoyi-ui/src/views/todo/config/journey/journey-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/index.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/JourneyStepNav.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoJourneyDependencyServiceTest.java`
- Test: `ruoyi-ui/scripts/check-todo-journey-model.js`
- Test: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Produces: `JourneyImpact(changedPaths, affectedSteps, invalidatedEvidence, message)`.
- Produces authoritative issue lists per journey step after save.

- [ ] **Step 1: Add failing dependency tests**

Assert changes to `contactResult` affect DOD, ROUTING and SIMULATION_PUBLISH; route-only changes affect ROUTING and SIMULATION_PUBLISH; title-only changes do not invalidate evidence.

- [ ] **Step 2: Verify backend RED**

Run:

```powershell
mvn -pl law-todo -am -Dtest=TodoJourneyDependencyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Add failing frontend health tests**

Assert a server `TRIGGER` blocker overrides the local completed state, the impact banner names affected steps, and the nav exposes a scrollable current step at narrow widths.

- [ ] **Step 4: Implement dependency and health responses**

Return affected steps and authoritative issues after draft save. Invalidate client simulation state only when executable paths change.

- [ ] **Step 5: Implement impact and responsive UI**

Show a concise impact banner with one synchronization action. Make the seven-step nav horizontally scrollable and retain icon-plus-text status semantics.

- [ ] **Step 6: Run focused tests and build**

Run Steps 2 and 3 commands and `npm --prefix ruoyi-ui run build:prod`.

- [ ] **Step 7: Commit Task 6**

```powershell
git add law-todo/src/main/java/com/law/todo/application/TodoJourneyDependencyService.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationViews.java law-todo/src/test/java/com/law/todo/application/TodoJourneyDependencyServiceTest.java ruoyi-ui/src/views/todo/config/journey/journey-model.js ruoyi-ui/src/views/todo/config/journey/index.vue ruoyi-ui/src/views/todo/config/journey/components/JourneyStepNav.vue ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue ruoyi-ui/scripts/check-todo-journey-model.js ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo): expose journey dependency impacts"
```

### Task 7: Safe TD-001 repair migration

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_65__lead_template_configuration_hardening.sql`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTemplateConfigurationHardeningMigrationTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/integration/LeadTodoFlowMigrationContractTest.java`

**Interfaces:**
- Produces a new repairable TD-001 draft with empty trigger condition, `ownerId` owner rule and three typed routes.
- Preserves immutable versions and historical evidence.

- [ ] **Step 1: Add a failing migration contract test**

Assert the next available migration version exists and expresses:

```text
event.condition = {}
owner.config.type = PAYLOAD
owner.config.field = ownerId
VALID → TD-004
SUSPECT_INVALID → TD-002
UNREACHABLE → TD-003
```

Assert no `UPDATE todo_template_version ... status='PUBLISHED'`, no evidence insert, and no delete.

- [ ] **Step 2: Verify migration RED**

Run:

```powershell
mvn -pl ruoyi-admin -am -Dtest=LeadTemplateConfigurationHardeningMigrationTest,LeadTodoFlowMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement the Flyway migration**

Use JSON operations and published target lookups to create or repair only the mutable TD-001 draft. Add governed field metadata and the lead route recommendation resource idempotently.

- [ ] **Step 4: Run migration contract tests**

Run Step 2. Expected: pass.

- [ ] **Step 5: Commit Task 7**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_65__lead_template_configuration_hardening.sql ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTemplateConfigurationHardeningMigrationTest.java law-todo/src/test/java/com/law/todo/integration/LeadTodoFlowMigrationContractTest.java
git commit -m "fix(todo): repair lead template configuration"
```

### Task 8: MySQL integration and full three-scenario proof

**Files:**
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java`
- Modify: `ruoyi-ui/e2e/lead-todo-flow.spec.js`
- Create: `doc/reviews/lead-template-p0-p1-test-matrix.md`

**Interfaces:**
- Proves migration, journey APIs, read-only simulation, routing and evidence against MySQL 8 and real HTTP APIs.

- [ ] **Step 1: Add failing MySQL assertions**

Assert after migration:

- every LEAD_ASSIGNED field has a governed Chinese label;
- only ownerId is owner eligible;
- TD-001 draft has no invalid condition;
- route targets are TD-004, TD-002 and TD-003;
- immutable published rows and historical evidence counts are unchanged.

- [ ] **Step 2: Run the focused integration test and confirm RED**

Use the repository MySQL integration profile and run:

```powershell
mvn -pl ruoyi-admin -am -Dtest=LeadTemplateConfigurationMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Add API scenario assertions**

Run all three scenario endpoints against one selected sample and assert:

```text
TD001_VALID actualNextTemplateCode = TD-004
TD001_SUSPECT_INVALID actualNextTemplateCode = TD-002
TD001_UNREACHABLE actualNextTemplateCode = TD-003
```

- [ ] **Step 4: Assert simulation read-only boundaries**

Compare counts and business-row hashes before and after simulation for:

```text
todo_instance
todo_action_log
business_event
biz_lead
biz_lead_followup
```

Only `todo_simulation_evidence` may gain rows.

- [ ] **Step 5: Make implementation corrections until MySQL tests pass**

Do not weaken route, evidence, read-only or immutable-version assertions.

- [ ] **Step 6: Write the test matrix**

Map every P0/P1 requirement to a test method, SQL assertion or E2E step.

- [ ] **Step 7: Commit Task 8**

```powershell
git add ruoyi-admin/src/test/java/com/ruoyi/web/todo/LeadTemplateConfigurationMySqlIT.java ruoyi-ui/e2e/lead-todo-flow.spec.js doc/reviews/lead-template-p0-p1-test-matrix.md
git commit -m "test(todo): prove lead template configuration"
```

### Task 9: Real browser acceptance and completion audit

**Files:**
- Modify: `ruoyi-ui/e2e/lead-todo-flow.spec.js`
- Create: `doc/reviews/lead-template-p0-p1-acceptance.md`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces final evidence for every P0/P1 requirement.

- [ ] **Step 1: Start clean MySQL, Redis, backend and production frontend**

Use a disposable database initialized from the v0.15 baseline plus every Flyway migration. Record process IDs, ports and Flyway version.

- [ ] **Step 2: Run real browser flow**

Use the previously authorized Chrome/Playwright environment and verify:

```text
event field meanings
condition summary
owner strategy retention
recommended route materialization
one-click sample load
Chinese semantic values
three scenario batch pass
zero-blocker preflight
responsive step navigation
```

- [ ] **Step 3: Capture screenshots and console evidence**

Capture one screenshot per seven-step journey state plus the final evidence gate. Assert browser console has no errors.

- [ ] **Step 4: Run full backend verification**

```powershell
mvn clean verify
```

Expected: `BUILD SUCCESS`, zero failed required tests.

- [ ] **Step 5: Run full frontend verification**

```powershell
npm --prefix ruoyi-ui run test:todo
npm --prefix ruoyi-ui run test:todo-config
npm --prefix ruoyi-ui run test:todo-phase-two
npm --prefix ruoyi-ui run test:todo-schema
npm --prefix ruoyi-ui run test:encoding
npm --prefix ruoyi-ui run build:prod
```

Expected: every command exits 0.

- [ ] **Step 6: Add a CI gate**

Ensure CI runs focused P0/P1 backend contracts, frontend Todo contracts, production build and the disposable-MySQL lead template scenario suite. Upload browser screenshots and backend logs on failure.

- [ ] **Step 7: Write the acceptance report**

For each design requirement, cite fresh command output, test method, SQL result, API response or screenshot. Mark unproven items incomplete.

- [ ] **Step 8: Commit final evidence**

```powershell
git add ruoyi-ui/e2e/lead-todo-flow.spec.js doc/reviews/lead-template-p0-p1-acceptance.md .github/workflows/ci.yml
git commit -m "test(todo): complete lead template p0 p1 acceptance"
```

- [ ] **Step 9: Verify implementation scope**

```powershell
git status --short
git log --oneline --decorate -20
```

Expected: only the pre-existing user-owned files and runtime artifacts remain dirty.

---

## Plan Self-Review Mapping

| Requirement | Task |
|---|---|
| Event-scoped Chinese field metadata | 1 |
| ownerId-only owner eligibility | 1, 3 |
| Typed conditions and no undefined | 2 |
| Non-destructive owner switching | 3 |
| Typed result routing and recommendation | 4 |
| One-click sample and Chinese semantic values | 5 |
| Evidence states and localized repairs | 5 |
| Cross-step impact and authoritative health | 6 |
| Responsive journey navigation | 6 |
| Safe TD-001 draft repair | 7 |
| MySQL three-scenario and read-only proof | 8 |
| Real browser, full regression and CI gate | 9 |

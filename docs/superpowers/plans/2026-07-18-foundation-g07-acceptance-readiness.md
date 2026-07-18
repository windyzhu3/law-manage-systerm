# G-07 Acceptance Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a governed G-07 phase-one acceptance ledger that tracks 19 templates, 114 AT references, versioned scenarios, golden-data metadata and independent acceptance responsibility without treating the existing mocked E2E suite as business evidence.

**Architecture:** A forward-only Flyway migration creates immutable AT mapping slots plus mutable scenario/mapping evidence and idempotent action ledgers. `law-todo` exposes a read model and governed commands; `TodoAdmissionEvidenceService` consults the computed G-07 gate. The Todo configuration UI provides scenario and AT mapping management while keeping actual production E2E execution outside this development-admission gate.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, MySQL 8, Flyway, Vue 2, Element UI, Playwright, JUnit 5, Mockito.

## Global Constraints

- Repository facts are authoritative: phase one is `TD-001`～`TD-016`, `TD-022`, `TD-023`, `TD-025` and exactly 114 unique AT references.
- Existing 28 Playwright tests use mocked `/prod-api` and never count as G-07 business evidence automatically.
- Do not seed scenarios, users, golden data, signatures, approvals, published templates or enabled trigger rules.
- All database changes use a new migration `V0_20_21`; never edit an executed migration.
- All writes use strong commands, Bean Validation, `actionId`, request fingerprints, optimistic versions and independent review.
- G-07 approval never publishes templates or changes `foundation_state`/`production_state`.
- Work on branch `v0.2-Foundation`, commit locally, and do not push without user instruction.

---

### Task 1: Freeze the 19-template/114-AT database contract

**Files:**
- Create: `law-todo/src/test/java/com/law/todo/integration/AcceptanceReadinessMigrationContractTest.java`
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_21__foundation_phase_one_acceptance.sql`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

**Interfaces:**
- Consumes: `todo_prd_definition_catalog.acceptance_refs_json` created by `V0_20_10`.
- Produces: `todo_foundation_acceptance_requirement`, `todo_acceptance_scenario`, `todo_acceptance_ref_mapping`, and `todo_acceptance_action`.

- [ ] **Step 1: Write the failing migration contract**

Assert that the migration contains the four tables, the explicit phase-one template set, `json_table`, all five mapping states, all four scenario states, unique `acceptance_ref`, action idempotency, and no inserts into scenario, user, role, trigger or template publication tables.

- [ ] **Step 2: Run the contract and verify RED**

Run:

```powershell
mvn -pl law-todo -am '-Dtest=AcceptanceReadinessMigrationContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: compilation or assertion failure because `V0_20_21` does not exist.

- [ ] **Step 3: Add the forward-only migration**

Create the four tables with these stable identities and constraints:

```sql
unique key uk_todo_acceptance_requirement (gate_code, requirement_code);
unique key uk_todo_acceptance_scenario_code (scenario_code);
unique key uk_todo_acceptance_ref (acceptance_ref);
primary key (action_id);
```

Seed eight fixed G-07 requirements. Populate only `todo_acceptance_ref_mapping` through `json_table` for the 19 approved template codes. Derive `dimension_code` from the suffix after the last hyphen and set every row to `UNMAPPED` with all evidence/accountability columns null.

- [ ] **Step 4: Extend the real migration invariant**

Set the expected Flyway version to `0.20.21` and assert:

```text
8 G-07 requirements
19 distinct template codes
114 mappings
114 UNMAPPED
0 scenarios
0 actions
0 mappings with scenario/test/accountability/review data
0 enabled TD-001..TD-025 triggers
```

- [ ] **Step 5: Run the contract GREEN**

Run the focused Maven command from Step 2. Expected: all selected tests pass.

### Task 2: Add live G-07 readiness computation

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/mapper/TodoAcceptanceReadinessMapper.java`
- Create: `law-todo/src/main/resources/mapper/todo/TodoAcceptanceReadinessMapper.xml`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoAcceptanceReadinessService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoAcceptanceRequirementView.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoAcceptanceReadinessView.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoAcceptanceReadinessServiceTest.java`

**Interfaces:**
- Produces: `TodoAcceptanceReadinessView readiness(String gateCode)` and `boolean gateReady(String gateCode)`.
- Read model fields: requirement counts, template/AT/catalog mismatch counts, scenario/golden-data counts, mapped/reviewed AT counts and accountability counts.

- [ ] **Step 1: Write failing service tests**

Cover:

```text
19 templates + 114 matching AT slots are only the technical catalogue baseline
zero scenarios => SCENARIO_CATALOG and GOLDEN_DATASET are RUNTIME_MISSING
partially mapped/reviewed AT => RUNTIME_INCOMPLETE
all eight requirements ready => gateReady true
empty requirement catalogue => gateReady false
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn -pl law-todo -am '-Dtest=TodoAcceptanceReadinessServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: missing readiness service/mapper/view compilation failure.

- [ ] **Step 3: Implement the live SQL read model**

The mapper must compare the persisted mapping set with a `json_table` projection of the current 19-template catalogue and compute a symmetric mismatch count. It must count only `APPROVED` scenarios/mappings and require `PHASE_ONE_GOLDEN_PATH` to have nonblank dataset reference, 64-character checksum and positive data version.

- [ ] **Step 4: Implement deterministic requirement evaluation**

Map the eight check kinds to `READY`, `RUNTIME_MISSING` or `RUNTIME_INCOMPLETE`. `gateReady` is true only when all eight rows are ready and no catalogue mismatch exists.

- [ ] **Step 5: Verify GREEN**

Run the focused command from Step 2. Expected: all tests pass.

### Task 3: Implement governed scenario and AT mapping commands

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/command/TodoAcceptanceEvidenceCommands.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoAcceptanceScenarioView.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoAcceptanceMappingView.java`
- Create: `law-todo/src/main/java/com/law/todo/mapper/TodoAcceptanceEvidenceMapper.java`
- Create: `law-todo/src/main/resources/mapper/todo/TodoAcceptanceEvidenceMapper.xml`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoAcceptanceEvidenceService.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoAcceptanceEvidenceServiceTest.java`

**Interfaces:**
- Scenario commands: create/update with `actionId`, expected version, versioned JSON, dataset metadata, owner, acceptor, reviewer, due date, target status and conclusion.
- Mapping command: update one pre-seeded mapping with scenario, planned test reference, note, owner, reviewer, due date, target status and conclusion.
- Batch mapping command: mapping ids plus scenario/test values only; it cannot set approval, reviewer conclusion or terminal status.

- [ ] **Step 1: Write failing command-service tests**

Test strong validation and transitions:

```text
create scenario starts DRAFT
DRAFT -> IN_REVIEW requires steps, outcomes, dataset ref/checksum/version and three accountable users
reviewer differs from owner and acceptor
only selected reviewer can APPROVE/REJECT
REJECTED -> IN_REVIEW is allowed; APPROVED direct edit is rejected
UNMAPPED save with scenario+test becomes MAPPED
MAPPED -> IN_REVIEW requires owner/reviewer/due
mapping approval requires its scenario APPROVED
batch bind never approves mappings
same actionId/fingerprint replays; changed fingerprint conflicts
stale versions perform zero writes
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn -pl law-todo -am '-Dtest=TodoAcceptanceEvidenceServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- [ ] **Step 3: Implement commands and mapper**

Use active-user queries, conditional updates and the action ledger. Preserve immutable `acceptance_ref`, `template_code` and `dimension_code`. Store JSON as canonical text and never accept client-provided reviewed-by/time values.

- [ ] **Step 4: Implement state and independence rules**

Use stable business codes including:

```text
TODO_ACCEPTANCE_SCENARIO_STATE_INVALID
TODO_ACCEPTANCE_MAPPING_STATE_INVALID
TODO_ACCEPTANCE_ACCOUNTABILITY_REQUIRED
TODO_ACCEPTANCE_REVIEWER_INDEPENDENCE_REQUIRED
TODO_ACCEPTANCE_DATASET_REQUIRED
TODO_ACCEPTANCE_SCENARIO_NOT_APPROVED
TODO_ACCEPTANCE_VERSION_CONFLICT
TODO_ACCEPTANCE_ACTION_CONFLICT
```

- [ ] **Step 5: Verify GREEN**

Run the focused command from Step 2. Expected: all selected tests pass.

### Task 4: Expose APIs and enforce the G-07 approval guard

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoAcceptanceReadinessController.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoAcceptanceEvidenceController.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoAdmissionEvidenceService.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoAdmissionEvidenceServiceTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/TodoDefinitionManagementApiTest.java`

**Interfaces:**
- Read endpoints use `todo:admission:view`.
- Scenario/mapping create, update and batch bind use `todo:admission:edit`.
- `TodoAdmissionEvidenceService` injects `TodoAcceptanceReadinessService` and blocks G-07 approval with `TODO_ADMISSION_ACCEPTANCE_NOT_READY`.

- [ ] **Step 1: Add failing approval and permission tests**

Assert the G-07 evidence update performs no action claim or evidence update when the gate is false. Reflect over every new controller method and assert exact permission annotations.

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn -pl law-todo,ruoyi-admin -am '-Dtest=TodoAdmissionEvidenceServiceTest,TodoDefinitionManagementApiTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- [ ] **Step 3: Implement controllers and guard**

Use `@Valid`, current authenticated actor construction and stable response envelopes. The readiness endpoint defaults to `G-07`; list endpoints support optional template, dimension and status filters.

- [ ] **Step 4: Verify GREEN**

Run the focused command from Step 2. Expected: all selected tests pass.

### Task 5: Build the G-07 visual management surface

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/components/AcceptanceReadiness.vue`
- Create: `ruoyi-ui/src/views/todo/config/components/AcceptanceScenarioDialog.vue`
- Create: `ruoyi-ui/src/views/todo/config/components/AcceptanceMappingDialog.vue`
- Modify: `ruoyi-ui/src/views/todo/config/index.vue`
- Modify: `ruoyi-ui/src/api/todo-definition.js`
- Modify: `ruoyi-ui/scripts/check-todo-ui.js`
- Modify: `ruoyi-ui/e2e/todo-foundation-config-resources.spec.js`

**Interfaces:**
- Container receives the live readiness report and reloads after successful writes only.
- Dialogs emit saved records; failed requests preserve local input and table state.
- UI never shows a bulk approval action.

- [ ] **Step 1: Add failing frontend contract and E2E assertions**

Require API functions, all three components, readiness markers, the “28 个 Mock E2E 不计入 G-07” warning, scenario persistence, mapping filters, batch bind without approval, independent-review labels and the G-07 gate warning.

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd ruoyi-ui
npm run test:todo
npm run test:e2e -- e2e/todo-foundation-config-resources.spec.js --grep "phase-one acceptance"
```

Expected: missing component/API or test assertion failure.

- [ ] **Step 3: Implement API and components**

Add the “阶段一验收” tab. Show 19/114 catalogue facts, scenario/dataset/accountability counts, requirement statuses and mapping coverage. Provide version-aware scenario and mapping forms, filters and a non-terminal batch bind action.

- [ ] **Step 4: Verify GREEN and production compilation**

Run:

```powershell
npm run test:todo
npm run test:todo-schema
npm run test:encoding
npm run build:prod
npm run test:e2e -- e2e/todo-foundation-config-resources.spec.js --grep "phase-one acceptance"
```

Expected: all commands pass; only the repository's known bundle-size warnings may remain.

### Task 6: Verify real MySQL, update evidence and commit

**Files:**
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: `docs/superpowers/plans/2026-07-18-foundation-g07-acceptance-readiness.md`

**Interfaces:**
- Produces: measured G-07 evidence and a local commit; does not claim G-07 business approval.

- [ ] **Step 1: Initialize a fresh MySQL 8 container from the exact CI baseline**

Import the eleven SQL baseline files in `.github/workflows/ci.yml` order, then run `FlywayMigrationTest`. Expected terminal version: `0.20.21`.

- [ ] **Step 2: Query baseline truth directly**

Expected before authorized evidence entry:

```text
19 phase-one templates
114 AT mappings
114 UNMAPPED
0 scenarios
0 actions
G-07 gate false
existing 28 mocked E2E credited as 0 business acceptance items
```

- [ ] **Step 3: Run final full verification**

Run:

```powershell
mvn clean verify
cd ruoyi-ui
npm run test:todo
npm run test:todo-schema
npm run test:encoding
npm run build:prod
npm run test:e2e
```

Record exact Maven and E2E counts from fresh reports.

- [ ] **Step 4: Update the admission report**

Document the G-07 technical governance as ready while keeping the gate `FAIL` until authorized scenarios, data, mappings and reviewers are supplied. Update Flyway version and measured test counts only from command output.

- [ ] **Step 5: Audit and commit locally**

Run `git diff --check`, review staged names and commit:

```text
feat(todo): govern phase-one acceptance readiness
```

Do not push. Stop the disposable MySQL container and verify the worktree is clean.

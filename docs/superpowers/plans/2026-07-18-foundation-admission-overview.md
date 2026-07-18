# Foundation Admission Overview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one repository-authoritative 8/8 Foundation admission gate that aggregates G-01 through G-08, exposes the measured blockers in the Todo configuration UI, and runs on the `v0.2-Foundation` CI branch without manufacturing any business decision or approval.

**Architecture:** A focused MyBatis read model measures G-01, G-03 and G-08 directly from the existing decision, PRD catalogue, Flyway and idempotency schemas. `TodoFoundationAdmissionReadinessService` composes those facts with the existing G-02/G-04/G-05/G-06/G-07 live readiness services and independently approved admission-evidence rows. A read-only controller and a first-position configuration tab display the aggregate result; no command can override a failed gate.

**Tech Stack:** Java 17, Spring Boot, MyBatis, MySQL 8, Vue 2, Element UI, Playwright, GitHub Actions.

## Global Constraints

- Repository code, current Flyway state and automated verification are authoritative.
- Foundation scope remains the 13 platform DoD items; business pages and final handlers for missing non-litigation/enforcement/classification domains stay outside this slice.
- G-01 requires exactly 12 Q decisions with accountability and all eight `PHASE_ONE` decisions `CLOSED`.
- G-02/G-04/G-05/G-06/G-07 require both their live technical/source gate and an independently `APPROVED` admission-evidence record.
- G-03 requires exactly 25 unique TD catalogue rows, all definition packages `READY`, valid JSON definitions and six unique acceptance references per template.
- G-08 requires a successful forward migration chain containing this release migration, no failed Flyway row, and the four core idempotency indexes.
- The aggregate remains `NOT_ADMITTED` unless all eight gates are ready; tests and UI must never turn missing business evidence into approval.
- Work only on `v0.2-Foundation`; commit locally and do not push without explicit user instruction.

---

### Task 1: Backend aggregate read model

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/mapper/TodoFoundationAdmissionReadinessMapper.java`
- Create: `law-todo/src/main/resources/mapper/todo/TodoFoundationAdmissionReadinessMapper.xml`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoFoundationAdmissionGateView.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoFoundationAdmissionReadinessView.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoFoundationAdmissionReadinessService.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoFoundationAdmissionReadinessServiceTest.java`

**Interfaces:**
- Consumes: the existing `gateReady(String)` methods for G-02/G-04/G-05/G-06/G-07 and one mapper row containing decision, catalogue, evidence, Flyway and index counts.
- Produces: `TodoFoundationAdmissionReadinessView readiness()` with `overallStatus`, `readyGateCount`, `totalGateCount`, `admitted` and ordered G-01..G-08 gate rows.

- [x] **Step 1: Write failing service tests**

Cover the truthful baseline (`2/8`, only G-03 and G-08 ready), the all-ready result (`8/8`, `ADMITTED_FOR_PHASE_ONE_BUSINESS_IMPLEMENTATION`), and the rule that approved evidence cannot bypass a false live readiness service.

- [x] **Step 2: Run the focused test and verify RED**

Run: `mvn -pl law-todo -am -Dtest=TodoFoundationAdmissionReadinessServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because the aggregate service and views do not exist.

- [x] **Step 3: Implement the mapper, views and service**

The mapper must return exactly one row with:

```text
decision_total, decision_accountable, phase_one_total, phase_one_closed
prd_total, prd_ready, prd_valid_definition, prd_acceptance_ref_total
g02_evidence_status, g04_evidence_status, g05_evidence_status,
g06_evidence_status, g07_evidence_status
foundation_migration_present, failed_migration_count, core_idempotency_index_count
```

The service must construct eight deterministic gate rows and never accept a catalogue size other than the exact expected size.

- [x] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2 and expect all aggregate readiness tests to pass.

### Task 2: API and permission contract

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoFoundationAdmissionReadinessController.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/TodoDefinitionManagementApiTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/TodoFoundationAdmissionReadinessControllerTest.java`

**Interfaces:**
- Consumes: `TodoFoundationAdmissionReadinessService.readiness()`.
- Produces: `GET /todo/foundation-admission` guarded by `todo:admission:view` and wrapped in `AjaxResult.success`.

- [x] **Step 1: Write failing controller and permission tests**

Assert the response delegates to the service and reflection finds the exact `@PreAuthorize("@ss.hasPermi('todo:admission:view')")` contract.

- [x] **Step 2: Verify RED**

Run: `mvn -pl ruoyi-admin -am -Dtest=TodoFoundationAdmissionReadinessControllerTest,TodoDefinitionManagementApiTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because the controller is absent.

- [x] **Step 3: Add the read-only controller**

Implement only one GET endpoint. Do not add an admission override or mutation endpoint.

- [x] **Step 4: Verify GREEN**

Run the command from Step 2 and expect both tests to pass.

### Task 3: Frontend admission overview

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/components/FoundationAdmissionOverview.vue`
- Modify: `ruoyi-ui/src/api/todo-definition.js`
- Modify: `ruoyi-ui/src/views/todo/config/index.vue`
- Modify: `ruoyi-ui/scripts/check-todo-ui.js`
- Modify: `ruoyi-ui/e2e/todo-foundation-config-resources.spec.js`

**Interfaces:**
- Consumes: `getFoundationAdmissionReadiness()`.
- Produces: a first-position `准入总览` tab showing the aggregate state, `n/8` progress, each gate's technical/evidence status and concrete blockers.

- [x] **Step 1: Add failing source-contract and Playwright expectations**

Require the new API, component and tab; in the mocked baseline assert `2/8` and `NOT_ADMITTED`, and assert no admitted copy appears.

- [x] **Step 2: Verify RED**

Run:

```powershell
npm --prefix ruoyi-ui run test:todo
npm --prefix ruoyi-ui run test:e2e -- e2e/todo-foundation-config-resources.spec.js --grep "aggregate admission"
```

Expected: both focused checks fail because the overview is absent.

- [x] **Step 3: Implement the API, component and page integration**

The component is read-only. It must visibly state that technical readiness does not replace authorized decisions or independent review.

- [x] **Step 4: Verify GREEN**

Run the commands from Step 2 and expect both to pass.

### Task 4: CI scope, full verification and evidence report

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: this plan file

**Interfaces:**
- Consumes: all backend/frontend gates already present in the repository.
- Produces: CI triggers for `v0.2-Foundation`, executes Todo schema contracts, and a report whose aggregate numbers come from the new runtime gate.

- [x] **Step 1: Update CI source-contract expectations**

Add a repository test or source assertion requiring `v0.2-Foundation` in both push and pull-request triggers and `npm run test:todo-schema` in the frontend job.

- [x] **Step 2: Verify the source contract fails before the workflow edit**

Run the focused contract check and confirm it reports the stale `V0.17` scope.

- [x] **Step 3: Update the workflow and admission report**

Rename the workflow for Foundation, include `v0.2-Foundation` triggers, run `test:todo-schema`, and document that the current measured aggregate remains `2/8 NOT_ADMITTED` until authorized inputs close the remaining gates.

- [x] **Step 4: Run complete verification**

Run:

```powershell
mvn clean verify
npm --prefix ruoyi-ui run test:todo
npm --prefix ruoyi-ui run test:todo-schema
npm --prefix ruoyi-ui run test:encoding
npm --prefix ruoyi-ui run build:prod
npm --prefix ruoyi-ui run test:e2e
git diff --check
```

Also execute the aggregate mapper SQL against the disposable MySQL 8 baseline after applying the complete Flyway chain.

- [x] **Step 5: Audit and commit locally**

Confirm the worktree contains only this slice, update every checkbox from measured output, and commit with:

```text
feat(todo): aggregate Foundation admission readiness
```

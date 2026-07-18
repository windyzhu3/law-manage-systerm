# Decision Accountability Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every blocking PRD decision assignable to a concrete user with a responsibility role, due date and delivery phase, without inferring or closing any business decision.

**Architecture:** Extend `todo_decision` through a forward-only Flyway migration, carry governance fields through the existing command/service/mapper/view boundary, and expose active user choices through the Todo decision API. Reuse the existing decision registry UI, idempotent action ledger and optimistic versioning; publish remains blocked while decisions are OPEN.

**Tech Stack:** Java 17, Spring Boot, MyBatis, Flyway, MySQL 8, Vue 2, Element UI, Node contract tests, Playwright.

## Global Constraints

- The repository is authoritative.
- Q-001 through Q-012 remain OPEN until an authorized business owner supplies a conclusion and resolution.
- Blocking decisions require `ownerUserId`, `ownerRoleKey`, `dueAt` and `deliveryPhase` on create or update.
- `deliveryPhase` is exactly one of `PHASE_ONE`, `PHASE_TWO`, or `CROSS_PHASE`.
- Only active, non-deleted `sys_user` rows may be selected as owners.
- Existing decision action idempotency and optimistic version checks remain unchanged.
- Database changes use a new migration; no applied migration is edited.

---

### Task 1: Backend decision governance contract

**Files:**
- Test: `law-todo/src/test/java/com/law/todo/application/TodoDecisionManagementServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoDecisionCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoDecisionView.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDecisionManagementService.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoDefinitionCatalogController.java`

**Interfaces:**
- Consumes: existing `CreateDecisionCommand`, `UpdateDecisionCommand`, `TodoDefinitionCatalogController` decision routes.
- Produces: governance fields in commands/views plus `GET /todo/decision-governance-options` for active users, active stable role keys and fixed delivery phases.

- [x] Write service tests that reject missing accountability, persist all governance fields, expose them in the view and list active owner options.
- [x] Run the focused tests and observe failures caused by missing fields/methods.
- [x] Add the command/view fields, validation, persistence mapping, owner validation and owner-options query.
- [x] Rerun focused tests and mapper/controller contract tests.

### Task 2: Forward database migration and live invariant

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_15__todo_decision_accountability.sql`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

**Interfaces:**
- Consumes: Task 1 mapper columns.
- Produces: `owner_user_id`, `owner_role_key`, `due_at`, `delivery_phase` and query indexes.

- [x] Add failing migration assertions for terminal version `0.20.15`, four columns and 12 seeded OPEN decisions remaining unassigned.
- [x] Run against a v0.20.14 isolated database and observe the expected version/schema failure.
- [x] Add the migration with nullable accountability fields for pre-existing OPEN decisions and phase values derived only from the approved two-phase plan.
- [x] Recreate the isolated database and prove the full migration chain and database invariants.

### Task 3: Frontend accountability editor

**Files:**
- Modify: `ruoyi-ui/src/api/todo-definition.js`
- Modify: `ruoyi-ui/src/views/todo/config/resource-contract.js`
- Modify: `ruoyi-ui/src/views/todo/config/components/DecisionRegistry.vue`
- Modify: `ruoyi-ui/scripts/check-todo-config-resources.js`
- Modify: `ruoyi-ui/e2e/todo-foundation-config-resources.spec.js`

**Interfaces:**
- Consumes: `GET /todo/decision-governance-options` and extended decision DTO.
- Produces: owner, responsibility role, due date and phase columns/editor controls.

- [x] Add contract assertions that blocking decisions without governance fail validation.
- [x] Run `npm run test:todo-config` and observe the expected failure.
- [x] Add owner-options loading, governance fields, validation and overdue/unassigned indicators.
- [x] Extend Playwright to assign a decision, save, reload, resolve and reopen while preserving governance.
- [x] Run contract test and focused E2E to green.

### Task 4: Verification and local commit

**Files:**
- Modify: `doc/v0.2-foundation-admission-report.md`

- [x] Update G-01 to distinguish “governance mechanism ready” from “12 decisions actually assigned/frozen”.
- [x] Run Maven full tests, frontend contracts, UTF-8, production build and full E2E.
- [x] Run `git diff --check` and inspect the staged diff.
- [x] Commit locally with `feat(todo): govern PRD decision accountability`.

# Foundation Resource Readiness Implementation Plan

> **For Codex:** Execute this plan in the existing `v0.2-Foundation` worktree using test-driven development. Repository code, migrations, and repository documentation are authoritative. Do not invent business values or role mappings.

**Goal:** Make G-02 auditable by cataloguing every v0.2 dictionary type and stable role key required by the repository, comparing that catalog with the live RuoYi dictionaries and roles, and preventing a false G-02 approval while resources or source decisions remain unresolved.

**Architecture:** Add an immutable, migration-seeded Foundation resource requirement catalog in `law-todo`. A read service computes source and runtime readiness without mutating `sys_dict_*` or `sys_role`. The existing admission evidence workflow consults the computed G-02 result before approval. The Todo configuration UI exposes a read-only resource-readiness tab.

**Tech Stack:** Java 17, Spring Boot, MyBatis, MySQL 8 JSON, Vue 2 + Element UI, Flyway, JUnit 5, Mockito, Jest/Node contract tests, Playwright.

---

### Task 1: Backend readiness contract

- [x] Add failing service tests for dictionary, role, unresolved-source, missing-value, and gate-summary states.
- [x] Add `TodoFoundationResourceMapper`, XML queries, view records, and `TodoFoundationResourceService`.
- [x] Return repository source, phase, decision reference, expected values, runtime counts, and computed status.
- [x] Keep the service read-only; it must never create runtime dictionaries or roles.

### Task 2: G-02 approval guard

- [x] Add a failing test proving G-02 cannot transition to `APPROVED` when catalog readiness is incomplete.
- [x] Inject the resource-readiness service into `TodoAdmissionEvidenceService`.
- [x] Preserve independent reviewer, optimistic version, and idempotency behavior.

### Task 3: Forward-only migration

- [x] Add `V0_20_17__foundation_resource_readiness.sql`.
- [x] Seed every dictionary and role key named by repository documentation or the TD-001..TD-025 definition catalog.
- [x] Store only explicit repository values; mark unknown values and conflicting execution-role naming as unresolved.
- [x] Add Flyway invariant tests for catalog size, explicit values, conflict rows, and absence of invented runtime dictionary/role inserts.

### Task 4: API and frontend visibility

- [x] Add a permission-protected read endpoint using `todo:admission:view`.
- [x] Add API permission tests.
- [x] Add a “基础资源” tab with summary cards and a filterable table.
- [x] Clearly distinguish `SOURCE_UNRESOLVED`, `RUNTIME_MISSING`, `RUNTIME_INCOMPLETE`, and `READY`.
- [x] Add frontend resource-contract and Playwright coverage.

### Task 5: Verification and evidence

- [x] Run focused backend tests and frontend contract tests.
- [x] Run real MySQL Flyway migration and invariant tests.
- [x] Run full Maven verification, frontend production build, and Playwright suite.
- [x] Update the Foundation admission report with exact G-02 counts; keep overall status `NOT_ADMITTED` until independent approvals and runtime resources are complete.
- [x] Commit locally on `v0.2-Foundation`; do not push unless requested.

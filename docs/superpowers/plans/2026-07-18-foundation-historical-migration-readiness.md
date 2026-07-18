# Foundation Historical Migration Readiness Plan

> **For Codex:** Execute in `v0.2-Foundation` with TDD. Repository schema, migrations, tests and documentation are authoritative. Do not select a default business line or mutate historical business data.

**Goal:** Turn G-04 from a prose-only gap into a machine-verifiable, read-only migration-readiness gate.

**Architecture:** Seed a fixed checklist through a forward-only Flyway migration. A dedicated service combines checklist source status with live schema/data invariants. The admission workflow refuses G-04 approval while any item is unresolved or invalid. The Todo configuration UI exposes the checklist and current inventory without providing a bypass or automatic backfill.

**Boundary:** This slice does not add `biz_case.business_line`, choose its historical default, rewrite a Todo instance, or approve G-04. Those actions require an independently reviewed migration artifact and later forward migrations.

## Task 1: Red tests

- [x] Add application tests for source-unresolved, missing-schema, invalid-reference and ready states.
- [x] Add an API permission contract for the read-only readiness endpoint.
- [x] Add a migration contract proving no historical business rows are updated.
- [x] Extend the real Flyway invariant test with checklist counts and live inventory.
- [x] Add frontend contract and E2E expectations for the historical-migration tab.

## Task 2: Backend and migration

- [x] Add `todo_foundation_migration_requirement` through `V0_20_18`.
- [x] Seed only requirements supported by repository evidence.
- [x] Compute live `biz_case.business_line` schema presence and Todo template-version referential integrity.
- [x] Block G-04 approval unless the computed gate is ready.
- [x] Expose a permission-protected read endpoint.

## Task 3: Frontend

- [x] Add a read-only historical migration readiness component.
- [x] Show source blockers separately from runtime/schema blockers.
- [x] Show historical case and Todo inventory counts.
- [x] Integrate the component into Todo configuration without changing existing permissions.

## Task 4: Verification

- [x] Run focused backend tests.
- [x] Run UI contract and focused E2E tests.
- [x] Run a real MySQL Flyway chain and invariants.
- [x] Update the admission report from measured results only.
- [x] Run final diff review and commit locally; do not push unless requested.

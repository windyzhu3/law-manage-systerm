# Foundation G-06 Finance Review Package Implementation Plan

**Goal:** Prepare an independently reviewable finance/formula package without inventing node-fee policy, collection ownership, risk formula, rounding, tax, refund, or recalculation rules.

**Architecture:** A repository review document records current finance facts, the exact Q-009/Q-012 decision inputs, candidate additive schema boundaries, event/idempotency requirements, reconciliation SQL and controlled sign-off. A forward-only metadata migration links only the existing `FINANCE_BUSINESS_SIGNOFF` row to the package and keeps G-06 unresolved.

**Tech Stack:** MySQL 8.4 design, Flyway metadata migration, Java 17, JUnit 5, Maven.

## Global Constraints

- Work only on `v0.2-Foundation`; commit locally and do not push without explicit user instruction.
- Do not close Q-009/Q-012, choose formulas, owners, rounding, tax, refund or recalculation rules.
- Do not alter finance business tables or create receivable, collection, refund or risk-calculation facts in this slice.
- Keep `FINANCE_BUSINESS_SIGNOFF=NEEDS_REVIEW`, `G06-FINANCE-FORMULA=OPEN`, G-06 at 3/9 READY and aggregate admission at `2/8 NOT_ADMITTED`.
- All production changes follow RED/GREEN TDD.

## Task 1: Establish the failing contract

- [x] Add a contract test requiring a complete review package and a metadata-only migration.
- [x] Run the focused test and confirm RED because both artifacts are absent.

## Task 2: Prepare review inputs without approving policy

- [x] Document all nine G-06 requirement codes and current repository facts.
- [x] Define the mandatory Q-009/Q-012 decision record, including formula version, precision, rounding, tax, refund and recalculation boundaries.
- [x] Define candidate additive schema and immutable finance-event/idempotency boundaries without executing DDL.
- [x] Define preflight, reconciliation, rollback/reversal and reviewer evidence requirements.
- [x] Add `V0_20_26` that links only `FINANCE_BUSINESS_SIGNOFF` while preserving `NEEDS_REVIEW`.
- [x] Run the focused test and confirm GREEN.

## Task 3: Verify and report

- [x] Run full backend, frontend, Flyway and E2E verification against disposable MySQL 8.4.
- [x] Confirm Q-009/Q-012 remain OPEN, G-06 remains 3/9, evidence remains OPEN and aggregate remains `2/8 NOT_ADMITTED`.
- [x] Update the admission report with measured totals and the package boundary.
- [x] Audit, remove the disposable database and commit locally without pushing.

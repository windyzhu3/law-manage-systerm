# Foundation G-04 Migration Review Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare an independently reviewable G-04 historical-case migration package with exact inventory, idempotency, validation and guarded rollback designs, without selecting a default business line or changing historical data.

**Architecture:** A repository review document defines the proposed additive schema and parameterized migration algorithm, including an exception register, immutable batch audit, preflight queries, post-migration validation and change-aware rollback guard. A forward-only metadata migration links the four `NEEDS_EVIDENCE` requirements to this package but deliberately leaves their statuses unresolved until architecture, case-management and DBA reviewers sign off.

**Tech Stack:** MySQL 8.4 SQL design, Flyway metadata migration, Java 17, JUnit 5, Maven.

## Global Constraints

- Work only on `v0.2-Foundation`; commit locally and do not push without explicit user instruction.
- The review package is not a deployable backfill migration and must not be represented as one.
- Do not add `biz_case.business_line`, alter `biz_case`, update a case, rewrite a Todo instance, or choose the historical default.
- Keep `HISTORICAL_CASE_DEFAULT=NEEDS_DECISION` and the four review requirements at `NEEDS_EVIDENCE`.
- Keep `G04-HISTORICAL-MIGRATION=OPEN`, G-04 at 2/8 READY and aggregate admission at `2/8 NOT_ADMITTED`.
- The proposed algorithm may accept only `NON_LITIGATION`, `COMPREHENSIVE`, or `EXECUTION` after a reviewed decision.
- Rollback must never overwrite a row changed after the migration batch.
- All production changes follow RED/GREEN TDD.

---

### Task 1: Establish failing review-package contracts

**Files:**
- Create: `law-todo/src/test/java/com/law/todo/integration/HistoricalMigrationReviewPackageContractTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

**Interfaces:**
- Consumes: the eight fixed G-04 requirement codes from `V0_20_18`.
- Produces: static evidence-boundary and live-MySQL assertions for a non-approving review package.

- [x] **Step 1: Write the failing package contract**

The document contract must require:

```java
assertTrue(document.contains("PENDING_ARCHITECTURE_CASE_DBA_REVIEW"));
assertTrue(document.contains("Reviewer：未指定"));
assertTrue(document.contains("默认业务线：未决定"));
assertTrue(document.contains("不得由开发人员代签"));
```

It must require all eight G-04 requirement codes, the three allowed business-line values, the exception register, a batch unique key, immutable audit, preflight counts, invalid/missing-value validation, orphan Todo-version validation and a rollback predicate that matches both migrated value and migration timestamp. It must reject any development approval marker.

- [x] **Step 2: Write the failing metadata-migration contract**

Require `V0_20_25__foundation_g04_migration_review_package.sql` to:

- link exactly the four `NEEDS_EVIDENCE` requirement codes to the package;
- keep `source_status='NEEDS_EVIDENCE'` only in the `WHERE` guard;
- avoid assigning `source_status`, touching `todo_admission_evidence`, altering/updating `biz_case`, updating `todo_instance`, assigning reviewers/owners, or containing `'APPROVED'`.

- [x] **Step 3: Extend real MySQL invariants**

Advance the expected terminal version to `0.20.25` and assert:

```java
assertEquals(4L,count(connection,"select count(*) from todo_foundation_migration_requirement where gate_code='G-04' and source_status='NEEDS_EVIDENCE' and source_ref='doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md'"));
assertEquals(0L,count(connection,"select count(*) from information_schema.columns where table_schema=database() and table_name='biz_case' and column_name='business_line'"));
```

Retain all existing G-04 source-count and orphan-version assertions.

- [x] **Step 4: Run focused tests and verify RED**

Run:

```powershell
mvn -pl law-todo -am "-Dtest=HistoricalMigrationReviewPackageContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation succeeds and the test fails because the package and `V0_20_25` do not exist.

### Task 2: Write the exact non-executing migration design

**Files:**
- Create: `doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md`
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_25__foundation_g04_migration_review_package.sql`

**Interfaces:**
- Produces: reviewer-ready SQL design and repository linkage.
- Preserves: all current runtime schemas, rows, requirement statuses, decision statuses and admission evidence.

- [x] **Step 1: Document purpose, boundary and fixed evidence**

Declare status `PENDING_ARCHITECTURE_CASE_DBA_REVIEW`, unspecified reviewers, undecided default and no approval. Map every G-04 requirement to repository evidence and state that code review does not replace case-management/DBA sign-off.

- [x] **Step 2: Define preflight inventory and exception output**

Provide executable read-only SQL for active/deleted case counts, case-status/type distribution and orphan Todo version references. Define the immutable CSV/export fields for the unclassified-case exception list and its SHA-256 evidence reference.

- [x] **Step 3: Define additive schema and parameterized batch idempotency**

Design, but do not execute:

- nullable `biz_case.business_line` plus an index;
- `biz_case_business_line_exception` with unique case ID and controlled review states;
- `biz_case_business_line_migration_batch` with unique `batch_key`, selected reviewed default, status and counts;
- `biz_case_business_line_migration_audit` with unique `(batch_id,case_id)`, old/new values, old/update timestamps and reason.

The backfill procedure takes `p_batch_key` and `p_default_business_line`, rejects values outside the three confirmed codes, inserts audit rows once, updates only still-null rows and uses the audit key as the resume point.

- [x] **Step 4: Define validation and guarded rollback SQL**

Validation must cover input/output counts, allowed values, pending exceptions, audit uniqueness, post-update timestamps and Todo orphan versions. Rollback may restore a row only when its current business line still equals the audit `new_business_line` and its `update_time` still equals `migrated_at`; changed rows are emitted as manual exceptions.

- [x] **Step 5: Define reviewer record and controlled closure**

Require architecture, case-management and DBA reviewers, target SHA, actual database/environment, preflight/export checksums, chosen default decision reference, executed commands, validation output, rollback rehearsal and conclusion. Explain that a later reviewed Flyway may add schema/backfill and a later metadata migration may change the four statuses.

- [x] **Step 6: Link the package without changing status**

`V0_20_25` updates only `source_ref` and `remark` for the four fixed requirement codes under `source_status='NEEDS_EVIDENCE'`.

- [x] **Step 7: Run focused tests and verify GREEN**

Run the command from Task 1 Step 4. Expected: both package-contract tests pass.

### Task 3: Verify and report measured evidence

**Files:**
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: this plan file

**Interfaces:**
- Produces: measured repository/MySQL evidence with unchanged gate truth.

- [x] **Step 1: Run complete backend and frontend verification**

Use a disposable MySQL 8.4 database initialized from the exact v0.15 baseline, run `mvn clean verify`, parse Surefire totals, then run frontend contracts, production build and all Playwright E2E.

- [x] **Step 2: Query live G-04 and aggregate blockers**

Verify terminal Flyway `0.20.25`, G-04 remains 3 `CONFIRMED`, 1 `NEEDS_DECISION`, 4 `NEEDS_EVIDENCE`, `biz_case.business_line` remains absent, orphan Todo versions remain zero, all five admission evidence rows remain OPEN and all twelve decisions remain OPEN.

- [x] **Step 3: Update the admission report truthfully**

Record that the review package is available and linked while G-04 remains 2/8 READY and FAIL. Update migration/test totals only from fresh evidence; keep aggregate `2/8 NOT_ADMITTED`.

- [x] **Step 4: Audit and commit locally**

Run `git diff --check`, inspect staged paths, remove the disposable database, and commit:

```text
docs(migration): prepare G04 independent review package
```

Do not push.

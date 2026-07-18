# Foundation G05 Security Review Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing G-05 file-security implementation independently reviewable from one repository package without assigning a reviewer, manufacturing a sign-off, or changing the gate from its truthful 6/7 blocked state.

**Architecture:** A version-controlled Markdown review package maps each fixed G-05 requirement to production code, database invariants and executable tests, records residual risks, and provides blank review/signature fields. A forward-only Flyway migration points `SECURITY_REVIEW_SIGNOFF` at that package while leaving `source_status='NEEDS_REVIEW'`; contract and real-MySQL tests prevent the migration from granting permissions or approving evidence.

**Tech Stack:** Java 17, JUnit 5, Flyway, MySQL 8, Markdown.

## Global Constraints

- Repository code, current Flyway state and automated verification are authoritative.
- The package may prepare independent review evidence but must never assign an owner/reviewer, sign, approve, or close G-05.
- `SECURITY_REVIEW_SIGNOFF` remains `NEEDS_REVIEW`; `G05-FILE-SECURITY` remains `OPEN` until an authorized independent reviewer acts through the governed evidence workflow.
- Existing executed migrations are immutable; use only a new forward migration.
- Work only on `v0.2-Foundation`; commit locally and do not push without explicit user instruction.

---

### Task 1: Review-package contract

**Files:**
- Create: `law-todo/src/test/java/com/law/todo/integration/FileSecurityReviewPackageContractTest.java`
- Create: `doc/reviews/v0.2-foundation-g05-file-security-review-package.md`

**Interfaces:**
- Consumes: fixed G-05 requirement codes and repository evidence paths.
- Produces: one review package with current verdict, evidence matrix, executable verification commands, residual-risk checklist and blank sign-off fields.

- [x] **Step 1: Write the failing contract test**

Require all seven requirement codes, the `PENDING_INDEPENDENT_REVIEW` verdict, the 21-material E2E reference, explicit residual-risk questions, and blank reviewer/signature fields.

- [x] **Step 2: Verify RED**

Run:

```powershell
mvn -pl law-todo -am -Dtest=FileSecurityReviewPackageContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the review package and forward migration do not exist.

- [x] **Step 3: Write the review package**

Document the exact code/test evidence and state explicitly that development evidence does not replace independent review.

- [x] **Step 4: Verify the document half of the contract**

Run the focused test after adding the document; the remaining expected failure must name the absent forward migration.

### Task 2: Forward-only evidence reference

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_23__foundation_file_security_review_package.sql`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoFoundationAdmissionReadinessMapper.xml`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoFoundationAdmissionReadinessService.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/integration/FileSecurityReviewPackageContractTest.java`

**Interfaces:**
- Consumes: the review package path.
- Produces: terminal Flyway `0.20.23`, with `SECURITY_REVIEW_SIGNOFF.source_ref` pointing to the package and its status still `NEEDS_REVIEW`.

- [x] **Step 1: Extend the failing contract**

Assert the migration targets only `SECURITY_REVIEW_SIGNOFF`, preserves `NEEDS_REVIEW`, and contains no permission, reviewer, admission-evidence or approval mutation.

- [x] **Step 2: Add the minimal migration and terminal-version updates**

Update only the package reference/remark and advance G-08's terminal migration check from `0.20.22` to `0.20.23`.

- [x] **Step 3: Verify focused GREEN**

Run the focused test from Task 1 and the Foundation admission service tests.

- [x] **Step 4: Verify real MySQL invariants**

Run `FlywayMigrationTest` against a clean disposable MySQL 8 database and assert 6 confirmed controls, one `NEEDS_REVIEW`, five open admission-evidence rows and terminal version `0.20.23`.

### Task 3: Report and full verification

**Files:**
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: this plan file

**Interfaces:**
- Consumes: fresh focused/full verification output.
- Produces: an admission report that links the review package but still reports `2/8 NOT_ADMITTED` and G-05 at 6/7.

- [x] **Step 1: Update the report from measured facts**

Advance only the Flyway/test evidence and package availability; do not alter gate counts or admission status.

- [x] **Step 2: Run complete verification**

Run Maven full verification, frontend Todo/schema/encoding contracts, production build, Playwright E2E, `git diff --check`, and the real-MySQL migration/material E2E tests.

- [x] **Step 3: Audit and commit locally**

Confirm the diff contains only this review-enablement slice and commit with:

```text
docs(security): prepare G05 independent review package
```

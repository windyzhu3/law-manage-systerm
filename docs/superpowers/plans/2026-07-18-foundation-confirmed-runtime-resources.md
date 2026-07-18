# Foundation Confirmed Runtime Resources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Materialize the two G-02 resources whose stable values are already authoritative in the repository, without promoting any unresolved dictionary value or conflicting execution role.

**Architecture:** A forward-only Flyway migration creates the `law_business_line` dictionary with exactly the three repository-confirmed values and creates a least-privilege `sales` role key only when absent. The existing live-readiness query remains authoritative; real MySQL invariants prove the catalogue advances from 5/51 to 7/51 READY while G-02 remains blocked by unresolved sources.

**Tech Stack:** MySQL 8.4, Flyway, Java 17, JUnit 5, Maven, existing RuoYi dictionary and role tables.

## Global Constraints

- Work only on `v0.2-Foundation`; commit locally and do not push without explicit user instruction.
- Add only `V0_20_24__foundation_confirmed_runtime_resources.sql`; never edit an applied migration.
- The only promoted dictionary is `law_business_line`, with values `NON_LITIGATION`, `COMPREHENSIVE`, and `EXECUTION`.
- The only promoted role key is `sales`; it receives self data scope and no menu, department, or user grants.
- Do not create any resource whose source status is `NEEDS_DECISION` or `CONFLICTING`.
- Do not change G-02 evidence status, aggregate admission `2/8`, or any Q-001～Q-012 decision.
- All production changes follow RED/GREEN TDD.

---

### Task 1: Lock the promotion boundary with failing contracts

**Files:**
- Modify: `law-todo/src/test/java/com/law/todo/integration/FoundationResourceMigrationContractTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

**Interfaces:**
- Consumes: the fixed G-02 catalogue seeded by `V0_20_17`.
- Produces: static and real-MySQL assertions for the two approved resources and the unchanged unresolved counts.

- [x] **Step 1: Add the failing migration contract**

Add a second contract test that reads `V0_20_24__foundation_confirmed_runtime_resources.sql` and asserts:

```java
assertTrue(sql.contains("'law_business_line'"));
assertTrue(sql.contains("'non_litigation'"));
assertTrue(sql.contains("'comprehensive'"));
assertTrue(sql.contains("'execution'"));
assertTrue(sql.contains("'sales'"));
assertFalse(sql.contains("insert into sys_role_menu"));
assertFalse(sql.contains("insert into sys_user_role"));
assertFalse(sql.contains("update todo_foundation_resource_requirement"));
```

Also enumerate all quoted `law_*` dictionary types and stable role keys in the file and require their sets to equal `Set.of("law_business_line")` and `Set.of("sales")` respectively.

- [x] **Step 2: Add real MySQL invariants**

Extend `verifyFoundationResourceReadinessSchema` to assert:

```java
assertEquals(1L, count(connection, "select count(*) from sys_dict_type where dict_type='law_business_line' and status='0'"));
assertEquals(3L, count(connection, "select count(*) from sys_dict_data where dict_type='law_business_line' and status='0' and dict_value in ('NON_LITIGATION','COMPREHENSIVE','EXECUTION')"));
assertEquals(1L, count(connection, "select count(*) from sys_role where role_key='sales' and status='0' and del_flag='0'"));
assertEquals(0L, count(connection, "select count(*) from sys_role_menu rm join sys_role r on r.role_id=rm.role_id where r.role_key='sales'"));
```

Keep the catalogue assertions at 39 `NEEDS_DECISION` and 5 `CONFLICTING`.

- [x] **Step 3: Run focused tests and verify RED**

Run:

```powershell
mvn -pl law-todo -am "-Dtest=FoundationResourceMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: the new test fails because `V0_20_24__foundation_confirmed_runtime_resources.sql` does not exist.

### Task 2: Add the forward-only runtime resource migration

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_24__foundation_confirmed_runtime_resources.sql`

**Interfaces:**
- Produces: active `law_business_line` dictionary data and an active `sales` role key.
- Preserves: existing IDs, roles, permissions, users, unresolved source states and all admission evidence.

- [x] **Step 1: Insert the dictionary type idempotently**

Use `insert ... select ... where not exists` for `sys_dict_type`, with name `律所业务线`, status `0`, creator `migration`, and a remark identifying the G-02 confirmed source.

- [x] **Step 2: Insert exactly three dictionary values idempotently**

Insert, in order:

```text
NON_LITIGATION -> 非诉
COMPREHENSIVE   -> 综法
EXECUTION       -> 执行
```

Each value uses status `0`; uniqueness is checked by `(dict_type, dict_value)`.

- [x] **Step 3: Insert a least-privilege sales role idempotently**

Insert role name `销售人员`, role key `sales`, sort `35`, data scope `5`, strict menu/department checks, active/not-deleted status, and no grants. Do not insert into `sys_role_menu`, `sys_role_dept`, or `sys_user_role`.

- [x] **Step 4: Run focused tests and verify GREEN**

Run the command from Task 1 Step 3. Expected: both migration contract tests pass.

### Task 3: Verify real readiness and report measured evidence

**Files:**
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: this plan file

**Interfaces:**
- Produces: measured Flyway and G-02 evidence.
- Does not approve G-02 or alter aggregate admission.

- [x] **Step 1: Run the complete backend and frontend gates**

Import the exact v0.15 SQL baseline into a disposable MySQL 8.4 database, run `mvn clean verify`, parse Surefire totals, then run all frontend contracts, production build and Playwright E2E.

- [x] **Step 2: Query the live G-02 result**

Verify Flyway ends at `0.20.24`, the two promoted resources are active, the live ready count is 7/51, and unresolved counts remain 39 `NEEDS_DECISION` plus 5 `CONFLICTING`.

- [x] **Step 3: Update the admission report without overstating readiness**

Update Flyway/test totals and describe G-02 as 7/51 READY. Keep G-02 FAIL, G-04 2/8, G-05 6/7, G-06 3/9, G-07 3/8 and aggregate `2/8 NOT_ADMITTED` unless fresh database evidence proves otherwise.

- [x] **Step 4: Audit and commit locally**

Run `git diff --check`, inspect staged paths, remove the disposable database, and commit:

```text
feat(foundation): materialize confirmed G02 resources
```

Do not push.

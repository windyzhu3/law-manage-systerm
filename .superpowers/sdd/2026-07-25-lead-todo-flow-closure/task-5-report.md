# Task 5 — Lead data model, dictionaries, permissions, and event contracts

## Status

Complete. Task 5 adds only the forward migration `V0_20_48__lead_todo_flow.sql`, stable Java contracts, lead-domain mappings, and focused optimistic-lock writes. It does not implement Task 6 business services and does not recreate the Todo-owned runtime tables delivered by `V0_20_46`.

## RED evidence

Command:

```powershell
mvn -pl law-todo -Dtest=LeadTodoFlowMigrationContractTest test
```

Result: expected `BUILD FAILURE`; 2 tests ran, with 1 failure and 1 error.

- `migrationDefinesLeadFactsDictionariesPermissionsAndEvents` errored with `NoSuchFileException` for absent `V0_20_48__lead_todo_flow.sql`.
- `javaAndMapperContractsExposeVersionGuardedFocusedWrites` failed at the absent lead event/mapper contract.

No production or migration file had been changed before this RED run.

## GREEN evidence

Focused migration and mapper contracts:

```powershell
mvn -pl law-todo,ruoyi-system -am "-Dtest=LeadTodoFlowMigrationContractTest,TodoMapperXmlContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; `law-todo` ran 20 tests (2 lead-flow contracts and 18 existing Todo mapper contracts), 0 failures/errors/skips. `law-business` and `ruoyi-system` compiled in the same six-module reactor.

Lead/business regressions:

```powershell
mvn -pl ruoyi-system -am "-Dtest=BusinessEventCommandTest,Lead*Test" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`.

- `law-business`: 4 tests passed.
- `law-todo`: 3 tests passed.
- `ruoyi-system`: 17 tests passed.
- Total: 24 tests, 0 failures/errors/skips.

`git diff --check` passed.

Flyway terminal-version gate, using a fresh schema imported from the same exact eleven-file v0.15 baseline:

```powershell
mvn -pl ruoyi-admin -am "-Dtest=FlywayMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Before changing the gate, Flyway migrated successfully through `V0_20_48` and the test failed exactly at the terminal assertion: expected `0.20.45`, actual `0.20.48`. After updating the terminal version, the next Task 5 invariant showed that the seven net-new catalog rows increased the active `READY` event-contract total from 36 to 43, so that assertion was synchronized as well.

A second fresh-baseline run then finished `BUILD SUCCESS`: 1 test passed, 0 failures/errors/skips, with terminal version `0.20.48`.

## Real MySQL 8 / Flyway evidence

Used the existing disposable `mysql:8.0` container (`8.0.46`) and a new isolated schema with `utf8mb4_unicode_ci`. Imported the exact eleven CI v0.15 baseline files in workflow order, byte-for-byte through `docker cp` and MySQL `source`.

A temporary, uncommitted smoke test used the repository Flyway runtime with `baselineOnMigrate=true`, `baselineVersion=0.15.0`, and `classpath:db/migration`.

Final result:

- Flyway validated 60 migration files.
- The exact baseline migrated successfully through `V0_20_48`.
- Flyway reported 60 applied migrations and terminal version `0.20.48`.
- A second `migrate()` executed 0 migrations and reported the schema up to date; no repair or destructive workaround was used.
- Smoke invariants passed:
  - all 18 new `biz_lead` columns;
  - 5 existing rows preserved and backfilled (`3 ACTIVE`, `1 PUBLIC_POOL`, `1 CONVERTED`);
  - all 9 Task 5 business tables;
  - 30 controlled dictionary values across 8 dictionary types;
  - all 12 permissions;
  - all 10 new lead event contracts in `READY`/`ACTIVE`;
  - exactly 5 canonical `LEAD_ASSIGNED` schema properties.

The temporary MySQL smoke-test source was removed after the evidence run and is not staged.

## Schema, backfill, and index details

### `biz_lead`

Added:

- tag confirmation: `tag_confirm_status`, `tag_confirm_time`, `tag_confirm_by`;
- first contact: `first_contact_status`, `first_contact_time`, `first_contact_result`, `city`, `visited`;
- invalid review: `invalid_reason_code`, `invalid_source_node`, `invalid_review_status`;
- retry: `retry_stage`, `retry_attempt_count`, `next_retry_time`;
- location: `disposition`, `dead_pool_time`, `dead_pool_reason`;
- concurrency: `row_version`.

The migration first adds nullable `disposition`, then backfills with deterministic precedence:

1. `customer_id is not null` or lifecycle status `3` → `CONVERTED`;
2. legacy `pool_status='1'` → `PUBLIC_POOL`;
3. otherwise → `ACTIVE`.

Only after the backfill is `disposition` made `NOT NULL`. `row_version` and retry attempts use safe zero defaults. Existing create/assign/move-to-pool/claim/convert writes now keep `disposition` synchronized with legacy `status`/`pool_status`, and all existing lead mutations increment `row_version`.

New query indexes cover disposition/Dead-Pool, first-contact owner queues, invalid-review queues, and retry due scans.

### New business tables

- `biz_lead_call_record`
- `biz_lead_invalid_review`
- `biz_lead_retry_record`
- `biz_lead_quality_record`
- `biz_lead_dead_pool_log`
- `biz_business_tag`
- `biz_business_tag_rel`
- `biz_lead_assignment_policy`
- `biz_lead_assignment_policy_candidate`

Important idempotency and identity constraints include:

- unique call-record, review, retry, quality, and Dead-Pool idempotency keys;
- unique retry identity `(plan_id, window_code, attempt_no)`;
- unique tag relation `(business_type, business_id, tag_id)`;
- unique policy code and scope `(sales_dept_id, source_code, business_type)`;
- unique candidate membership and candidate order inside a policy.

No business-table foreign keys were added to legacy lead rows, preserving existing hard-purge behavior and avoiding coupling business facts to Todo runtime ownership.

## Dictionaries and permissions

Added the exact controlled uppercase values:

- `law_first_contact_result`: `VALID`, `SUSPECT_INVALID`, `UNREACHABLE`;
- `law_lead_tag_confirm_status`: `PENDING`, `CONFIRMED`, `CORRECTED`;
- `law_lead_invalid_reason`: `NO_DEMAND`, `DENY_SUBMISSION`, `COMPETITOR_INTERFERENCE`, `OTHER`;
- `law_lead_invalid_review_result`: `TRUE_INVALID`, `MISJUDGED_VALID`;
- `law_retry_stage`: `T0`, `T1_AM`, `T1_NOON`, `T1_PM`, `T2_AM`, `T2_NOON`, `T2_PM`, `EXHAUSTED`;
- `law_retry_result`: `CONNECTED`, `NEXT_WINDOW`, `EXHAUSTED`;
- `law_call_channel`: `MANUAL`, `APP`, `OUTBOUND_SYSTEM`;
- `law_lead_disposition`: `ACTIVE`, `PUBLIC_POOL`, `DEAD_POOL`, `CONVERTED`.

All labels and remarks are readable UTF-8 Chinese.

Added all 12 minimum permissions from design section 9 and attached them to the closest existing lead pages without altering legacy grants.

## Event contracts

Added Java enum and catalog contracts for:

- `LEAD_TAG_CONFIRMED`
- `LEAD_FIRST_CONTACT_VALID`
- `LEAD_SUSPECT_INVALID_MARKED`
- `LEAD_FIRST_CONTACT_UNREACHABLE`
- `LEAD_INVALID_REVIEW_CONFIRMED`
- `LEAD_INVALID_REVIEW_MISJUDGED`
- `LEAD_RETRY_WINDOW_DUE`
- `LEAD_RETRY_CONNECTED`
- `LEAD_RETRY_EXHAUSTED`
- `LEAD_MOVED_TO_DEAD_POOL`

Existing catalog rows are corrected only through `V0_20_48` updates. In particular, `LEAD_ASSIGNED@1` now has exactly the canonical required payload fields produced by Task 1:

- `schemaVersion`
- `assignmentId`
- `ownerId`
- `ownerDeptId`
- `operatorId`

All lead schemas include typed properties, required fields, samples, readable names/descriptions, `source_module='lead'`, and `schema_status='READY'`.

## Domain and mapper contracts

`BizLead` and `BizLeadMapper.xml` map every new `biz_lead` field. New focused mapper writes are:

- `confirmLeadTags`
- `completeFirstContact`
- `markInvalidReviewed`
- `advanceRetryStage`
- `moveToDeadPool`
- `restoreFromDeadPool`

Each focused write increments `row_version` and guards on the supplied current `row_version` plus the relevant lifecycle, review, retry, or disposition state. No generic `Map`-based lead-flow update API was introduced.

## Changed files

- `.superpowers/sdd/2026-07-25-lead-todo-flow-closure/task-5-report.md`
- `ruoyi-admin/src/main/resources/db/migration/V0_20_48__lead_todo_flow.sql`
- `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`
- `law-todo/src/test/java/com/law/todo/integration/LeadTodoFlowMigrationContractTest.java`
- `law-business/src/main/java/com/law/business/event/BusinessEventType.java`
- `law-business/src/main/java/com/law/business/security/LeadPermissions.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/domain/BizLead.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java`
- `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`

User-owned `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, `.playwright-cli/`, `.runtime-logs/`, `output/`, and `test-results/` were not staged or modified by Task 5.

## Self-review

- Confirmed no older migration was edited.
- Confirmed the Todo-owned `todo_round_robin_cursor`, `sys_user_availability`, and `sys_user_delegation` tables are not recreated.
- Corrected a draft `LEAD_ASSIGNED` union-type property because the current runtime validator accepts scalar JSON Schema `type` values only.
- Added legacy public-pool/disposition synchronization after identifying that leaving existing writes unchanged would create divergent business facts after migration.
- Confirmed all focused updates contain both a state guard and `row_version` guard.
- Confirmed UTF-8 Chinese source text contains no replacement characters.
- Synchronized the shared Flyway terminal-version and event-catalog total assertions with `V0_20_48`, then passed the gate from a fresh exact baseline.
- Confirmed only Task 5 files are staged.

## Concerns

- Task 6 must add the transactional business services and fact-table insert/read mappers; Task 5 intentionally provides only stable facts and contracts.

## Review fix round 1

### Result

All four review findings are resolved without changing `V0_20_48__lead_todo_flow.sql` or weakening the canonical `LEAD_ASSIGNED` schema.

1. Permanent purge now deletes every lead-owned Task 5 fact before deleting `biz_lead`.
2. Every pre-existing lead state write that increments `row_version` now accepts and checks the loaded expected row version.
3. Dead-Pool entry is restricted to confirmed-invalid active leads and no longer overwrites retry stage.
4. Assignment validates the target user and department before assignment, log, or outbox mutation.

The assignment-policy and assignment-policy-candidate tables are not purged: neither contains a `lead_id`; they are reusable department/source routing configuration rather than lead-owned facts.

### Review RED evidence

Lead-owned purge:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadCommandServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected `BUILD FAILURE` at test compilation with six absent typed mapper methods:
`purgeLeadCallRecords`, `purgeLeadInvalidReviews`, `purgeLeadRetryRecords`,
`purgeLeadQualityRecords`, `purgeLeadDeadPoolLogs`, and `purgeLeadTagRelations`.

Legacy optimistic locking:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadAssignmentServiceTest,LeadPoolServiceTest,LeadConversionServiceTest,LeadFollowupServiceTest,LeadFirstContactHandlerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected `BUILD FAILURE` with 19 test-compilation errors because the six legacy mapper writes did not yet accept expected row version:
`assignLead`, `moveToPool`, `claimLead`, `bindCustomerConditionally`,
`touchLeadFollowTime`, and `touchLeadFollowTimeConditionally`.

Dead-Pool transition:

```powershell
mvn -pl law-todo -Dtest=LeadTodoFlowMigrationContractTest test
```

Expected `BUILD FAILURE`; 3 tests ran and the new transition test failed because `moveToDeadPool` accepted caller-supplied disposition, lacked the confirmed-invalid guard, and wrote `retry_stage='EXHAUSTED'`.

Assignment target validation:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadAssignmentServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected `BUILD FAILURE`; 7 tests ran with 4 failures. Missing, disabled, deleted, and department-less targets reached the old mutation path instead of producing the stable precondition error before writes.

### Review GREEN evidence

The individual GREEN runs passed:

- purge: 3/3 `LeadCommandServiceTest`;
- optimistic-lock propagation: 13/13 across assignment, pool, conversion, followup, first-contact handler, and conversion flow;
- Dead-Pool transition contract: 3/3;
- assignment target validation plus conversion flow: 8/8;
- strengthened exact migration/mapper contract: 6/6.

Final focused gate:

```powershell
mvn -pl law-todo,ruoyi-system -am "-Dtest=LeadTodoFlowMigrationContractTest,TodoMapperXmlContractTest,LeadCommandServiceTest,LeadAssignmentServiceTest,LeadPoolServiceTest,LeadConversionServiceTest,LeadFollowupServiceTest,LeadFirstContactHandlerTest,LeadCustomerConversionFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 44 tests passed:

- `law-todo`: 24;
- `ruoyi-system`: 20;
- 0 failures/errors/skips.

Lead/business regression gate:

```powershell
mvn -pl ruoyi-system -am "-Dtest=BusinessEventCommandTest,Lead*Test" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 37 tests passed:

- `law-business`: 4;
- `law-todo`: 7;
- `ruoyi-system`: 26;
- 0 failures/errors/skips.

`git diff --check` passed. The migration file did not change in this review round, so the already-green fresh-baseline Flyway/MySQL evidence above remains the applicable migration gate; it was not rerun for Java/mapper-only fixes.

### Review implementation details

Permanent purge is one transaction and deletes, in order:

1. `biz_lead_call_record`;
2. `biz_lead_invalid_review`;
3. `biz_lead_retry_record`;
4. `biz_lead_quality_record`;
5. `biz_lead_dead_pool_log`;
6. `biz_business_tag_rel` where `business_type='LEAD'`;
7. legacy `biz_lead_followup`;
8. legacy `biz_lead_assignment_log`;
9. `biz_lead`.

Each dependent delete retains the recycle-bin guard (`biz_lead.del_flag='2'`).

The six legacy lead state writes now require `expectedRowVersion`, increment `row_version`, and return zero for a stale caller. The services propagate the version from the loaded `BizLead`; stale assignment, pool, claim, conversion, and follow-time paths fail before publishing a duplicate event. The Todo first-contact completion handler now loads the lead version and raises `LEAD_CONCURRENT_MODIFICATION` when its guarded touch loses the race.

`moveToDeadPool` now requires both `disposition='ACTIVE'` and `invalid_review_status='CONFIRMED'`. It clears the pending retry time but preserves `retry_stage`; retry exhaustion remains a public-pool concern.

`LeadAssignmentService` now loads the target `SysUser` before mutation and requires:

- the user exists;
- `status='0'`;
- `del_flag='0'`;
- non-null `deptId`.

Failure returns stable `PRECONDITION_FAILED` and invokes neither assignment mapper, assignment log, nor event publisher. A valid assignment publishes the required non-null `ownerDeptId` from that validated user.

The migration contract now checks exact dictionary type/value pairs, exact properties/required/sample key shapes for all ten Task 5 events plus canonical `LEAD_ASSIGNED`, all lead-owned purge statements and recycle guards, and all six legacy expected-row-version SQL guards.

### Review changed files

- `.superpowers/sdd/2026-07-25-lead-todo-flow-closure/task-5-report.md`
- `law-todo/src/test/java/com/law/todo/integration/LeadTodoFlowMigrationContractTest.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java`
- `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadFirstContactHandler.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAssignmentService.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadCommandService.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadConversionService.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadFollowupService.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadPoolService.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadCustomerConversionFlowTest.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadFirstContactHandlerTest.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerCommandServiceTest.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadAssignmentServiceTest.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadCommandServiceTest.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadConversionServiceTest.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadFollowupServiceTest.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadPoolServiceTest.java`

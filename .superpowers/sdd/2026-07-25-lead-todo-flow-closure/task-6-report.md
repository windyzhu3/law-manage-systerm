# Task 6 — Typed lead commands and business services

## Status

Complete. Task 6 adds typed lead commands, fact-table domain/mappers, transactional branch services, stable fact-derived event keys, optimistic state transitions, schedule-owned retry progression, and Dead-Pool/public-pool isolation. Todo completion handlers and published TD-001 through TD-004 routing remain Task 7/8 work.

## Strict TDD evidence

### First contact and call facts

RED:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadFirstContactServiceTest,LeadCallRecordServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected `BUILD FAILURE` at test compilation with 32 errors because the typed commands, services, fact mapper, and expanded focused lead write did not exist.

GREEN after implementation: the same command passed all first-contact/call tests.

### Invalid review, Dead-Pool, and pool isolation

RED:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadInvalidReviewServiceTest,LeadDeadPoolServiceTest,LeadPoolServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected `BUILD FAILURE` at test compilation with 10 missing-service/API errors.

GREEN after implementation: 7 tests passed, covering confirmed invalid, misjudged valid, system default audit, Dead-Pool cancellation/logging, public-pool behavior, and Dead-Pool claim denial.

### Retry, tag confirmation, and assignment policy

RED:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadRetryServiceTest,LeadTagConfirmationServiceTest,LeadAssignmentPolicyServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected `BUILD FAILURE` at test compilation with 14 errors because the services/system pool entry point were absent and the schedule completion contract did not return the engine-owned next window.

GREEN after implementation: 6 tests passed before final hardening.

## Final GREEN evidence

Focused Task 6 gate after the final replay and schedule-ownership hardening:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadTagConfirmationServiceTest,LeadCallRecordServiceTest,LeadFirstContactServiceTest,LeadInvalidReviewServiceTest,LeadRetryServiceTest,LeadDeadPoolServiceTest,LeadAssignmentPolicyServiceTest,LeadPoolServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 19 tests passed, 0 failures/errors/skips.

Schedule/mapper plus service gate:

```powershell
mvn -pl ruoyi-system -am "-Dtest=TodoScheduleServiceTest,TodoMapperXmlContractTest,LeadTagConfirmationServiceTest,LeadCallRecordServiceTest,LeadFirstContactServiceTest,LeadInvalidReviewServiceTest,LeadRetryServiceTest,LeadDeadPoolServiceTest,LeadAssignmentPolicyServiceTest,LeadPoolServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 28 `law-todo` tests and 18 `ruoyi-system` tests passed before the final additional replay test. The final focused gate above covers the last service changes.

Broad lead regression:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadTodoFlowMigrationContractTest,TodoScheduleServiceTest,TodoMapperXmlContractTest,Lead*Test,BusinessEventCommandTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 82 tests passed before the final additional replay test:

- `law-business`: 4;
- `law-todo`: 36;
- `ruoyi-system`: 42;
- 0 failures/errors/skips.

`git diff --check` passed.

## Real MySQL 8 evidence

Used the existing disposable MySQL 8.0.46 container on port 3307 and an isolated `task6_lead_services` schema. The schema was loaded from the exact eleven-file v0.15 baseline used by CI.

Flyway gate:

```powershell
mvn -pl ruoyi-admin -am "-Dtest=FlywayMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; the exact baseline migrated through `V0_20_48`; 1 test passed.

Real mapper transaction gate:

```powershell
mvn -pl ruoyi-admin -am "-Dtest=LeadFlowMapperExternalMysqlIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 1 test passed. The test loads the real `BizLeadMapper`, `LeadFlowMapper`, and `BusinessEventMapper` XML against MySQL and proves:

- call fact + version-guarded lead transition + Outbox event all disappear on rollback;
- the same three records commit together on success.

## Business behavior

### Call and first contact

- Call facts require a controlled channel and either provider external ID or stable business occurrence key.
- The key is `LEAD_CALL:{channel}:{externalId|occurrenceKey}`. A matching duplicate returns the persisted fact; a conflicting duplicate returns `LEAD_CALL_RECORD_DUPLICATE`.
- Recording evidence is accepted only when file-center authorization succeeds and the file is related to the same lead.
- TD-001 always writes a real `biz_lead_followup`; the valid event key is exactly `LEAD_FIRST_CONTACT_VALID:{leadId}:{followupId}`.
- `VALID` requires and atomically persists contact name, city, legal demand, and visited.
- `SUSPECT_INVALID` validates the four-value controlled reason, inserts one invalid-review fact, resolves the owner supervisor, and publishes one `LEAD_SUSPECT_INVALID_MARKED` event keyed by `reviewId`.
- `UNREACHABLE` creates one retry schedule plan and publishes one `LEAD_FIRST_CONTACT_UNREACHABLE` event keyed by `planId`.
- Exactly one branch event is published for a successful first-contact completion.

### Invalid review and Dead-Pool

- Manual review uses the current actor; automatic default review is persisted as user `0/system` with `system_default='Y'`.
- `TRUE_INVALID` completes the review, moves the lead to `DEAD_POOL`, clears owner/public-pool state, cancels an active retry plan, writes a Dead-Pool log, and publishes independent review-confirmed and Dead-Pool events.
- `MISJUDGED_VALID` preserves the original review fact, writes idempotent quality evidence, version-guard reopens first contact, and publishes a new occurrence key containing `reviewId` and `qualityRecordId`.
- Repeating a completed review returns the existing quality/Dead-Pool fact instead of reapplying the transition.
- Public-pool reads and claims require `disposition='PUBLIC_POOL'`; a Dead-Pool lead cannot pass through the ordinary public-pool claim path.

### Retry and assignment policy

- `LEAD_RETRY_OCCURRENCE:{occurrenceId}` is the retry completion idempotency boundary; a repeated matching completion returns the existing outcome before branch mutations.
- The schedule engine derives the next window code and start time. Caller-supplied next-stage/time fields cannot control the persisted retry fact or lead transition.
- `NEXT_WINDOW` keeps the plan active and advances the lead using the derived stage/time.
- `CONNECTED` lets schedule completion terminate the locked plan and future Todo/occurrence graph, writes the valid contact fields, and publishes `LEAD_RETRY_CONNECTED:{planId}:{retryRecordId}`.
- `EXHAUSTED` terminates the plan, advances the lead to `EXHAUSTED`, and invokes the dedicated fixed-system `LeadPoolService` entry point so assignment log and public-pool event behavior remain consistent.
- Assignment-policy lookup prefers an exact department/source policy, falls back to `*`, and returns active candidates in stable configured order.

## Event keys

- `LEAD_TAG_CONFIRMED:{leadId}:{tagRelationId}`
- `LEAD_FIRST_CONTACT_VALID:{leadId}:{followupId}`
- `LEAD_SUSPECT_INVALID_MARKED:{leadId}:{reviewId}`
- `LEAD_FIRST_CONTACT_UNREACHABLE:{leadId}:{planId}`
- `LEAD_INVALID_REVIEW_CONFIRMED:{leadId}:{reviewId}`
- `LEAD_INVALID_REVIEW_MISJUDGED:{leadId}:{reviewId}:{qualityRecordId}`
- `LEAD_MOVED_TO_DEAD_POOL:{leadId}:{deadPoolLogId}`
- `LEAD_RETRY_CONNECTED:{planId}:{retryRecordId}`
- `LEAD_RETRY_EXHAUSTED:{planId}`
- existing public-pool event key `LEAD_MOVED_TO_POOL:{leadId}:{assignmentLogId}`

## Changed files

- `.superpowers/sdd/2026-07-25-lead-todo-flow-closure/task-6-report.md`
- five typed DTOs under `law-business/src/main/java/com/law/business/lead/dto/`
- schedule completion contract/query/test in `law-todo`
- `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadFlowMapperExternalMysqlIT.java`
- six typed fact domains, `LeadFlowMapper.java`, and `LeadFlowMapper.xml`
- focused `BizLeadMapper` Java/XML extensions
- seven Task 6 services plus scoped `LeadPoolService` and `LeadAccessPolicy` hardening
- eight matching lead service test classes

User-owned `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, `.playwright-cli/`, `.runtime-logs/`, `output/`, and `test-results/` are not staged.

## Self-review and concerns

- All Task 6 business mutations are transactional and conditional lead updates use expected `row_version`.
- Generated database identities, not caller IDs or random values, form branch event keys.
- Retry schedule ownership is enforced in both the lead transition and retry fact.
- `insert ignore` replay paths always reload and validate the persisted fact.
- The real MySQL test proves the shared mapper transaction boundary; Task 7 still owns end-to-end Todo handler/action-log rollback tests.
- Task 7 must wire only thin typed completion handlers. Task 8 must publish the executable TD-001 through TD-004 routes; until then these services do not by themselves create the downstream Todos.
- The existing shared Outbox publisher derives `create_by` from the security context. System-default review facts and payloads are explicitly audited as `0/system`; Task 7 should preserve a service security actor when invoking automatic completion so the shared Outbox audit column is also available and consistent.

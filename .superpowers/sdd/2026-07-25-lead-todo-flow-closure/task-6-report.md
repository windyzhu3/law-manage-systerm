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
- The original shared Outbox publisher derived `create_by` from the security context; this concern is resolved in independent review fix round 1 by explicit trusted-actor publication.

## Independent review fix round 1

Status: complete. All findings in `task-6-review.md` (C1, C2, I1, I2, I3, I4, and M1) are addressed.

### RED evidence

The first review-fix test command was:

```powershell
mvn -pl ruoyi-system -am "-Dtest=TodoScheduleServiceTest,LeadRetryServiceTest,LeadInvalidReviewServiceTest,OutboxBusinessEventPublisherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

It failed at `law-todo` test compilation with seven expected missing-contract errors, including the locked schedule-context mapper API, `ScheduleOccurrenceContext`, and configured `ScheduleWindowRule`.

The outbound-boundary test was also introduced before its port implementation. Its first run stopped at compilation while the new call-record API and actor boundary were still absent.

### Fixes

- C1: added a joined `FOR UPDATE` occurrence/window/plan query and authoritative schedule context containing lead business identity, linked Todo, plan/window/occurrence identity, timezone, template/rule, maximum attempts, and statuses. Retry validates that context before call facts or graph mutations, derives all persisted schedule fields from it, and uses its timezone. Context-based schedule completion re-locks and rejects a changed/forged context.
- C2: removed `systemDefault` from the public review DTO. Human review always uses the current actor, data scope, permission, stored source Todo, and assigned-reviewer/admin checks. Automatic review is a separate fixed-TRUE_INVALID entry point and accepts only the exact Todo service capability object.
- I1: added explicit actor publication. Task 6 services pass their already-authorized actor; the automatic path passes `0/system`, so Outbox no longer depends on an ambient web principal.
- I2: persisted the resolved reviewer, enforced assigned reviewer/admin plus `lead:invalid-review:handle`, enforced owner/admin plus `lead:call-record:add` for direct call records, and required embedded call records to match their trusted parent Todo.
- I3: added a vendor-neutral outbound-call port and verified callback command/result. Ordinary commands are MANUAL-only. APP/OUTBOUND_SYSTEM records require adapter verification, provider-qualified idempotency, a canonical SHA-256 hash, and exact immutable replay comparison. Database timestamp precision is canonicalized before comparison.
- I4: first-contact commands no longer carry retry template, rule, or timezone authority. The service resolves the active department/source policy with wildcard fallback, parses server-side template/rule/timezone/windows, snapshots immutable absolute windows, validates the published TD-003 version, and stores the selected rule version.
- M1: completed review replay now requires its immutable quality or Dead-Pool companion fact and raises stable `LEAD_FLOW_EVIDENCE_MISSING` consistency failure if evidence is absent.

### Final verification

Focused review-fix gate:

```powershell
mvn -pl ruoyi-system -am "-Dtest=TodoScheduleServiceTest,TodoMapperXmlContractTest,LeadTagConfirmationServiceTest,LeadCallRecordServiceTest,LeadFirstContactServiceTest,LeadInvalidReviewServiceTest,LeadRetryServiceTest,LeadDeadPoolServiceTest,LeadAssignmentPolicyServiceTest,LeadPoolServiceTest,OutboxBusinessEventPublisherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 69 tests passed (33 `law-todo`, 36 `ruoyi-system`), 0 failures/errors/skips.

Broad lead regression:

```powershell
mvn -pl ruoyi-system -am "-Dtest=TodoScheduleServiceTest,TodoMapperXmlContractTest,Lead*Test,BusinessEventCommandTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 103 tests passed (4 `law-business`, 40 `law-todo`, 59 `ruoyi-system`), 0 failures/errors/skips.

Real MySQL 8 gate:

```powershell
mvn -pl ruoyi-admin -am "-Dtest=LeadFlowMapperExternalMysqlIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 3 tests passed. In addition to the original rollback/commit proof, the real mapper/service test proves cross-lead and cross-Todo retry commands leave both lead rows, the plan, the occurrence, and retry facts unchanged. The real Outbox publisher also persists `create_by='system'` with no authenticated principal.

`git diff --check` passes. User-owned Task 7 report, UI proxy edit, Playwright/runtime output, and test-results remain unstaged.

## Independent review fix round 2

Status: complete. R1, R2, R3 and the missing durable CI gate from
`task-6-rereview.md` are addressed.

### RED evidence

The round-two contract tests were introduced before the production mapper API:

```powershell
mvn -pl ruoyi-system -am "-Dtest=TodoScheduleServiceTest,TodoScheduleMigrationContractTest,LeadRetryServiceTest,LeadFirstContactServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The expected RED stopped at `law-todo` test compilation with two missing
`selectScheduleOccurrenceIdentity(long)` errors. Later R2/R3 tests remained behind that compile
fence until the plan-first API was implemented.

### R1: one production lock order

- Completion now reads occurrence identity without a lock, locks the owning plan first, and only
  then reloads the joined occurrence/window/plan context `FOR UPDATE`. It rejects any identity or
  plan-status change across the fence.
- Mapper contract tests prove the identity lookup is non-locking and the authoritative context is
  locking. Service tests prove the exact identity -> plan -> joined-context call order.
- The real-MySQL race no longer manually restates mapper calls. Its materializer invokes
  `TodoRoutingService.createScheduledNext`, its completion invokes
  `TodoScheduleService.completeOccurrence`, and a latch only pauses the real materializer mapper
  invocation immediately after its production plan lock. The test observes the completion session
  waiting on `todo_schedule_plan`, releases the materializer, and proves both production operations
  commit without MySQL error 1213.

### R2: configured call-attempt semantics

- A retry attempt is now one immutable `biz_lead_call_record` bound to the authoritative lead and
  TD-003 Todo. The actual attempt number is the real count for that lead/Todo, not
  `occurrence_no`.
- The idempotency key is `LEAD_RETRY_ATTEMPT:{occurrenceId}:{callRecordId}`. Exact replay requires
  the same immutable call fact and compatible server-owned outcome; a conflicting terminal outcome
  is rejected without schedule or lead mutation.
- Unconnected attempts below `max_attempts` persist `CONTINUE_CURRENT_WINDOW`, update the real
  attempt count, and retain the same Todo/window. At the configured limit, the schedule service,
  not caller stage/time fields, derives `NEXT_WINDOW` when a later window exists and `EXHAUSTED`
  otherwise. A client cannot force early exhaustion.
- Tests cover attempts 1, 2 and 3, early exhaustion, configured maximum 2, terminal next/exhausted
  derivation, connected completion, matching replay, and conflicting replay.

### R3: immutable policy audit snapshot

- Flyway `V0_20_49__todo_schedule_policy_snapshot.sql` adds
  `assignment_policy_id` and `assignment_policy_version` to `todo_schedule_plan`, and expands the
  immutable retry-result constraint for the internal continuation outcome.
- First-contact plan creation passes the resolved policy ID and row version through the schedule
  command, plan insert, plan lock, authoritative context and completion result.
- The real-MySQL lead-flow test creates an exact-source and wildcard policy with different row
  versions, rules and attempt limits. It resolves both through `LeadAssignmentPolicyService`,
  creates plans through `TodoScheduleService`, and queries the real plan/window rows to prove the
  selected policy ID/version and configured values were snapshotted exactly.

### CI closure

- `LeadFlowMapperExternalMysqlIT` is explicitly present in the migration MySQL Maven list.
- The external Surefire report assertion requires its report and rejects zero, skipped, failed or
  errored evidence.
- A repository contract test verifies both workflow execution and the non-skip assertion; the
  report gate's negative contract also passes.

### Final verification

Focused schedule and Task 6 service gate:

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am "-Dtest=TodoScheduleMigrationContractTest,TodoScheduleServiceTest,TodoMapperXmlContractTest,LeadTagConfirmationServiceTest,LeadCallRecordServiceTest,LeadFirstContactServiceTest,LeadInvalidReviewServiceTest,LeadRetryServiceTest,LeadDeadPoolServiceTest,LeadAssignmentPolicyServiceTest,LeadPoolServiceTest,OutboxBusinessEventPublisherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 77 tests passed (37 `law-todo`, 40 `ruoyi-system`), 0
failures/errors/skips. The final exact-replay addition was also rerun independently:
`LeadRetryServiceTest` passed 11/11.

Broad lead regression:

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am "-Dtest=TodoScheduleMigrationContractTest,TodoScheduleServiceTest,TodoMapperXmlContractTest,Lead*Test,BusinessEventCommandTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 112 tests passed (4 `law-business`, 44 `law-todo`, 64
`ruoyi-system`), 0 failures/errors/skips.

An isolated MySQL 8 schema was initialized from the exact eleven-file CI v0.15 baseline.
`FlywayMigrationTest` migrated it through `0.20.49` and passed its real
`information_schema` assertion. The final production-path MySQL/gate command:

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-admin -am "-Dtest=TodoScheduleLockOrderExternalMysqlIT,LeadFlowMapperExternalMysqlIT,LeadFlowMysqlGateContractTest,TodoScheduleLockOrderMysqlGateContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 7 tests passed (lead flow 4, production lock race 1, CI contracts 2),
0 failures/errors/skips. `node ruoyi-ui/scripts/check-external-db-reports-contract.js` also passed.

`git diff --check` passes. The user-owned Task 7 report, UI proxy edit,
`.playwright-cli/`, `.runtime-logs/`, `output/`, and `test-results/` remain unstaged.

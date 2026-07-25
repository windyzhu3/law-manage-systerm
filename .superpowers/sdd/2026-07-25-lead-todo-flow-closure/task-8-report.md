# Task 8 report — publish TD-001 through TD-004

## Stage and commit boundary

- Plan: `docs/superpowers/plans/2026-07-25-lead-todo-flow-closure.md`, Task 8.
- Baseline: `b9c429d6 fix(lead): honor authoritative retry outcomes`.
- Implementation commit: the commit containing this report, with subject
  `feat(todo): publish executable lead templates`.
- Migration version is `V0_20_51`, because the planned `V0_20_49` and the
  subsequent `V0_20_50` already exist and are immutable.

## RED evidence

The published-template contract was written before the resources and migration:

```text
mvn --batch-mode --no-transfer-progress -pl law-todo \
  '-Dtest=LeadTodoPublishedTemplateContractTest,V02PrdDefinitionManifestTest,TodoRoutingEngineTest' test
```

Expected RED was observed: all five new contract tests failed because TD-001
through TD-004 were still production `BLOCKED`, governed dictionaries and
controlled defaults were absent, routes were not executable, and the forward
publication migration did not exist.

## Implementation

- Promoted TD-001 through TD-004 to `READY` with present completion-handler
  capabilities and no unresolved business dependency.
- Added governed dynamic-form dictionaries, conditional visibility,
  validator references, TD-003's Asia/Shanghai T0/T+1/T+2 schedule, and
  corrected 80/100/150 SLA behavior. Only SLA 150 performs escalation.
- Added executable route graphs using the Task 7 authoritative result fields:
  `contactResult`, `reviewResult`, and server-owned `result`.
- Owner resolution is canonical at every creation boundary. Graph-created tasks
  read the immutable target version definition, execute the same typed owner
  resolver as trigger-created and schedule-created tasks, and fail closed with
  `TODO_OWNER_UNRESOLVED` before inserting a task when no owner can be resolved.
- TD-001 through TD-004 use stable business authority. Their rules explicitly
  disable delegation and fallback while requiring the persisted actor to be
  available. The first-contact and invalid-review handlers expose only
  server-owned owner/reviewer values; client payload cannot redirect the next
  task.
- TD-003 is schedule-only. `NEXT_WINDOW` ends the current routing graph and the
  next T0/T+1/T+2 retry is materialized exclusively by `TodoScheduleService`.
  This prevents both immediate graph clones and graph/schedule duplicates.
- TD-002's due action is a controlled, retryable `COMPLETE_DEFAULT` with
  `reviewResult=TRUE_INVALID`.
- TD-004 remains the approved stage-3 boundary and has no TD-005 or
  `CONTRACT_SIGN` route.
- Added the immutable forward migration in dependency order
  TD-004 → TD-002 → TD-003 → TD-001. Version IDs are allocated from the live
  database sequence and rebound into JSON; no environment-specific ID is
  hardcoded.
- The mutable publication section of `V0_20_51` is one explicit transaction,
  covering version rows, current-version pointers, triggers, catalogue rows, and
  dictionaries. The migration contains a named failure-injection point directly
  after the permanent version inserts.
- Disabled enabled legacy `LEAD_FIRST_CONTACT` and superseded
  `LEAD_ASSIGNED` triggers without deleting their history, then created exactly
  one enabled TD-001 `LEAD_ASSIGNED` trigger.
- Added the TD-004 governed progress dictionary and synchronized the PRD
  catalogue/manifest/matrix with the four published definitions.
- Updated the old PRD blocker test fixture so its production-blocking assertion
  remains isolated from the newly executable TD-002 numeric route.
- Added an exact byte fixture and normalized SHA-256 contract for immutable
  `V0_20_50`; the expected hash is
  `a6285b773d29195e7d9f34128882e169111a2b4421c48509c557ff5293d34820`.

## Verification

Focused template, assignment, handler, and runtime routing:

```text
mvn --batch-mode --no-transfer-progress -pl law-todo \
  '-Dtest=LeadTodoPublishedTemplateContractTest,V02PrdDefinitionManifestTest,TodoRoutingEngineTest,TodoTemplateAndRoutingTest,CompositeOwnerResolverTest,TodoScheduleMigrationContractTest' test
PASS — 49 focused law-todo tests, 0 failures, 0 errors

mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am \
  '-Dtest=LeadFirstContactHandlerTest,LeadInvalidReviewTodoHandlerTest,LeadRetryTodoCommandFlowTest,LeadRetryTodoHandlerTest,LeadFirstContactServiceTest' \
  '-Dsurefire.failIfNoSpecifiedTests=false' test
PASS — 28 focused system tests, 0 failures, 0 errors
```

Broad Todo and lead-business regression:

```text
mvn --batch-mode --no-transfer-progress -pl law-todo test
PASS — 745 tests, 0 failures, 0 errors, 2 conditional skips

mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am test
PASS — ruoyi-system 237 tests, 0 failures, 0 errors
```

Real MySQL 8.0 migration from the repository's v0.15 baseline:

```text
mvn --batch-mode --no-transfer-progress -pl ruoyi-admin -am \
  '-Dtest=FlywayMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
PASS — Flyway applied all migrations through v0.20.51
```

The real MySQL test imported the repository's eleven v0.15 baseline SQL files,
then applied production migrations through `V0_20_50`. It created a temporary
out-of-order probe from the actual `V0_20_51` text by replacing only the named
failure-injection marker with a MySQL `SIGNAL`. After the forced failure it
proved that template current pointers, LEAD triggers, catalogue state/hash,
published-version counts, and dictionary type/data counts were unchanged. It
then applied the unmodified production `V0_20_51` successfully.

The final database invariant check proved four current published LEAD versions,
TD-001 graph targets bound to the live current TD-002 and TD-004 version IDs,
TD-003 schedule metadata bound to its live current version ID, four stable
no-fallback owner rules, exactly one enabled `LEAD_ASSIGNED` trigger, no enabled
`LEAD_FIRST_CONTACT` trigger, present handler catalogue entries, the controlled
TD-002 default, and no `CONTRACT_SIGN` reference.

The production command-flow integration test further proved that attempts one
and two keep their T0 TD-003 task, exhaustion completes the current task without
creating an immediate successor, and a real `TodoScheduleService.materializeDue`
call creates exactly one next-window TD-003 with schedule occurrence key
`SCHEDULE:81:T1_AM:1`.

```text
git diff --check
PASS
```

## Review remediation: production-port proof

- Marked every repository TD-001 through TD-004 route as a compiler fixture
  with `identityBinding=MIGRATION_DYNAMIC`; TD-003 schedule metadata carries
  the same binding. Contract tests reject a production definition that omits
  it.
- Corrected the `visited` field dictionary from the Y/N `sys_yes_no` values to
  the business service's governed `law_yes_no_flag` values (`0`/`1`) in both
  resources and the publication migration.
- Added `LeadTodoProductionPortsExternalMysqlIT`. It discovers the enabled
  `LEAD_ASSIGNED` rule and the current published TD-001 through TD-004 version
  IDs from the migrated database and explicitly rejects fixture IDs 1 through
  4. It uses real MyBatis mappers plus production `TodoEventService`,
  `TodoCommandService`, `TodoRoutingService`, `TodoScheduleService`, owner
  resolution, Task 7 handlers, lead services, file material lookup, dictionary
  lookup, and transactional outbox publishing; it contains no mocked
  `TodoMapper` and no fixed version IDs.
- The external test proves: event-triggered TD-001 ownership; VALID routing to
  live TD-004; SUSPECT routing to live TD-002 with persisted reviewer and
  MISJUDGED routing back to live TD-001; no graph-created TD-003 for
  UNREACHABLE; exactly one occurrence-linked TD-003 from real due
  materialization; retained attempts below the maximum; no immediate TD-003 at
  the maximum/NEXT_WINDOW boundary; and exactly one next-window occurrence
  when it becomes due. It also checks route identity, candidates, primary
  relations, schedule occurrence links, and absence of orphan or duplicate
  tasks.
- Added the production-port test to the MySQL CI suite and to the mandatory
  no-skip Surefire report checker.
- Updated migration E2E assertions made stale by V0_20_51: current migration is
  `0.20.51`, and the phase-two inventory now has 18 published non-legacy-first-
  contact templates and 16 enabled non-legacy-first-contact rules.

Final review gates:

```text
mvn -pl law-todo -Dtest=LeadTodoPublishedTemplateContractTest test
PASS - 8 tests

mvn -pl ruoyi-system -am test
PASS - all reactor modules; ruoyi-system 237 tests

mvn -pl ruoyi-admin -am -Dtest=FlywayMigrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
PASS - fresh v0.15 baseline, failure-injection rollback, V0_20_51 success

mvn -pl ruoyi-admin -am \
  -Dtest=<CI external MySQL suite including LeadTodoProductionPortsExternalMysqlIT> \
  -Dsurefire.failIfNoSpecifiedTests=false test
PASS - 20 tests, 0 failures, 0 errors, 0 skips

node ruoyi-ui/scripts/assert-external-db-reports.js \
  ruoyi-admin/target/surefire-reports
PASS - 12 required external-database reports, no skips or failures
```

## Review remediation: scheduled root route identity

- `TodoRoutingService.createScheduledNext` now derives the initial route node
  from the published definition graph and rejects a scheduled version without
  an executable start node.
- After the insert returns the generated `todoId`, the service persists one
  complete initial snapshot atomically through `updateInitialRouteSnapshot`:
  `rootTodoId=todoId`, the definition start node (`td003` for the production
  lead retry template), no branch, occurrence `0`, and route status `ACTIVE`.
  A zero-row update fails closed and rolls back the materialization before
  relations, candidates, SLA records, or occurrence links are written.
- Materialized and concurrently claimed duplicate paths now validate the
  existing root ID, route node, occurrence key, and parsed route token before
  returning it. An incomplete or contradictory snapshot is rejected instead
  of being returned as a usable scheduled task.
- `LeadTodoProductionPortsExternalMysqlIT` proves that both the first T0
  materialization and the next-window T1 materialization persist self-rooted
  TD-003 graph identity. Its completion path reloads the task from MySQL, so
  successor routing is proven to consume the persisted token rather than an
  in-memory value.
- The H2 command-flow fixture and the external lock-order fixture now model the
  same persisted route snapshot contract.

Round-three verification:

```text
mvn -pl law-todo -Dtest=TodoTemplateAndRoutingTest test
PASS - 19 tests, including failed-snapshot rollback and incomplete duplicate rejection

mvn -pl ruoyi-system -am \
  -Dtest=LeadRetryTodoCommandFlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
PASS - 1 end-to-end command-flow test

mvn -pl ruoyi-system -am test
PASS - all reactor modules; ruoyi-system 237 tests

mvn -pl ruoyi-admin -am \
  -Dtest=<CI external MySQL suite including LeadTodoProductionPortsExternalMysqlIT> \
  -Dsurefire.failIfNoSpecifiedTests=false test
PASS - 20 tests, 0 failures, 0 errors, 0 skips

node ruoyi-ui/scripts/assert-external-db-reports.js \
  ruoyi-admin/target/surefire-reports
PASS - 12 required external-database reports, no skips or failures
```

## Review remediation: materialized occurrence replay fence

- A `MATERIALIZED` schedule occurrence is replayable only when its persisted
  `todo_id` is non-null and exactly equals the Todo found by the schedule
  idempotency key. A missing or mismatched link now raises the stable
  `TODO_SCHEDULE_OCCURRENCE_LINK_INVALID` consistency error and performs no
  write.
- Before an exact replay can return, the existing Todo must also match the
  requested template version, business type and business ID, occurrence key,
  self-root identity, route start node, and a parsable active route token.
- Focused tests cover all four required paths: null materialized link,
  mismatched link, malformed route token, and the exact valid replay. Each
  corruption path is fail-closed and the valid replay is read-only.
- The real-MySQL lock-order fixture now carries the same template/business
  identity columns as production, so its materialized replay exercises the
  full identity fence while retaining the plan-before-occurrence concurrency
  proof.

Round-four verification:

```text
mvn -pl law-todo -Dtest=TodoTemplateAndRoutingTest test
PASS - 23 tests, including four materialized replay cases

mvn -pl ruoyi-system -am test
PASS - all reactor modules; ruoyi-system 237 tests

mvn -pl ruoyi-admin -am \
  -Dtest=<CI external MySQL suite including TodoScheduleLockOrderExternalMysqlIT> \
  -Dsurefire.failIfNoSpecifiedTests=false test
PASS - 20 tests, 0 failures, 0 errors, 0 skips

node ruoyi-ui/scripts/assert-external-db-reports.js \
  ruoyi-admin/target/surefire-reports
PASS - 12 required external-database reports, no skips or failures
```

## Self-review

- Existing migrations `V0_20_49` and `V0_20_50` were not modified; V0_20_50 is
  protected by the checked-in byte/hash fixture.
- Published version rows are append-only; the migration contains no update of
  `todo_template_version`.
- No `V0_20_52` was added. Review corrections were made in the still-unapproved
  Task 8 `V0_20_51` migration and the original Task 8 commit is amended in
  place.
- Historical trigger rows are retained and only their enabled state is
  superseded.
- Protected pre-existing worktree changes and runtime outputs were not staged:
  `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`,
  `.playwright-cli/`, `.runtime-logs/`, `output/`, and `test-results/`.

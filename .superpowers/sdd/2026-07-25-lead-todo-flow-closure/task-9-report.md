# Task 9 report: Lead APIs, permissions, and read models

## Outcome

Task 9 is complete. The Lead/Todo HTTP boundary now exposes the exact approved
operations with typed validated requests, server-owned actor/business/status
identity, data-scoped joined read models, optimistic and idempotent Dead-Pool
restore, and versioned assignment-policy administration.

No provider callback was exposed as an ordinary Lead endpoint. Invalid-review
completion enters through `TodoCommandService`; the TD-002 persistent handler
derives Lead and review identities from the persisted Todo provenance.

## Delivered interfaces

| Method | Path | Permission |
|---|---|---|
| POST | `/lead/tag/confirm` | `lead:tag:confirm` |
| GET | `/lead/{leadId}/call-records` | `lead:call-record:view` or `lead:dead-pool:list` |
| POST | `/lead/{leadId}/call-records` | `lead:call-record:add` |
| GET | `/lead/invalid-review/list` | `lead:invalid-review:list` |
| POST | `/lead/invalid-review/{todoId}/complete` | `lead:invalid-review:handle` |
| GET | `/lead/retry/list` | `lead:retry:list` |
| GET | `/lead/{leadId}/retry-timeline` | `lead:retry:list` |
| GET | `/lead/dead-pool/list` | `lead:dead-pool:list` |
| POST | `/lead/dead-pool/{leadId}/restore` | `lead:dead-pool:restore` |
| GET | `/lead/assignment-policy` | `lead:assignment-policy:list` |
| PUT | `/lead/assignment-policy` | `lead:assignment-policy:edit` |

Review, retry, and Dead-Pool queues are paged by the existing RuoYi page
interceptor. Each row is projected in one mapper query with Lead summary, fact,
Todo owner/status/title, due time, SLA state, overdue state, and escalation
state. Timeline operations enforce the existing Lead access policy before the
set query. Queue SQL implements role data-scope rules for all/custom/current
department, department-and-children, and own-record scopes.

Dead-Pool detail and evidence reads additionally accept the dedicated
`lead:dead-pool:list` permission. Permission alone is insufficient: the service
reconstructs data scope from the immutable TRUE_INVALID review submitter and
the ENTER ledger row. The same provenance rule is used for list, detail, call
evidence, completed invalid-review history, restore, and restore replay.

## Command safety

- All new write bodies are typed and use `@Valid`.
- Actor/operator IDs are obtained from `BusinessActorProvider`.
- Path identity supplies Lead or Todo identity; DTOs cannot supply persisted
  Lead, review, Todo, actor, target status, disposition, or row version.
- Manual call entry always fixes `callChannel=MANUAL` on the server.
- Tag confirmation first resolves the persisted tag-relation Lead identity.
- Invalid review completion calls `TodoCommandService.complete`; it does not
  call the business review service using request-supplied Lead/review IDs.
- Dead-Pool restore requires the dedicated permission on every attempt,
  including replay. It locks the Lead/TRUE_INVALID/ENTER provenance first and
  the action ledger second, verifies the same actor and canonical
  Lead/action/reason, performs one row-version transition, writes one
  `RESTORE` ledger row, and publishes one stable `LEAD_MOVED_TO_POOL` Outbox
  event. An exact concurrent second request returns the canonical audit ID.
- Human Todo completion authorizes the persisted owner before action replay.
  A replay must match Todo, human source, action kind, operator, canonical
  opinion, recursively canonicalized fields, and sorted file identities.
  Conflict returns stable `TODO_ACTION_ID_CONFLICT`; trusted automatic and
  exception paths retain their previous contracts.

## Assignment policy

The GET operation returns active/inactive LEAD policy rows and all candidates
with user, availability, delegation, and stable order in one policy query plus
one bulk candidate query.

The PUT operation:

- enforces the edit permission and old/new department scope;
- accepts only a published LEAD `TD-003` template version;
- validates a real timezone and exactly one complete supported time mode for
  every window;
- uses one fail-closed validator for API commands and persisted JSON;
- requires the exact seven-window lifecycle: T0 at order/day 0/0,
  T1_AM/NOON/PM at orders 1/2/3 and day 1, and T2_AM/NOON/PM at orders
  4/5/6 and day 2;
- rejects missing, duplicate, reversed, wrong-day, unsupported, invalid-time,
  non-positive attempt, and non-positive occurrence values;
- requires unique active users in the configured sales department;
- fixes business type and active state on the server;
- conditionally updates by persisted `row_version`;
- replaces candidates in the same transaction and returns the new version.

No new Flyway migration was required. V0.20.48 already supplies the Lead flow
table constraints and queue indexes, V0.17.3 indexes Todo template/status, and
V0.20.47 indexes schedule business/previous/due paths. The Task 9 set queries
use those persisted access paths.

## TDD evidence

The initial Task 9 focused run failed compilation with 19 missing controller,
DTO, service, and mapper contracts. After implementation, the same contract
suite passed.

An architecture regression then detected that `BizLeadServiceImpl` had grown
beyond its 150-line compatibility boundary. Todo API orchestration was moved to
`LeadTodoApiService`; the compatibility facade is now 107 lines and the guard
passes.

## Verification

### Focused contracts and services

```text
mvn --batch-mode --no-transfer-progress -pl ruoyi-admin,ruoyi-system -am \
  -Dtest=LeadTodoControllerContractTest,LeadTodoReadModelTest,LeadAccessPolicyTest,\
LeadDeadPoolServiceTest,LeadAssignmentPolicyServiceTest,LeadTagConfirmationServiceTest,\
LeadCallRecordServiceTest -Dsurefire.failIfNoSpecifiedTests=false test

26 tests passed; 0 failures; 0 errors; 0 skips.
```

### Clean MySQL 8 migration

A clean schema was initialized from the eleven repository v0.15 baseline SQL
files and migrated through the unmodified V0.20.51.

```text
FlywayMigrationTest
1 test passed; 0 failures; 0 errors; 0 skips.
```

### Real MySQL mapper proof

`LeadTodoReadModelExternalMysqlIT` now contains two real-MySQL tests and runs
production mapper XML and services against the fully migrated schema. It
proves:

- joined call/review/retry/Dead-Pool Todo and SLA projections;
- salesperson own visibility;
- supervisor department-and-child visibility;
- unauthorized peer exclusion;
- assignment-policy and candidate bulk reads;
- active same-department candidate selection;
- completed TRUE_INVALID supervisor visibility and peer denial;
- Dead-Pool list -> detail -> call evidence -> restore through the shared
  immutable provenance scope;
- two-connection simultaneous exact restore with one state transition, one
  RESTORE audit row, one Outbox event, and one canonical replay;
- conflicting reason rejection and unauthorized peer replay denial.

```text
2 tests passed; 0 failures; 0 errors; 0 skips.
```

The class is included in the CI external-MySQL list and the no-skip report gate.

### Broad regression

```text
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am test

law-file:     73 tests; 0 failures; 0 errors; 0 skips
law-business: 28 tests; 0 failures; 0 errors; 0 skips
law-todo:    755 tests; 0 failures; 0 errors; 2 environment-conditional skips
ruoyi-system:253 tests; 0 failures; 0 errors; 0 skips
```

The CI-equivalent external database report gate also passed:

```text
Verified 13 external-database Surefire reports with no skips or failures
```

`git diff --check` passed. Protected dirty files and unrelated runtime/UI
artifacts were not staged or modified by Task 9.

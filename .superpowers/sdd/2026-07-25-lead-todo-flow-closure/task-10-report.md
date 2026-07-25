# Task 10 report: Lead operational frontend

## Outcome

Task 10 implementation and both independent-review repair rounds are complete.
The work remains one Task 10 commit on top of base `162f0a8d`; the final commit
is produced by amending the existing Task 10 commit rather than adding repair
commits.

The Lead module now exposes user-facing workbenches for source-tag
confirmation, TD-001 first contact, TD-002 invalid review, retry execution,
Dead-Pool review/restore, and TD-003 rotation/retry policy configuration. The
pages reuse the Todo dynamic form, governed file picker, business Todo summary,
Todo action drawer, and the server-provided allowed-action state.

## Delivered frontend

- Lead list/detail:
  - source-tag confirmation status and single/batch confirmation;
  - first-contact action and TD-001 drawer;
  - Todo/SLA summary, call evidence, and retry timeline;
  - source is read-only after Lead creation;
  - public-pool actions are suppressed for Dead-Pool records.
- First-contact drawer:
  - resolves the active TD-001 Todo for the persisted Lead;
  - renders the published dynamic form and `showWhen` fields;
  - supports governed file material fields;
  - lists provider/manual call evidence without fabricating provider facts;
  - completes only when the Todo explicitly returns `complete` in
    `allowedActions`.
- Invalid-review workbench:
  - paged queue, SLA/owner/status, evidence view, and 24-hour default behavior
    guidance;
  - supports `TRUE_INVALID` and `MISJUDGED_VALID` with canonical
    `reviewOpinion` and authorized evidence.
- Retry workbench:
  - paged queue, current retry window, SLA, call count, and full T0/T+1/T+2
    timeline;
  - delegates Todo state changes to the shared `BusinessTodoDrawer`.
- Dead-Pool:
  - scoped list, detail, call evidence, Todo summary, and audited restore;
  - intentionally has no public-pool claim action.
- Assignment policy:
  - department/source scope, named candidate selection, availability and
    delegation status, stable candidate order, published TD-003 version,
    optimistic version, timezone, and the canonical seven retry windows;
  - no raw owner-ID input.
- Shared UI:
  - consistent loading, error/retry, empty, focus, responsive, and high-density
    operational styles.

## Independent review closure

- C1: the public TD-002 contract, dynamic form, Todo DoD, adapter, and handler
  now use `reviewOpinion`. `reviewComment` is accepted only as a Jackson input
  alias for old clients.
- C2: queue rows expose server-authoritative `allowedActions`. The typed
  invalid-review command advances a TD-002 atomically through claim, start,
  submit, and complete using deterministic child action IDs. Multi-row queue
  authorization facts are loaded by one bulk SQL statement and projected in
  memory, eliminating the rereview's per-row authorization query regression.
- I1: manual call, TD-001 completion, TD-002 completion, and Dead-Pool restore
  allocate an operation ID at draft open, retain it after uncertain failures,
  and rotate it only after success/cancel or a semantic edit.
- I2/I3: Lead source is immutable server-side; assignment policies accept only
  `*` or an active governed source setting.
- I4: optimistic conflicts preserve the draft, reload the latest version, and
  offer explicit server-version or draft-on-latest recovery. The E2E contract
  now performs a deterministic competing write in two browser contexts.
- I5: review evidence uses the authorized file-material read model and exposes
  filename, preview, download, and version actions through access tokens.
- I6: the real-backend Playwright suite now executes and verifies separate
  VALID, manual-call/evidence, both invalid-review outcomes, all retry
  outcomes, Dead-Pool restore, and seven-window concurrent-edit journeys. Its
  deterministic fixture creates nine independent Leads and verifies resulting
  database/read-model state. Todo dynamic fields bind form `prop`, label ID,
  and the actual interactive file/material controls through
  `aria-labelledby`.
- I7: V0.20.52 is schema-only and creates the source-governance audit table.
  V0.20.53 performs all source/tag/relation/audit/navigation DML in one
  transaction. The MySQL failure probe records table existence separately from
  row count, asserts the failed Flyway entry, verifies an exact unchanged
  business snapshot, repairs the isolated failed history, and then proves the
  real V53 retry reaches all final invariants.
- M1/M2: detail first-contact entry now requires a confirmed tag and a
  server-authorized `complete` action; compact actions have 44px hit targets
  and mobile tables scroll horizontally.

## Production bridge completed during Task 10

The Task 9 confirmation command requires a persisted `tagRelationId`, but the
existing Lead create/list path did not create or expose that relation. Task 10
closed the gap:

- new Lead creation materializes the source business tag and relation in the
  same transaction before `LEAD_CREATED`;
- Lead list/detail projects the pending relation identity;
- V0.20.52 creates the audit schema and V0.20.53 atomically backfills source
  tags/relations for pre-existing Leads;
- the migration adds the four operational routes and preserves existing role
  grants;
- assignment-policy viewers may load the governed owner option list.

## TDD evidence

The structural contract was run before implementation and originally failed
with:

```text
missing API confirmLeadTag
```

After implementation:

```text
Lead Todo flow UI contract passed
todo lead dynamic form contract passed
All frontend source files are valid UTF-8.
```

The Lead create service test was also extended first and initially failed
compilation because source-tag mapper contracts did not exist. It now proves
the transaction order Lead -> tag definition -> tag relation -> Outbox event.
The final full-reactor run exposed one older integration fixture that had not
stubbed the now-required source relation insert; that fixture was repaired and
both its focused test and the full reactor pass.

## Verification

### Focused Java contracts

```text
TodoBusinessViewServiceTest:                 5 passed
LeadTodoReadModelTest:                       6 passed
LeadSourceGovernanceMigrationContractTest:   1 passed
LeadCustomerConversionFlowTest:              1 passed
```

The complete `mvn test` reactor then passed all ten modules with zero failures
or errors. Environment-gated integration tests retain their expected skips.

### Frontend

```text
node ruoyi-ui/scripts/check-lead-todo-flow-ui.js       PASS
node ruoyi-ui/scripts/check-todo-lead-dynamic-form.js  PASS
node ruoyi-ui/scripts/check-source-encoding.js         PASS
npm --prefix ruoyi-ui run build:prod                   PASS
npx --prefix ruoyi-ui playwright test e2e/lead-todo-flow.spec.js --list
                                                       10 tests discovered
```

The production build retains only the repository's pre-existing bundle-size
and CSS-order warnings.

### Clean MySQL migration

An isolated MySQL 8.0 database was created from the complete v0.15 baseline.
`FlywayMigrationTest` migrated it through V0.20.53 and passed:

```text
FlywayMigrationTest: 1 passed
current version:     0.20.53
```

The test includes known, blank, unknown, disabled, and deleted historical
source fixtures, a stale second source relation, an injected V53 failure before
commit, exact snapshot comparison, Flyway repair, and a successful rerun. A
real-MySQL read-model integration test also loads three Todo rows and proves
that `TodoMapper.selectAllowedActionFacts` executes exactly once.

### Browser acceptance contract

`lead-todo-flow.spec.js` contains ten real-browser journeys backed by
`lead-todo-e2e-database.js`. The fixture helper was executed against a migrated
MySQL database and proved setup, nine isolated Lead identities, seven retry
windows, state/evidence reads, cleanup, and no data leakage. With
`LEAD_TODO_E2E` disabled the suite skips safely; full browser execution requires
the purpose-created application users and a running backend rather than mock
responses.

### Scope safety

`git diff --cached --check` passed. The Task 7 report, local Vue proxy
configuration, Playwright state, runtime logs, screenshots, output artifacts,
and test-results directory were not staged or committed.

## Round-3 fixture and determinism closure

The second rereview's two Important harness findings and Minor selector/server
finding are now closed:

- The E2E run context is created before any fixture write. Setup runs inside a
  MySQL transaction and validates the exact nine-Lead manifest before commit.
  Cleanup runs from `finally` even when setup or manifest validation fails.
- Fixture ownership is the exact generated `lead_no` set and captured
  Lead/Todo/plan/file IDs. No SQL `LIKE` ownership inference remains. Long
  operator markers are mapped to a compact deterministic run ID so the largest
  fixture number still fits the persisted 32-character Lead-number contract.
- Cleanup covers every fixture-owned Lead, retry/review/dead-pool, schedule,
  Todo child, route, event, policy/candidate and source root. Tests prove a
  partial setup failure is cleaned and two prefix-overlapping run IDs cannot
  delete each other's rows.
- Uploaded evidence is retired through the authenticated production
  `FileObjectService` lifecycle. The lifecycle disables the object, revokes
  relations, deletes every stored version key, retains completed cleanup and
  lifecycle audit rows, and leaves failed deletions retryable. E2E cleanup
  rejects a still-valid download token, waits for terminal cleanup, and checks
  each physical object key is absent; it never directly deletes file-center
  rows.
- The Docker MySQL fallback passes only `-e MYSQL_PWD`; the value exists only
  in the child environment. Runner diagnostics redact it. Dedicated tests
  verify both the generated argument vector and failure output.
- Stable `data-testid` hooks now identify Lead/Todo rows, drawers, policy
  cards/editor, retry-window rows, submit controls and optimistic-conflict
  recovery. The real-backend Playwright profile owns port 4173 with
  `reuseExistingServer: false`.

Round-3 verification:

```text
mvn test
  PASS - all 10 reactor modules, zero failures/errors

FileObjectGovernanceReviewTest
  PASS - 14/14, including successful multi-key retirement and retryable failure

npm --prefix ruoyi-ui run test:lead-e2e-helpers
  PASS - 4/4 (partial failure, overlapping runs, Docker args, redaction)

node ruoyi-ui/scripts/check-lead-todo-flow-ui.js
node ruoyi-ui/scripts/check-todo-lead-dynamic-form.js
node ruoyi-ui/scripts/check-source-encoding.js
  PASS

npm --prefix ruoyi-ui run build:prod
  PASS - only the pre-existing CSS-order and bundle-size warnings

npx --prefix ruoyi-ui playwright test e2e/lead-todo-flow.spec.js --list
  PASS - exactly 10 journeys discovered
```

The helper was also executed, not only parsed, against migrated MySQL 8:
transactional setup produced the exact nine-Lead manifest, cleanup removed all
owned relational state, and the extended terminal-state assertion passed. A
separate database initialized from all eleven v0.15 baseline SQL files passed
`FlywayMigrationTest` through 0.20.53 (including injected failure, repair and
retry), `FileMaterialEndToEndTest`, and all three
`LeadTodoReadModelExternalMysqlIT` cases. The disposable database was removed.

The full `ruoyi-admin` source and test source compilation succeeded. A separate
`package` attempt reached Spring Boot repackage but could not rename the
existing `ruoyi-admin.jar` because the user's running PID 7116 holds it open on
Windows. The running process was intentionally left untouched; `mvn test`
subsequently compiled and tested the complete reactor successfully without
overwriting that JAR.

## Runtime handoff

The real-backend suite is intentionally gated by `LEAD_TODO_E2E`. A runtime
acceptance operator only needs to provide the isolated `_e2e` database, start
the migrated backend/frontend, and supply the purpose-created role credentials;
the suite owns deterministic setup, state assertions, and cleanup. No mock API
route is accepted by this suite.

# Lead Todo Guided Configuration Acceptance

Acceptance date: 2026-08-05

Branch: `runtime/startup-wiring-fix`

Reviewed Task 11 head: `6685e8ff9030be72067fa07ec9723d7f8138e114`

Review-round-1 implementation head: `2a7bf8c2697f08640691b8b84b5474fc1ef16395`

Review-round-2 final verified implementation head: `94c6688e7ec1e2cedb504956edcd2ba78d041245`

Review-round-2 commit message: `fix(todo): close lead template review gaps`

Final-review-round-1 verified implementation head: `f4d523c1b83fa10288da6db44a72b5112fbf4a53`

Decision: **PASS**. The governed TD-001 through TD-004 configuration, simulation,
publication, coordinated activation, runtime branching and five-day recurrence are ready as
one verified flow.

## Environment

| Item | Value |
|---|---|
| Backend | `http://127.0.0.1:18083` |
| UI | `http://127.0.0.1:4173` |
| Browser | Google Chrome via Playwright Chromium project |
| Runtime database | Fresh `law_task11_round2_browser_e2e` on MySQL 8.0, host port `13319`; automatically dropped by Playwright global teardown |
| External migration gate | Fresh `law_task11_round2_external`, initialized from the repository v0.15 SQL baseline and dropped after verification |
| Redis | `lead-todo-task12-redis`, host port `16381`; flushed before the final run |
| Latest migration | `0.20.80` |

The first external-database attempt used an empty schema and correctly failed at V0.15.2
because the required v0.15 `sys_job` baseline table was absent. The final gate recreated a
fresh schema, imported the same eleven baseline SQL files as CI, and then passed without a
skipped test.

## Coordinated publication and activation evidence

| Template | Published version | Governed scenarios | Locked route |
|---|---:|---:|---|
| TD-001 | 88 | 3/3 passed | `VALID -> TD-004 v92`; `SUSPECT_INVALID -> TD-002 v90`; `UNREACHABLE -> TD-003 v91` |
| TD-002 | 90 | 3/3 passed | The selected TD-002 task reaches a decision and exactly one current-version TD-001 task for `reviewResult EQ MISJUDGED_VALID` |
| TD-003 | 91 | 4/4 passed | `CONNECTED -> TD-004 v92`; retain/schedule/end branches remain governed |
| TD-004 | 92 | 3/3 passed, including required-proof failure | `PROGRESS_RECORDED -> SCHEDULE_SELF TD-004`; the guided editor persists the exact current immutable self version |

All current-hash simulation evidence rows were `PASSED`. Repeated full-simulation evidence
from deliberate reruns is append-only and does not replace the governed scenario evidence.

Exactly one trigger for `LEAD_FIRST_CONTACT_ENTRY` was enabled and it targeted TD-001 version
88. Previous entry rules were disabled. The browser confirmed the coordinated release binding
before runtime execution.

## Runtime evidence

The final real-browser run created three independent business scenarios:

1. Lead 9920548: TD-001 `SUSPECT_INVALID` -> TD-002 `TRUE_INVALID`; the lead moved to the dead pool.
2. Lead 9920549: TD-001 `SUSPECT_INVALID` -> TD-002 `MISJUDGED_VALID` -> a new current-version TD-001.
3. Lead 9920550: TD-001 `UNREACHABLE` -> scheduled TD-003 `CONNECTED` -> TD-004.

For lead 9920550 the authoritative database rows were:

| ID | Template / object | State | Identity |
|---:|---|---|---|
| Todo 30 | TD-001 v88 | COMPLETED | runtime root |
| Todo 31 | TD-003 v91 | COMPLETED | retry occurrence completed with `CONNECTED` |
| Todo 32 | TD-004 v92 | COMPLETED | routed from Todo 31; terminal state re-queried after actions |
| Todo 33 | TD-004 v92 | CREATED | materialized from the next `P5D` occurrence |
| Plan 4 | `LEAD_PROGRESS_5D` | ACTIVE | next occurrence due in 432000 seconds |
| Occurrence 5 | TD-004 v92 | MATERIALIZED | exactly one next Todo for the action identity |

The final evidence JSON was generated from terminal database re-queries, not pre-action snapshots.
It proves exactly one progress fact, one five-day plan, one occurrence and one next Todo for the
same action identity; the next due offset is 432000 seconds.

## Review-round closure

- V0.20.80 now remains idempotent when `todoScheduleTask.scan` already exists; a fresh external
  migration test pre-seeds the row before applying V0.20.80 and asserts the final count is one.
- Coordinated routing validates graph semantics instead of trusting outcome metadata alone. The
  compiler's canonical single-condition `AND` expression and implicit immutable TD-004 self route
  are accepted; disconnected, wrongly conditioned and wrong-version graphs remain rejected.
- One schedule-window parser now governs draft save, journey/preflight, simulation and runtime.
  Invalid `[{}]` schedules are blocked at every user-facing entry point.
- TD-002, TD-003 and TD-004 browser checks assert Chinese event/owner/SLA semantics, DoD recipe
  persistence, recommended-route persistence after step switching, publication and activation.
- The first two review reruns exposed the canonical `AND` and implicit self-route representation
  mismatches. Both were reproduced, fixed with focused regression tests, and the fresh final run
  then passed 2/2.

Review round 2 closed the remaining adversarial gaps:

- `schedule` is now fail-closed whenever the property is present: a non-object schedule, missing,
  empty or malformed windows, and mixed scalar plus window timing are rejected. The same central
  resolver is used by save, journey/preflight, simulation and runtime; valid scalar and valid
  window schedules remain accepted.
- TD-002 now rejects a graph that contains the correct `MISJUDGED_VALID` return edge plus any
  additional review-decision edge back to the current TD-001 task. Exactly one return edge and
  exactly one correct predicate are required.
- Chrome verifies the exact TD-002/TD-003/TD-004 DoD fields, proof catalogs, result labels,
  effects and immutable targets after recommendation, save and a step switch. Open dropdowns
  expose Chinese labels and the guarded technical codes do not appear.
- The browser found and closed a real TD-003 recipe-normalization defect: a grouped
  `requiredFields` condition is now expanded into four canonical runtime requirements with an
  `EQ` condition converted to `equals`. This prevents an undefined conditional field from being
  persisted by the guided editor.
- Backend readiness now follows the CI contract (`/captchaImage` reports
  `captchaEnabled=false`) before Playwright starts. The earlier port-only wait was the cause of
  the first login timeout; teardown deleting the database afterward caused the secondary
  `Unknown database` log noise.

Final review round 1 closed four additional release and concurrency gaps:

- Readiness and activation now reject any TD-001 through TD-004 candidate whose template is
  inactive, even when its selected version is published. The external MySQL lock test disables
  each downstream template independently, proves both paths reject it, re-enables it and then
  completes the release.
- TD-002 routing is exact rather than existence-based: the selected TD-002 task has one
  unconditional edge to one review decision; that decision has exactly `TRUE_INVALID -> END`,
  `MISJUDGED_VALID -> current TD-001` and a lower-priority default `-> END`. Direct bypasses and
  extra branches are rejected.
- TD-004 completion locks the authoritative lead row before authorization, state, progress-fact
  and scheduling checks. Real MySQL races prove completion waits behind reassignment/dead-pool
  transitions and then fails with zero fact, plan, occurrence or Todo writes.
- Chrome exposed a hidden-target defect after the stricter release validator landed: the guided
  TD-004 self route was binding the previous published version instead of the editable version
  being released. `SCHEDULE_SELF` now materializes and validates the exact current version; the
  unchanged coordinated-release assertion then passed on a fresh rerun.

## Verification matrix

| Layer | Command | Result |
|---|---|---|
| Backend reactor | `mvn clean test '-DskipTests=false'` | exit 0, all 10 reactor modules; admin suite 215 tests, 0 failed |
| Final-review backend reactor | `mvn clean test '-DskipTests=false'` | exit 0, all 10 reactor modules; admin suite 217 tests, 0 failed |
| Final-review focused release RED/GREEN | `mvn -pl law-todo -am '-Dtest=LeadTodoReleaseServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | RED exit 1 with the four expected inactive/routing failures; final exit 0, 24/24 |
| Final-review progress lock RED/GREEN | `mvn -pl ruoyi-system -am '-Dtest=LeadProgressMapperContractTest,LeadProgressCycleServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | RED failed before the locking mapper was used; final exit 0, 10/10 |
| Review-round-2 focused RED | `mvn -pl law-todo -am '-Dtest=TodoScheduleServiceTest,TodoDefinitionServiceTest,TodoConfigurationJourneyEvaluatorTest,TodoDefinitionSimulationServiceTest,LeadTodoReleaseServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | exit 1 before implementation; the initial compile also reported the intentionally missing central API/helper methods |
| Review-round-2 focused GREEN | same focused Maven command | exit 0, 127/127, 0 skipped |
| Release validator | `mvn -pl law-todo -am '-Dtest=LeadTodoReleaseServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | exit 0, included in the 127-test focused gate |
| Retry routing regression | `mvn -pl ruoyi-system -am '-Dtest=LeadRetryTodoHandlerTest,LeadRetryTodoCommandFlowTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | exit 0, 6/6 |
| External MySQL | `mvn -pl ruoyi-admin -am '-Dtest=LeadTodoGuidedConfigurationExternalMysqlIT,LeadTodoFlowEndToEndTest,FlywayMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | exit 0, 9/9, 0 skipped; 93 migrations validated through V0.20.80 |
| Final-review external MySQL | `mvn -pl ruoyi-admin -am '-Dtest=LeadTodoGuidedConfigurationExternalMysqlIT,LeadTodoFlowEndToEndTest,FlywayMigrationTest,LeadProgressCycleRuntimeMySqlTest,LeadTodoReleaseVersionLockExternalMysqlIT' '-Dsurefire.failIfNoSpecifiedTests=false' test` | exit 0, 16/16, 0 skipped; progress race 4/4 and release lock 3/3; 93 migrations through V0.20.80 |
| Backend package | `mvn -pl ruoyi-admin -am package -DskipTests` | exit 0 |
| Frontend Todo contracts | `npm run test:todo`; `npm run test:todo-config` | exit 0; exit 0 |
| Journey grouped-condition RED/GREEN | `npm run test:todo-phase-two` | exit 1 before normalization; final exit 0, 63 model and 43 UX checks |
| Schema and encoding | `npm run test:todo-schema`; `npm run test:encoding` | exit 0; exit 0 |
| E2E source/report contracts | `npm run test:e2e:contract` | initial exit 1 after the evidence contract changed; final exit 0 |
| Production frontend | `npm run build:prod` | exit 0; four pre-existing CSS-order/bundle-size warnings |
| Readiness/login probe | `Invoke-RestMethod http://127.0.0.1:18083/captchaImage`; `POST /login` | exit 0; `captchaEnabled=false`, login code 200, token present in 2850 ms |
| Real browser exploratory reruns | `npx playwright test tests/e2e/todo-config-journey.spec.js -g GUIDED_LEAD_ --project=chromium` | four exit-1 runs exposed, in order, startup readiness race, unstable dropdown targeting, the canonical material label, and grouped-condition normalization |
| Real browser final | same Playwright command with `TODO_E2E_BROWSER=chrome` | exit 0, fresh database, 2/2 in 92.6 s; publication/activation 34.8 s, runtime 50.8 s |
| Final-review real browser (superseded evidence) | same Playwright command with `TODO_E2E_BROWSER=chrome` | not used for final acceptance because retained logs could not prove both tests shared one database and application start |
| Final rereview one-DB browser | `npx playwright test tests/e2e/todo-config-journey.spec.js --grep "GUIDED_LEAD_" --project=chromium --reporter=list` | exit 0 on `lead_todo_round3_single_e2e`, one backend PID/start, exactly 2/2 in one invocation; templates 33.1 s, runtime 53.4 s |
| Disposable DB teardown | Playwright global teardown plus schema-existence query | exit 0; teardown logged the dropped database and the follow-up query returned no row |
| Patch whitespace | `git diff --check` | exit 0 |
| Worktree inventory | `git status --short` | exit 0; intentionally non-empty and reported separately below |

The worktree inventory remained non-empty because the user-owned
`.superpowers/sdd/task-7-report.md` and `ruoyi-ui/vue.config.js`, plus existing untracked browser,
runtime-log, output and `test-results` artifacts, were preserved. They were not staged in either
Task 11 commit. This dirty-state disclosure is separate from the passing product gates.

## Visual and machine-readable evidence

- [TD-002 seven-step configuration](../../../ruoyi-ui/output/playwright/lead-todo-guided-configuration/td002-seven-steps.png)
- [TD-003 retry timeline](../../../ruoyi-ui/output/playwright/lead-todo-guided-configuration/td003-retry-timeline.png)
- [TD-004 five-day cycle](../../../ruoyi-ui/output/playwright/lead-todo-guided-configuration/td004-five-day-cycle.png)
- [Active coordinated release binding](../../../ruoyi-ui/output/playwright/lead-todo-guided-configuration/lead-release-active-binding.png)
- [Runtime TD-004 and next occurrence](../../../ruoyi-ui/output/playwright/lead-todo-guided-configuration/lead-runtime-next-td004.png)
- [Runtime evidence JSON](../../../ruoyi-ui/output/playwright/lead-todo-guided-configuration/runtime-evidence.json)

All Task 11 disposable schemas, including the final browser database, external migration gate and
RED/GREEN migration schemas, were dropped after verification. All required database, backend,
frontend and browser gates passed. No unresolved Task 11 release blocker remains.

## Final review round 2 addendum — 2026-08-06

This addendum is the controlling final browser evidence. Earlier browser rows
and runtime identifiers in this document are retained as historical runs only.

- TD-004 `SCHEDULE_SELF` is bound to the exact editable candidate during load,
  save, journey/preflight and simulation. Missing, stale, wrong-draft and old
  published targets are rejected.
- One completion orchestrator now gives normal, force and automatic completion
  the same preparation fence. All three lock/revalidate the lead before any
  Todo, exception-log or audit mutation. Normal/auto run the handler before
  the Todo transition; force transitions first and then runs the handler. The
  whole operation remains atomic and preserves audit and idempotency semantics.
- The real MySQL lead-progress race suite increased from 4 to 6 cases and
  covers normal-versus-force and normal-versus-auto with no loser-side partial
  writes.
- The final required Chrome pair was rerun in one invocation on the single
  database `lead_todo_round3_single_e2e` and one backend PID 14984. The backend
  log contains one database identity and one application start. The exact
  list-reporter command passed `GUIDED_LEAD_TEMPLATES` then
  `GUIDED_LEAD_RUNTIME` 2/2 in 1.5 minutes with exit 0 (33.1 s and 53.4 s).
- Fresh runtime evidence at 2026-08-06 02:31:02 +08 and an independent query
  show TD-004 v88 Todo 8 completing and creating Todo 9 on TD-004 v88, with
  exactly one fact, plan, occurrence and next Todo and a 432000-second offset.
- Readiness/login, Playwright, requery, global teardown, absence query and
  backend stop all exited 0. Global teardown dropped the same database; the
  follow-up schema query returned zero rows and port 8080 had zero listeners.
- Full backend verification passed all 10 modules: 1547 tests, zero failures
  or errors; 37 tests were intentionally skipped without external-environment
  variables. The external MySQL gate separately passed 48/48 with zero skips.

Detailed evidence and the final verification matrix are recorded in
`docs/superpowers/reports/2026-08-06-lead-todo-final-review-round-two.md`.

## Reusable one-command acceptance addendum - 2026-08-06

The final governed pair is now covered by a committed PowerShell 5.1 harness
and source contract. `scripts/run-lead-todo-guided-acceptance.ps1` refuses an
existing or non-`_e2e` database, obtains all credentials from environment
variables without recording their values, imports the 11 baseline scripts,
runs Flyway and builds both applications, starts one backend, bootstraps once,
runs one exact Playwright command, requeries runtime state independently and
always performs guarded cleanup. The no-write `-ValidateOnly` mode and
`npm run test:guided-acceptance-harness` both pass.

The accepted run used `lead_todo_r4_20260805191157_e2e`, harness PID 20416,
launcher PID 18092 and Java listener PID 24884. The sanitized application log
has one database identity and one application start. All 32 manifest stages
exited 0. The one browser invocation passed the two required tests in 1.5
minutes (33.9 seconds and 52.7 seconds).

An independent query proved TD-004 Todo 10 `COMPLETED` to Todo 11 `CREATED`,
both on exact version 92, with previous Todo 10, exact-once 1/1/1/1 rows and a
432000-second offset. Global teardown dropped the database; the absence query,
backend stop and listener-absence checks all exited 0.

The run artifacts are ignored runtime evidence under
`ruoyi-ui/output/playwright/lead-todo-guided-configuration/`; they were not and
must not be committed. GitHub Actions uploads that directory through
`lead-todo-real-e2e-diagnostics` and `todo-config-real-e2e-diagnostics`. The
fresh-checkout environment variables and commands are listed in
`docs/superpowers/runbooks/lead-todo-guided-acceptance.md`.

## Process ownership hardening addendum - 2026-08-06

The reusable harness now treats process ownership as a release assertion. It
uses `Win32_Process` ancestry, accepts the backend listener only when descended
from the recorded launcher, rediscovers owned descendants in `finally` and
stops them deepest-first. It refuses but never kills an unowned listener. The
environment contract contains 14 variables, including the mandatory
`TODO_E2E_REDIS_DISPOSABLE` guard.

The post-bind controlled-failure run used
`lead_todo_r5_fail_20260805194004_e2e`. It proved launcher PID 3692 -> Java
listener PID 11568 ownership, rediscovered PIDs 3692/30492/11568 after the
intentional failure, deleted the database and recorded zero backend and
frontend listeners. Cleanup stages all exited 0; only the deliberate failure
stage exited 1, so the harness correctly returned 1.

The final successful run used `lead_todo_r5_final_20260805194313_e2e`, harness
PID 36624, backend launcher PID 6616, owned Java listener PID 34568 and
Playwright launcher PID 30996. All 32 stages exited 0. The exact Chrome pair
passed 2/2 in 84.6 seconds (33.4 and 45.1 seconds). The independent TD-004 row
again proved Todo 10 `COMPLETED` -> Todo 11 `CREATED`, version 92, exact-once
1/1/1/1 and 432000 seconds.

The durable `services.listener-absence` manifest stage records backend port
8080/count 0 and frontend port 4173/count 0. Global teardown and independent
database absence also returned 0. The ignored runtime artifacts were not
committed; CI upload locations and fresh-checkout commands remain documented
in `docs/superpowers/runbooks/lead-todo-guided-acceptance.md`.

## Round 6 immutable process identity and isolated proof bundles - 2026-08-06

Round 6 supersedes the PID-only cleanup and shared artifact-directory evidence.
Root ownership is now PID plus exact `CreationDate`, captured name, executable
path and command line. Roots must be created after the run began and match the
recorded identity before they seed descendant discovery. Every descendant is
creation-threshold checked, and every process is reread immediately before
`Stop-Process`. An already exited PID is accepted as stopped; a live PID with
a different creation identity is refused and never killed. Backend and
Playwright roots are registered immediately after `Start-Process`, before
waiting, including the Playwright failure path.

Executable PowerShell/Node contracts passed for a pre-minimum root, a reused
PID, a valid descendant, an unowned listener, stop-time identity mismatch,
start-time Playwright registration, unsafe run IDs and no-mutation validation.
The pure E2E path test passed traversal, absolute-external and sibling-prefix
rejection. The 14 E2E helper tests, real-E2E source contract and harness
contract all exited 0.

The controlling controlled-failure bundle is the ignored directory
`ruoyi-ui/output/playwright/lead-todo-guided-configuration/runs/20260806-harness-postbind-proof/`.
The harness exited 1 only because
`harness.test-only-failure-after-backend-bind=1`; owned-tree stop, guarded
fallback drop, independent fallback schema-absence proof, dual-port listener
proof and sanitized failed-backend evidence all exited 0. Its disposable
database and ports 8080/4173 were independently absent afterward.

The controlling success bundle is the separate ignored directory
`ruoyi-ui/output/playwright/lead-todo-guided-configuration/runs/20260806-harness-guided-final/`.
All 32 manifest stages exited 0. Harness PID 20184 registered backend root PID
12488 and Playwright root PID 19404 with immutable identities; the verified
Java listener was PID 36852. One Chrome invocation passed 2/2 in 1.4 minutes
(33.3 and 50.0 seconds). The independent runtime row was:

```text
10  COMPLETED  92  11  CREATED  92  10  1  1  1  1  432000
```

Global teardown, schema-absence proof, owned process stop, backend/frontend
listener absence and sanitized backend evidence all exited 0. The failure and
success bundles coexist and are intentionally ignored; a fresh reproduction
uses a new `-RunId` from the updated runbook and never overwrites either one.

## Review round 1 safety closure - 2026-08-06

The run lease is now independent of directory existence. The harness first
creates `runs/.claims/<runId>.json` with OS `CreateNew`, retains that claim,
and only then creates the bundle. A duplicate or concurrent attempt is refused
without changing the prior claim, run directory or temporary fingerprint.
Playwright also requires an explicit exact
`lead-todo-guided-configuration/runs/<safeRunId>` artifact path; missing,
shared-root and nested paths fail closed in both local and CI contracts.

Process authorization now checks every ancestry edge chronologically and
requires nonblank captured name, executable path, command line and expected
signature. Registered roots are sampled at every recorded-stage boundary and
every 100 ms poll of any external-process stage. An immutable captured child
remains cleanup-authorized if its root exits or it is reparented, while an
uncaptured orphan remains unauthorized.
The process self-test proves stale-child and multi-level rejection, captured
orphan authorization, uncaptured-orphan refusal, missing-metadata refusal,
reused-PID refusal and stop-time identity mismatch refusal.

The retained controlled failure is
`runs/20260806-r1-postbind-proof/`. Its manifest status is `FAILED` only at
`harness.test-only-failure-after-backend-bind` (exit 1); 26 other recorded
stages passed, including owned-process stop, guarded fallback drop, independent
schema absence, dual-port absence and sanitized backend failure evidence.

The retained final proof is `runs/20260806-r1-guided-final4/`. All 32 stages
exited 0. Backend launcher PID 2248 resolved application PID 35908; Playwright
launcher PID 8684 was the second root. Continuous capture recorded 72 immutable
identities. The one Chrome invocation passed exactly 2/2 in 1.4 minutes (34.3
and 49.5 seconds). Independent MySQL requery returned:

```text
10  COMPLETED  92  11  CREATED  92  10  1  1  1  1  432000
```

Both run claims match their bundle claim and manifest lease token. After each
run, every recorded identity was absent. Both disposable schemas were absent,
and ports 8080 and 4173 had zero listeners. Runtime bundles remain ignored and
were not committed.

## Review round 2 all-registered-root polling - 2026-08-06

Round 2 corrects the scope of the previous continuous-capture wording. The
harness does not run an unscheduled watcher inside arbitrary PowerShell code.
Instead, one `Update-AllRegisteredRoots` boundary refreshes every immutable
root before and after each recorded operation, and before, during every 100 ms
wait poll, after exit and in `finally` for every generic external-process
stage. That polling is independent of the current stage's registration flag,
so the backend tree remains sampled while unrelated MySQL, Redis, npm and
Playwright commands execute. Readiness loops use the same registry directly.

The RED source contract failed because this common boundary did not exist and
the generic poll was guarded by `RegisterOwnedRoot`. GREEN requires the common
function, unconditional no-output polling, both pure-stage boundaries and a
behavioral fixture that captures a mid-poll child of a background root and
still authorizes it after reparenting.

The new controlled bundle `runs/20260806-r2-postbind-proof/` has status
`FAILED` only for the injected post-bind stage. It captured three immutable
backend identities; cleanup stopped both remaining live identities with no
mismatch. Process stop, guarded fallback drop, schema absence and both listener
checks all exited 0.

The controlling success bundle is now `runs/20260806-r2-guided-final/`.
Harness PID 33060 registered backend launcher PID 27388/application PID 11680
and Playwright root PID 18708. All 32 stages exited 0 and 71 immutable
identities were captured. Chrome passed exactly 2/2 in 1.5 minutes (34.4 and
51.8 seconds); independent MySQL proof returned:

```text
10  COMPLETED  92  11  CREATED  92  10  1  1  1  1  432000
```

Both run claims match their bundle/manifest tokens. Independent checks found
zero remaining schemas, zero listeners on 8080/4173 and no live recorded PID.
The bundles remain ignored runtime evidence.

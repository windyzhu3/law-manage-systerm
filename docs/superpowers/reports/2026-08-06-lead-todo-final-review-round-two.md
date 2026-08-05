# Lead Todo final review round 2

Date: 2026-08-06
Scope: TD-004 exact candidate routing and unified completion lock order

## Outcome

The round is accepted. TD-004 `SCHEDULE_SELF` now retains the exact editable
candidate version through guided loading, draft save, journey evaluation,
preflight and simulation. A missing version, a different draft, an unknown
version or an older published version is rejected instead of being silently
rebound.

Normal completion, forced completion and automatic completion now share one
completion orchestrator. The handler preparation phase locks and revalidates
the authoritative lead before any Todo mutation. Handler effects and Todo
transitions retain each entry point's existing order inside the same
transaction. Existing action logs, exception audit, idempotency and
terminal-state semantics are preserved.

## Implemented controls

### Exact TD-004 candidate

- `TodoBusinessOutcomeCatalogService` accepts the editable candidate version
  and materializes TD-004 `SCHEDULE_SELF` to that exact version.
- `TodoDefinitionService` applies the same candidate-aware validation during
  save, preflight and the simulation gate.
- `TodoConfigurationJourneyService` and its evaluator use the editable
  version, not the previously published TD-004 version.
- The guided Chrome journey refreshes governed targets before saving another
  step after a downstream publication. This keeps stale references fail-closed.
- The TD-004 browser assertion reloads the page, re-enters routing and verifies
  both the visible route and persisted `targetVersionId` again.

### Unified completion lock order

- `TodoCompletionOrchestrator` is the single entry point for handler selection,
  preparation and execution.
- `TodoCompletionHandler.prepare` is backward-compatible and defaults to a
  no-op for handlers without a business lock requirement.
- `LeadProgressHandoffTodoHandler.prepare` locks and revalidates the lead via
  `LeadProgressCycleService` without writing progress or schedule facts.
- Normal, forced and automatic completion call `prepare` before any Todo,
  exception-log or audit mutation. Normal and automatic completion call the
  handler before the Todo transition because its authoritative result may
  retain the Todo; forced completion performs the terminal transition and then
  calls the handler. All steps remain in one transaction.
- Deterministic MySQL races cover normal versus force and normal versus auto.
  The competitor waits on the lead lock, the normal completion wins, and the
  loser receives `TODO_CONCURRENT_MODIFICATION` with no partial audit, fact,
  plan, occurrence or next-Todo rows.

## Verification

| Gate | Result |
|---|---|
| Candidate catalog/service focused tests | 40/40 passed |
| Journey evaluator/service focused tests | 23/23 passed |
| Lead progress handler focused tests | 5/5 passed |
| `TodoDefinitionServiceTest` | 39/39 passed |
| Real MySQL lead progress race suite | 6/6 passed, 0 skipped |
| Main external MySQL CI suite | 48/48 passed, 0 skipped |
| External report contract | 25 reports verified, no skip or failure |
| Full backend `mvn clean test -DskipTests=false` | all 10 modules passed; 1547 tests, 0 failures/errors, 37 environment skips |
| Frontend Todo/config/phase-two/schema/encoding/foundation gates | passed |
| Frontend production build | passed with existing non-blocking warnings |
| Final Chrome one-invocation rerun | exit 0; exactly 2/2 passed in 1.5 minutes on one database and one backend |

The two required Chrome tests were:

1. `GUIDED_LEAD_TEMPLATES`: seven-step configuration, governed scenarios,
   publication and coordinated release activation; 33.1 seconds.
2. `GUIDED_LEAD_RUNTIME`: valid, suspected-invalid, unreachable/retry and
   five-day recurrence paths; 53.4 seconds.

The final evidence run used database `lead_todo_round3_single_e2e`, backend
`http://127.0.0.1:8080`, frontend `http://127.0.0.1:4173` and backend PID
14984. The backend log contains exactly one matching JDBC database identity and
one `Started RuoYiApplication` record. Bootstrap ran once after Flyway reached
V0.20.80; the readiness probe returned `captchaEnabled=false`, login code 200
and a non-empty token.

One and only one Playwright invocation produced the final acceptance result:

```text
npx playwright test tests/e2e/todo-config-journey.spec.js --grep "GUIDED_LEAD_" --project=chromium --reporter=list
```

Fresh artifacts were written at 2026-08-06 02:31:02 +08. Runtime evidence and
an independent post-run database query record TD-004 Todo 8 as `COMPLETED` and
Todo 9 as `CREATED`, both on exact version 88 and linked by `previousTodoId`.
They also record exactly one follow-up fact, one plan, one occurrence and one
next Todo: follow-up 4, plan 2, occurrence 2 and a 432000-second due offset.

Evidence:

- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/lead-runtime-next-td004.png`
- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/runtime-evidence.json`
- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/round3-guided-lead-list.log`
- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/round3-runtime-requery.log`
- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/round3-readiness.log`
- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/round3-single-backend.log`
- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/round3-teardown.log`
- `ruoyi-ui/test-results/.last-run.json` (`status: passed`)

Numeric statuses: package 0, readiness/login 0, Playwright 0, runtime requery 0,
global teardown 0, information-schema absence query 0 and backend stop 0.

## CI contract maintenance

The external-database gate was synchronized with the current V0.20.80 schema:
historical fixture targets, required schedule columns, published-template
count, navigation/system encoding suites and the 25-report assertion now match
the tests actually executed by CI. These changes do not relax any runtime or
migration assertion.

## Cleanup

Playwright's global teardown dropped the exact database
`lead_todo_round3_single_e2e`. The follow-up `information_schema.schemata`
query returned zero rows, after which PID 14984 was stopped and port 8080 had
zero listeners. Pre-existing Docker containers and unrelated user-owned
worktree artifacts were preserved.

## Round 4 reusable acceptance harness

The browser evidence is now reproducible from a fresh checkout through
`scripts/run-lead-todo-guided-acceptance.ps1`. The PowerShell 5.1 parser,
non-mutating `-ValidateOnly` mode and the npm harness contract all pass. The
contract checks 14 required environment variables, including the explicit
disposable-Redis declaration, the `_e2e` database guard,
all 11 baseline SQL sources, one exact browser command, independent runtime
requery, guarded teardown and separate harness/launcher/application PIDs.

The final harness run used the one fresh database
`lead_todo_r4_20260805191157_e2e` from 2026-08-06 03:11:57 to 03:15:29 +08:00.
All 32 recorded stages have numeric exit status 0. Harness PID 20416 launched
wrapper PID 18092; the listener independently resolved Java application PID
24884, so the manifest does not conflate the wrapper and application process.
The sanitized backend log contains exactly one database identity and one
`Started RuoYiApplication` record.

Bootstrap executed once after migration and application readiness. Its
independent proof row was:

```text
r420260805191157  1  1  1  1  1
```

Those counts prove one marked configuration administrator, role, Lead, SLA
rule and DoD rule. Readiness then proved `captchaEnabled=false`, login code 200,
a non-empty token and no token logging.

One browser process executed exactly:

```text
npx playwright test tests/e2e/todo-config-journey.spec.js --grep "GUIDED_LEAD_" --project=chromium --reporter=list
```

It passed exactly 2/2 with one worker in 92.7 seconds: templates in 33.9
seconds and runtime in 52.7 seconds. The independent terminal database query
then returned:

```text
10  COMPLETED  92  11  CREATED  92  10  1  1  1  1  432000
```

This proves TD-004 Todo 10 completed, Todo 11 was created on the same exact
version 92 and linked by `previous_todo_id=10`, with exactly one follow-up
fact, plan, occurrence and next Todo and the five-day offset.

The repository global teardown dropped that exact database. A separate
information-schema query returned 0, the owned backend stopped, and ports 8080
and 4173 had zero listeners. The disposable MySQL and Redis containers were
left running and no unrelated service or worktree artifact was changed.

All runtime logs, JSON, screenshots and the sanitized manifest remain under
the ignored `ruoyi-ui/output/playwright/lead-todo-guided-configuration/`
directory and are not part of the commit. CI uploads this path in the
`lead-todo-real-e2e-diagnostics` artifact (and the broader configuration job's
`todo-config-real-e2e-diagnostics` artifact). Fresh-checkout prerequisites,
environment variable names and safe execution commands are documented in
`docs/superpowers/runbooks/lead-todo-guided-acceptance.md`.

## Round 5 process-ownership and dual-port evidence

Round 5 supersedes the Round 4 process-cleanup evidence while retaining its
product result. The harness now discovers ownership through PowerShell
5.1-compatible `Win32_Process` parent relationships. Backend readiness accepts
the listener only when its PID is in the owned launcher's descendant tree.
Finally cleanup rediscovers all owned descendants, stops them deepest-first and
never kills an arbitrary process merely because it is listening on a test
port. Any remaining unowned backend or frontend listener fails closed with
`UNOWNED_SERVICE_LISTENER`.

The source contract now checks all 14 required environment variables,
including `TODO_E2E_REDIS_DISPOSABLE`, plus ancestry discovery, finally
rediscovery, unowned-listener refusal, the controlled failure seam and durable
backend/frontend port evidence. The non-mutating process self-test selected the
fixture tree 100 -> 101 -> 102, calculated depth 2, excluded PID 900 and proved
the unowned listener is refused.

A real controlled failure ran on the fresh database
`lead_todo_r5_fail_20260805194004_e2e`. Backend listener PID 11568 was verified
as a descendant of launcher PID 3692. After the explicit post-bind failure,
finally rediscovered PIDs 3692, 30492 and 11568. The owned-tree stop,
guarded fallback drop and dual-port proof all exited 0; the independent schema
count, backend listener count and frontend listener count were all 0. The
harness itself correctly exited 1 because the injected failure stage is the
expected subject of this test.

The controlling successful run then used exactly one fresh database,
`lead_todo_r5_final_20260805194313_e2e`, from 2026-08-06 03:43:14 to 03:46:43
+08:00. All 32 stages exited 0. Harness PID 36624 owned backend launcher PID
6616; Java listener PID 34568 was independently proven to be its descendant.
Playwright launcher PID 30996 was retained as a second cleanup root. The
sanitized backend log again contains one database identity and one application
start.

The bootstrap proof was `r520260805194313 1 1 1 1 1`. The exact one-invocation
Chrome pair passed 2/2 in 84.6 seconds: template configuration in 33.4 seconds
and runtime in 45.1 seconds. The independent terminal result remained:

```text
10  COMPLETED  92  11  CREATED  92  10  1  1  1  1  432000
```

Global teardown and the schema-absence query exited 0. The final manifest's
`services.listener-absence` stage explicitly records backend port 8080 with
count 0 and frontend port 4173 with count 0. An additional terminal query
confirmed the same database and port absence. Runtime evidence remains ignored
under `ruoyi-ui/output/playwright/lead-todo-guided-configuration/` and is not
part of the commit.

## Follow-on harness safety closure

The final-review harness findings are closed by immutable process identity and
per-run proof isolation. PID alone is no longer authority: root and descendant
use requires an exact creation-time match plus captured executable/command
signature when available, and the same identity is reread before every kill.
Backend and Playwright launchers are registered at process start, before any
wait, so a failed Playwright process remains owned for `finally` cleanup.

Evidence now lives under
`ruoyi-ui/output/playwright/lead-todo-guided-configuration/runs/<runId>/`.
Run IDs and resolved Playwright paths fail closed on unsafe characters,
traversal, siblings and external absolute paths. Existing run directories are
never removed or overwritten. CI continues to upload the parent directory.

The ignored controlled-failure run `20260806-harness-postbind-proof` returned
the expected overall status 1 while process-tree stop, fallback drop,
independent schema absence, both listener checks and sanitized failure-log
retention returned 0. The separate final run
`20260806-harness-guided-final` returned 0 with all 32 stages at 0, Chrome 2/2,
the exact runtime row `10 COMPLETED 92 -> 11 CREATED 92`, exact-once 1/1/1/1
and 432000 seconds. Both disposable schemas and both test ports were absent
afterward. These runtime bundles are reproducible diagnostics, not committed
source; the updated runbook supplies the fresh-run commands.

## Final-review round 1 corrective closure

The follow-on implementation now closes the remaining safety review findings:

- Every root and child requires exact creation identity plus nonblank name,
  executable, command line and authorization signature. Each child creation
  time must be at or after its current parent's creation time at every depth.
- Backend and Playwright descendants are sampled at all recorded-stage
  boundaries and every 100 ms poll of any external process stage, not only at
  final cleanup. Captured orphans remain identity-authorized after root exit or
  reparenting; uncaptured or stale/PID-reused processes are refused.
- Run ownership is an OS-atomic persistent `CreateNew` claim made before bundle
  creation. Duplicate/concurrent attempts cannot mutate an existing claim,
  bundle or temporary fingerprint, and cleanup/manifest writes are lease-gated.
- The E2E artifact directory is mandatory and must be exactly the governed
  root plus `runs/<safeRunId>`. The shared root, nested directories, traversal,
  siblings and outside absolute paths are rejected; CI supplies the explicit
  per-run path.
- Registered process-stage capture cannot leak objects into the single stage
  result; a contract test guards the output stream after the final Chrome run
  exposed and drove this fix.

The controlled post-bind run `20260806-r1-postbind-proof` returned overall 1
only at the injected failure stage. Process stop, guarded fallback drop,
independent database absence, backend/frontend listener absence and sanitized
failure evidence all returned 0. It captured three immutable backend-tree
identities; cleanup stopped the two still live identities without a mismatch.

The final-code run `20260806-r1-guided-final4` returned overall 0 with all 32
stages at 0. It registered backend root PID 2248/application PID 35908 and
Playwright root PID 8684, continuously recording 72 immutable identities. One
Chrome invocation passed the governed pair 2/2 in 1.4 minutes (34.3 and 49.5
seconds). Independent runtime proof remained:

```text
10  COMPLETED  92  11  CREATED  92  10  1  1  1  1  432000
```

Persistent and in-bundle claims match each manifest's run ID and lease token.
Both disposable databases independently count 0, ports 8080 and 4173 have no
listeners, and every captured PID is absent. The two ignored bundles coexist;
no runtime evidence was staged.

## Safety rereview round 2 closure

The final remaining finding was valid: the earlier external-process loop only
updated the identity registry when that current stage had registered its own
root. After backend readiness, unrelated Redis/MySQL/npm stages therefore did
not poll the already registered backend tree. The fix introduces the common
no-output `Update-AllRegisteredRoots` operation and removes that conditional.

Every recorded in-process stage now refreshes all roots at entry and in
`finally`. Every external-process stage refreshes all roots before launch,
after optional registration, every 100 ms while waiting, after exit and in
`finally`. Readiness loops retain their explicit refresh. This is precise
whole-backend-lifetime sampling at harness scheduling points, not a claim of an
independent watcher inside arbitrary in-process action code.

The executable source contract requires the common function, unconditional
generic polling, no output pollution and both pure-stage boundaries. The pure
self-test registers background root 700, observes child 701 during an unrelated
poll, then verifies that the recorded immutable identity remains authorized
after reparenting. Existing stale-child, multi-level chronology, reused-PID,
missing-metadata and uncaptured-orphan refusals still pass.

The controlled run `20260806-r2-postbind-proof` returned harness status 1 only
at the intended injection; cleanup, fallback database drop/absence and both
listener proofs returned 0. The final run `20260806-r2-guided-final` returned 0
with all 32 stages at 0. Backend launcher PID 27388 resolved application PID
11680; Playwright launcher PID 18708 was the second root. It captured 71
immutable identities and had no cleanup mismatch.

Chrome passed 2/2 in 1.5 minutes (34.4 and 51.8 seconds), and independent
runtime proof remained:

```text
10  COMPLETED  92  11  CREATED  92  10  1  1  1  1  432000
```

Persistent claims match both manifests and bundle claims. Both disposable
schemas, every captured identity and listeners on ports 8080/4173 were absent
afterward. Runtime bundles remain ignored and unstaged.

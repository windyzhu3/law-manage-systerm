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
contract checks 13 required environment variables, the `_e2e` database guard,
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

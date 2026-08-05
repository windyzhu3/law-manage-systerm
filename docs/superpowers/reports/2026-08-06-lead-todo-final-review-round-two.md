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

- `TodoCompletionOrchestrator` is the single entry point for handler prepare
  and post-mutation handling.
- `TodoCompletionHandler.prepare` is backward-compatible and defaults to a
  no-op for handlers without a business lock requirement.
- `LeadProgressHandoffTodoHandler.prepare` locks and revalidates the lead via
  `LeadProgressCycleService` without writing progress or schedule facts.
- Normal, forced and automatic completion call prepare before any Todo
  mutation and call handle only after the terminal mutation.
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
| Final Chrome `--grep "GUIDED_LEAD_"` | 2/2 passed in 1.5 minutes |

The two required Chrome tests were:

1. `GUIDED_LEAD_TEMPLATES`: seven-step configuration, governed scenarios,
   publication and coordinated release activation; 38.1 seconds.
2. `GUIDED_LEAD_RUNTIME`: valid, suspected-invalid, unreachable/retry and
   five-day recurrence paths; 46.8 seconds.

Fresh runtime evidence records TD-004 Todo 46 as `COMPLETED` and Todo 47 as
`CREATED`, both on exact version 92 and linked by `previousTodoId`. The same
evidence records one follow-up fact, plan 6, occurrence 14 and a 432000-second
five-day due offset.

Evidence:

- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/lead-runtime-next-td004.png`
- `ruoyi-ui/output/playwright/lead-todo-guided-configuration/runtime-evidence.json`
- `ruoyi-ui/test-results/.last-run.json` (`status: passed`)

## CI contract maintenance

The external-database gate was synchronized with the current V0.20.80 schema:
historical fixture targets, required schedule columns, published-template
count, navigation/system encoding suites and the 25-report assertion now match
the tests actually executed by CI. These changes do not relax any runtime or
migration assertion.

## Cleanup

The real backend was stopped after browser verification. All disposable
`lead_todo_round2_*` schemas created by this review were dropped and their
absence was queried as teardown proof. Pre-existing Docker containers and
unrelated user-owned worktree artifacts were preserved.

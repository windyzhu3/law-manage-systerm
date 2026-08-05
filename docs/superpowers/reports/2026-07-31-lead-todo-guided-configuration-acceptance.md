# Lead Todo Guided Configuration Acceptance

Acceptance date: 2026-08-05

Branch: `runtime/startup-wiring-fix`

Reviewed Task 11 head: `6685e8ff9030be72067fa07ec9723d7f8138e114`

Review-fix and final verified implementation head: `2a7bf8c2697f08640691b8b84b5474fc1ef16395`

Review-fix commit message: `fix(todo): close governed lead review findings`

Decision: **PASS**. The governed TD-001 through TD-004 configuration, simulation,
publication, coordinated activation, runtime branching and five-day recurrence are ready as
one verified flow.

## Environment

| Item | Value |
|---|---|
| Backend | `http://127.0.0.1:18083` |
| UI | `http://127.0.0.1:4173` |
| Browser | Google Chrome via Playwright Chromium project |
| Runtime database | Fresh `law_task11_review_final_e2e` on MySQL 8.0, host port `13319`; automatically dropped by Playwright global teardown |
| External migration gate | Fresh `law_task11_review_external_e2e`, initialized from the repository v0.15 SQL baseline and dropped after verification |
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
| TD-004 | 92 | 3/3 passed, including required-proof failure | `PROGRESS_RECORDED -> SCHEDULE_SELF TD-004`; the immutable self version may be implicit |

All current-hash simulation evidence rows were `PASSED`. Repeated full-simulation evidence
from deliberate reruns is append-only and does not replace the governed scenario evidence.

Exactly one trigger for `LEAD_FIRST_CONTACT_ENTRY` was enabled and it targeted TD-001 version
88. Previous entry rules were disabled. The browser confirmed the coordinated release binding
before runtime execution.

## Runtime evidence

The final real-browser run created three independent business scenarios:

1. Lead 9920527: TD-001 `SUSPECT_INVALID` -> TD-002 `TRUE_INVALID`; the lead moved to the dead pool.
2. Lead 9920528: TD-001 `SUSPECT_INVALID` -> TD-002 `MISJUDGED_VALID` -> a new current-version TD-001.
3. Lead 9920529: TD-001 `UNREACHABLE` -> scheduled TD-003 `CONNECTED` -> TD-004.

For lead 9920529 the authoritative database rows were:

| ID | Template / object | State | Identity |
|---:|---|---|---|
| Todo 8 | TD-001 v88 | COMPLETED | runtime root |
| Todo 9 | TD-003 v91 | COMPLETED | retry plan 1, occurrence 1 (`T0`) |
| Todo 10 | TD-004 v92 | COMPLETED | routed from Todo 9; terminal state re-queried after actions |
| Todo 11 | TD-004 v92 | CREATED | materialized from plan 2, occurrence 2 (`P5D`) |
| Plan 1 | `LEAD_RETRY` | CONTACTED | completion reason `CONTACTED` |
| Plan 2 | `LEAD_PROGRESS_5D` | ACTIVE | next occurrence due in 432000 seconds |

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

## Verification matrix

| Layer | Command | Result |
|---|---|---|
| Backend reactor | `mvn clean test '-DskipTests=false'` | PASS, all 10 reactor modules; admin suite 215 tests, 0 failed |
| Release validator | `mvn -pl law-todo -am '-Dtest=LeadTodoReleaseServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS, 19/19 |
| Retry routing regression | `mvn -pl ruoyi-system -am '-Dtest=LeadRetryTodoHandlerTest,LeadRetryTodoCommandFlowTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS, 6/6 |
| External MySQL | `mvn -pl ruoyi-admin -am '-Dtest=LeadTodoGuidedConfigurationExternalMysqlIT,LeadTodoFlowEndToEndTest,FlywayMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS, 9/9, 0 skipped; 93 migrations validated through V0.20.80 |
| Frontend Todo contracts | `npm run test:todo`; `npm run test:todo-config` | PASS |
| Journey model and UX | `npm run test:todo-phase-two` | PASS, 62 model and 43 UX checks |
| Schema and encoding | `npm run test:todo-schema`; `npm run test:encoding` | PASS |
| E2E source/report contracts | `npm run test:e2e:contract` | PASS |
| Production frontend | `npm run build:prod` | PASS; four pre-existing CSS-order/bundle-size warnings |
| Real browser | `npx playwright test tests/e2e/todo-config-journey.spec.js -g 'GUIDED_LEAD_' --project=chromium` | PASS, fresh database, 2/2 in 1.4 minutes; publication/activation 31.3 s, runtime 45.9 s |
| Disposable DB teardown | Playwright global teardown plus `information_schema.schemata` query | PASS; teardown logged the dropped database and the follow-up query returned no row |

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

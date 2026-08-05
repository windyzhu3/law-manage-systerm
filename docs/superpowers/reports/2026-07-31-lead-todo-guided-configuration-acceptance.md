# Lead Todo Guided Configuration Acceptance

Acceptance date: 2026-08-05

Branch: `runtime/startup-wiring-fix`

Acceptance candidate base: `8c26f6ea8de4afbe457df3c346e4d3fecbba710e`

Task commit message: `test(todo): verify governed lead template flow`

Decision: **PASS**. The governed TD-001 through TD-004 configuration, simulation,
publication, coordinated activation, runtime branching and five-day recurrence are ready as
one verified flow.

## Environment

| Item | Value |
|---|---|
| Backend | `http://127.0.0.1:18083` |
| UI | `http://127.0.0.1:4173` |
| Browser | Google Chrome via Playwright Chromium project |
| Runtime database | `law_task11_browser_final7_e2e` on MySQL 8.0, host port `13319` |
| External migration gate | `law_task11_mysql_gate2_20260805`, initialized from the repository v0.15 SQL baseline |
| Redis | `lead-todo-task12-redis`, host port `16381`; flushed before the final run |
| Latest migration | `0.20.80` |

The first external-database attempt used an empty schema and correctly failed at V0.15.2
because the required v0.15 `sys_job` baseline table was absent. The final gate recreated a
fresh schema, imported the same eleven baseline SQL files as CI, and then passed without a
skipped test.

## Coordinated publication and activation evidence

| Template | Published version | Governed scenarios | Locked route |
|---|---:|---:|---|
| TD-001 | 84 | 3/3 passed | `VALID -> TD-004 v88`; `SUSPECT_INVALID -> TD-002 v86`; `UNREACHABLE -> TD-003 v87` |
| TD-002 | 86 | 3/3 passed | `MISJUDGED_VALID -> TD-001`; terminal invalid branches end |
| TD-003 | 87 | 4/4 passed | `CONNECTED -> TD-004 v88`; retain/schedule/end branches remain governed |
| TD-004 | 88 | 3/3 passed, including required-proof failure | `PROGRESS_RECORDED -> SCHEDULE_SELF TD-004 v88` |

All current-hash simulation evidence rows were `PASSED`. Repeated full-simulation evidence
from deliberate reruns is append-only and does not replace the governed scenario evidence.

Trigger rule `53` (`TRIGGER_LEAD_ASSIGNED_TD001_V3`) is the only enabled rule for
`LEAD_FIRST_CONTACT_ENTRY`; it targets TD-001 version 84. The previous entry rule is disabled.
The browser confirmed the coordinated release binding before runtime execution.

## Runtime evidence

The final real-browser run created three independent business scenarios:

1. Lead 7: TD-001 `SUSPECT_INVALID` -> TD-002 `TRUE_INVALID`; the lead moved to the dead pool.
2. Lead 8: TD-001 `SUSPECT_INVALID` -> TD-002 `MISJUDGED_VALID` -> a new TD-001.
3. Lead 9: TD-001 `UNREACHABLE` -> scheduled TD-003 `CONNECTED` -> TD-004.

For lead 9 the authoritative database rows were:

| ID | Template / object | State | Identity |
|---:|---|---|---|
| Todo 6 | TD-001 v84 | COMPLETED | runtime root |
| Todo 7 | TD-003 v87 | COMPLETED | retry plan 1, occurrence 1 (`T0`) |
| Todo 8 | TD-004 v88 | COMPLETED | routed from Todo 7 |
| Todo 9 | TD-004 v88 | CREATED | materialized from plan 2, occurrence 2 (`P5D`) |
| Plan 1 | `LEAD_RETRY` | CONTACTED | completion reason `CONTACTED` |
| Plan 2 | `LEAD_PROGRESS_5D` | ACTIVE | next occurrence due in 432000 seconds |

Business events 8 (`LEAD_ASSIGNED`), 9 (`LEAD_FIRST_CONTACT_UNREACHABLE`) and 10
(`LEAD_RETRY_CONNECTED`) were all `PROCESSED`, with zero retries and no error. The TD-004
duplicate-completion MySQL test also proves one progress fact, one plan, one occurrence and one
next Todo for the same action identity.

During the first runtime pass, TD-003 completed but did not create TD-004. Database evidence
proved that the route condition consumed `contactResult` while the typed retry handler emitted
only the legacy `result`. A regression test was observed failing first, then the handler was
changed to emit authoritative `contactResult` alongside `result`; focused tests and the entire
browser flow subsequently passed.

## Verification matrix

| Layer | Command | Result |
|---|---|---|
| Backend reactor | `mvn clean test '-DskipTests=false'` | PASS, all 10 reactor modules; 1:10 |
| Release validator | `mvn -pl law-todo -am '-Dtest=LeadTodoReleaseServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS, 17/17 |
| Retry routing regression | `mvn -pl ruoyi-system -am '-Dtest=LeadRetryTodoHandlerTest,LeadRetryTodoCommandFlowTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS, 6/6 |
| External MySQL | `mvn -pl ruoyi-admin -am '-Dtest=LeadTodoGuidedConfigurationExternalMysqlIT,LeadTodoFlowEndToEndTest,FlywayMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS, 9/9, 0 skipped; 93 migrations validated through V0.20.80 |
| Frontend Todo contracts | `npm run test:todo`; `npm run test:todo-config` | PASS |
| Journey model and UX | `npm run test:todo-phase-two` | PASS, 62 model and 43 UX checks |
| Schema and encoding | `npm run test:todo-schema`; `npm run test:encoding` | PASS |
| E2E source/report contracts | `npm run test:e2e:contract` | PASS |
| Production frontend | `npm run build:prod` | PASS; four pre-existing CSS-order/bundle-size warnings |
| Real browser | `npx playwright test tests/e2e/todo-config-journey.spec.js -g 'GUIDED_LEAD_' --project=chromium` | PASS, 2/2 in 1.3 minutes; publication/activation 28.7 s, runtime 44.0 s |

## Visual and machine-readable evidence

- [TD-002 seven-step configuration](../../../output/playwright/lead-todo-guided-configuration/td002-seven-steps.png)
- [TD-003 retry timeline](../../../output/playwright/lead-todo-guided-configuration/td003-retry-timeline.png)
- [TD-004 five-day cycle](../../../output/playwright/lead-todo-guided-configuration/td004-five-day-cycle.png)
- [Active coordinated release binding](../../../output/playwright/lead-todo-guided-configuration/lead-release-active-binding.png)
- [Runtime TD-004 and next occurrence](../../../output/playwright/lead-todo-guided-configuration/lead-runtime-next-td004.png)
- [Runtime evidence JSON](../../../output/playwright/lead-todo-guided-configuration/runtime-evidence.json)

All required database, backend, frontend and browser gates passed. No unresolved Task 11
release blocker remains.

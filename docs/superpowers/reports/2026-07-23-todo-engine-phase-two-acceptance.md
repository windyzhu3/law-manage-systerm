# Todo Engine Phase 2 Acceptance

Date: 2026-07-24

Branch: `runtime/startup-wiring-fix`

Decision: PASS

## Measurable acceptance

| Requirement | Result | Evidence |
|---|---:|---|
| Simple configuration and simulation | 4.8 seconds | Real Chrome failed-simulation repair/save/rerun scenario. This is deterministic browser automation timing, not a separately timed human usability study, and is below the 10-minute target. |
| Active-event schema coverage | 36/36, 100% | Every active event is `READY`, has at least one schema property, and has a sample payload in the clean MySQL migration database. |
| DoD recipe coverage | 5/5, 100% | Active recipes cover the five governed canonical business types: LEAD, CUSTOMER, CONTRACT, CASE, and MATTER. V0.20.45 aligns recipe actions with real event types. |
| Payload auto-fill | 2/2 required fields, 100% | Server `PayloadHydration.coveragePercent()` result for `DEMO-L-001` and `LEAD_ASSIGNED@1`. For transparency, 6/16 total schema fields had values; the remaining ten are optional event-result fields entered only when that event is being simulated. |
| Simulation trace | 6/6, 100% | EVENT, OWNER, DOD, SLA, ROUTING, and TODO_PREVIEW are present in stable order. |
| Publish safety | PASS | Blocking issues disable publish; warnings require an explicit review reason; a current successful simulation hash is required. |
| Employee preview parity | PASS | Owner, SLA, required fields, materials, instructions, and task title are projected from the live draft; the fallback owner copy is Chinese. |
| Compatibility | PASS | Five legacy configuration routes load, role boundaries are enforced, and published versions remain immutable. |

## Verification matrix

| Layer | Command | Result |
|---|---|---|
| Backend reactor | `mvn test` | PASS, all 10 reactor modules; 49.224 s. `ruoyi-admin`: 118 tests, 0 failures/errors, 13 intentional external-database skips in the environment-free reactor run. |
| Focused regression | `mvn -pl law-todo "-Dtest=TodoConfigurationQueryServiceTest,TodoConfigurationBusinessObjectDirectoryTest" test` | PASS, 20/20. |
| Real MySQL migration | v0.15 baseline import, then `mvn -pl ruoyi-admin -am "-Dtest=FlywayMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS, 1/1 with no skip; 58 migrations validated and schema reached V0.20.45. |
| Frontend model and UX | `npm run test:todo-phase-two` | PASS, 42 model checks and 25 UX checks. |
| Frontend compatibility | `npm run test:todo-config`; `npm run test:todo`; `npm run test:encoding`; `npm run test:e2e:contract` | PASS. |
| Production frontend | `npm run build:prod` | PASS; two pre-existing bundle-size warnings only. |
| Real browser | `npx playwright test tests/e2e/todo-config-center.spec.js tests/e2e/todo-config-journey.spec.js --workers=1` with real backend/MySQL/Redis and Chrome | PASS, 8/8 in 25.1 s; no route mocks and no runtime task writes from sample simulation. |
| Cleanliness | `git diff --check` | PASS. |

The first full Maven run exposed a null `emptyReason` regression in sample fallback. The implementation was corrected with null-safe comparisons, the focused 20-test set passed, and the full reactor was rerun successfully.

## Visual evidence

- [1440×1024 implementation](../../../output/playwright/todo-phase-two-ux/1440x1024-implementation.png)
- [1440×1024 comparison](../../../output/playwright/todo-phase-two-ux/1440x1024-comparison.png)
- [1920×1080 implementation](../../../output/playwright/todo-phase-two-ux/1920x1080-implementation.png)
- [1920×1080 comparison](../../../output/playwright/todo-phase-two-ux/1920x1080-comparison.png)
- [Design QA report](2026-07-23-todo-engine-phase-two-design-qa.md)

Residual non-blocking risk: the Vue 2 production bundle remains large and should be handled as a later performance task; it is not a functional or phase-two design acceptance defect.

Phase 2 comprehensive hardening accepted with 0 unresolved P0/P1/P2 defects

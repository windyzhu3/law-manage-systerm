# Lead Todo acceptance harness safety implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the guided Lead Todo acceptance harness fail closed on process identity and preserve every controlled-failure and success proof in a unique, isolated evidence bundle.

**Architecture:** The PowerShell 5.1 harness records immutable process identities (PID plus creation timestamp and captured executable/command signature), derives descendants only from still-valid roots, and revalidates each identity immediately before termination. A generated or explicitly validated `runId` selects a child of the governed Playwright output root; the harness passes that exact directory to Playwright, and all lifecycle evidence remains inside that one run directory.

**Tech Stack:** Windows PowerShell 5.1, Node.js contract tests, Playwright, MySQL disposable schemas, existing Vue 2 application.

## Global constraints

- Preserve `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, existing `.playwright-cli/`, `.runtime-logs/`, root `output/` and `test-results/` artifacts.
- Never terminate a listener or PID without a current PID-plus-creation identity match derived from a valid registered root.
- `-ValidateOnly` and executable ownership self-tests perform no database, service, or artifact mutation.
- Evidence is ignored runtime output under `ruoyi-ui/output/playwright/lead-todo-guided-configuration/runs/<runId>/`; source commits contain no credentials or runtime bundles.

---

### Task 1: Immutable process ownership

**Files:**
- Modify: `ruoyi-ui/scripts/check-lead-todo-guided-acceptance-harness.js`
- Modify: `scripts/run-lead-todo-guided-acceptance.ps1`

**Interfaces:**
- Consumes: `Win32_Process` rows with PID, parent PID, creation time, name, executable path and command line.
- Produces: registered root identity objects, identity-safe descendant discovery, immediate process-stage registration and deepest-first identity-safe cleanup.

- [x] Add executable contract expectations for pre-minimum roots, reused PIDs, valid descendants, unowned listeners and pre-wait Playwright registration.
- [x] Run `npm run test:guided-acceptance-harness` and retain the expected failing assertion.
- [x] Replace PID-only roots with immutable identities and validate roots/descendants against the run start threshold.
- [x] Register backend and Playwright launchers immediately after `Start-Process`; re-read and compare identity immediately before every `Stop-Process`.
- [x] Run parser, ownership self-test, `-ValidateOnly`, and the npm contract with exit 0.

### Task 2: Isolated proof bundles

**Files:**
- Modify: `ruoyi-ui/scripts/check-lead-todo-guided-acceptance-harness.js`
- Modify: `ruoyi-ui/tests/e2e/todo-config-journey.spec.js`
- Modify: `scripts/run-lead-todo-guided-acceptance.ps1`

**Interfaces:**
- Consumes: optional safe `-RunId` and harness-provided `TODO_E2E_ARTIFACT_DIR`.
- Produces: one never-overwritten run directory containing manifest, stage logs, screenshots and runtime evidence.

- [x] Add failing contracts for unsafe run IDs, outside-root artifact paths, per-run routing and prior-bundle preservation.
- [x] Generate or validate a unique run ID, refuse existing run directories, and route all harness evidence through the selected run root.
- [x] Make the Playwright spec resolve only an explicit artifact directory contained by the governed output root.
- [x] Run the Node harness and E2E source contracts with exit 0.

### Task 3: Controlled failure, success proof and documentation

**Files:**
- Modify: `docs/superpowers/runbooks/lead-todo-guided-acceptance.md`
- Modify: `docs/superpowers/reports/2026-07-31-lead-todo-guided-configuration-acceptance.md`
- Modify: `docs/superpowers/reports/2026-08-06-lead-todo-final-review-round-two.md`

**Interfaces:**
- Consumes: a disposable MySQL/Redis environment and the exact `GUIDED_LEAD_` Playwright pair.
- Produces: a preserved controlled-failure run bundle and a separate successful 2/2 run bundle, each with independent cleanup proof.

- [x] Run a unique post-bind controlled failure and require harness exit 1 while process stop, fallback database drop, schema absence and both listener proofs exit 0.
- [x] Run one new unique full acceptance and require exactly 2/2, independent runtime requery, teardown, schema absence and both listener proofs with exit 0.
- [x] Confirm both run directories coexist, no database or listeners remain, and no service survives.
- [x] Document fresh reproduction, both ignored bundle paths/run IDs and exact numeric exits.
- [x] Run final parser, validation, ownership, Node contracts and `git diff --check`; commit only intended source and documentation.

### Task 4: Review round 1 ancestry and orphan safety

**Files:**
- Modify: `scripts/run-lead-todo-guided-acceptance.ps1`
- Modify: `ruoyi-ui/scripts/check-lead-todo-guided-acceptance-harness.js`

- [x] Add RED fixtures for stale child chronology, multi-level chronology,
  missing authorization metadata, a captured surviving orphan and an
  uncaptured orphan.
- [x] Require every ancestry edge to satisfy child creation time greater than
  or equal to its current parent, and require nonblank identity/signature data.
- [x] Capture immutable descendant identities while registered launchers run;
  clean captured identities even after their root exits or they are reparented.

### Task 5: Review round 1 atomic run lease and explicit E2E path

**Files:**
- Modify: `scripts/run-lead-todo-guided-acceptance.ps1`
- Modify: `ruoyi-ui/tests/e2e/support/guided-artifact-directory.js`
- Modify: `ruoyi-ui/tests/e2e/support/guided-artifact-directory.test.js`
- Modify: `.github/workflows/ci.yml`

- [x] Add RED tests for missing/shared/nested artifact paths and a duplicate
  run whose existing temporary fingerprint must remain unchanged.
- [x] Acquire a persistent `CreateNew` claim before creating a run directory;
  gate all cleanup and manifest writes on ownership.
- [x] Require an explicit exact `governed-root/runs/<safeRunId>` artifact path
  and provide it in the harness and CI.

### Task 6: Review round 1 fresh lifecycle proof

**Files:**
- Modify: `docs/superpowers/runbooks/lead-todo-guided-acceptance.md`
- Modify: `docs/superpowers/reports/2026-07-31-lead-todo-guided-configuration-acceptance.md`
- Modify: `docs/superpowers/reports/2026-08-06-lead-todo-final-review-round-two.md`

- [x] Run parser, ownership/lease self-tests, no-mutation validation, unsafe
  input tests, Node contracts and CI source contracts.
- [x] Preserve a new controlled post-bind failure bundle whose cleanup stages
  are all 0, then a separate final-code Chrome 2/2 success bundle.
- [x] Prove both schemas, both listeners and all recorded identities absent;
  update documentation and commit only owned source/docs.

### Task 7: Review round 2 whole-lifetime descendant capture

**Files:**
- Modify: `scripts/run-lead-todo-guided-acceptance.ps1`
- Modify: `ruoyi-ui/scripts/check-lead-todo-guided-acceptance-harness.js`
- Modify: `docs/superpowers/runbooks/lead-todo-guided-acceptance.md`
- Modify: `docs/superpowers/reports/2026-07-31-lead-todo-guided-configuration-acceptance.md`
- Modify: `docs/superpowers/reports/2026-08-06-lead-todo-final-review-round-two.md`

**Interfaces:**
- Consumes: every registered immutable root and either a live
  `Win32_Process` snapshot or a pure fixture snapshot.
- Produces: `Update-AllRegisteredRoots`, a no-output refresh used at every
  recorded-stage boundary and every generic external-process polling interval.

- [x] Add a RED source contract proving the generic polling loop refreshes all
  registered roots independently of `RegisterOwnedRoot`, without output
  pollution, and add a pure mid-poll child/reparent fixture.
- [x] Implement `Update-AllRegisteredRoots`; invoke it before and after both
  recorded-operation and external-process stages and on every external-process
  poll, preserving captured identities after root exit.
- [x] Run parser, ownership/validation and Node contracts, then preserve a new
  post-bind controlled failure and a separate final-code Chrome 2/2 bundle.
- [x] Prove claims, schemas, listeners and captured identities clean; update
  wording to whole-backend-lifetime capture and commit only owned source/docs.

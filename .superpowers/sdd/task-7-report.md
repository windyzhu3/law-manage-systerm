# Task 7 report — typed configuration-centre APIs

## RED evidence

- Added `TodoConfigurationControllerValidationTest` before the controller existed.
- Ran:
  `mvn -pl ruoyi-admin -am '-Dtest=TodoConfigurationControllerValidationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Observed the expected test-compilation failure: `TodoConfigurationController` was not found (six references), proving the requested API boundary did not exist.

## GREEN implementation

- Added typed `/todo/config/*` API boundary for dashboard, reusable SLA and DoD rules, template reads/writes, trigger rules, safe simulation, release reads/diff/publish/rollback.
- Every mutating API receives a typed command and uses the authenticated `SecurityUtils` actor; path/body identities are checked with `TODO_CONFIGURATION_PATH_BODY_MISMATCH`.
- Lists manually slice unpaged service results and never invoke PageHelper. Release records retain their mapper `limit/offset` semantics and are not PageHelper-wrapped.
- Added `V0_20_31__todo_trigger_rule_sort_order.sql`, typed `TriggerSortCommand`, transactional optimistic sort updates, idempotency ledger usage, and deterministic trigger ordering.
- Added the minimum query projection needed for release-record detail.
- Existing `/todo/template` controller mappings were not changed.

## Test evidence

Focused regression:

```text
mvn -pl ruoyi-admin -am '-Dtest=TodoConfigurationControllerValidationTest,TodoTemplateControllerValidationTest,TodoTriggerRuleSortServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
PASS — 14 tests (2 trigger-sort, 8 configuration-controller, 6 legacy-controller; Maven module totals include the focused suites)
```

Full backend regression:

```text
mvn -pl ruoyi-admin -am test
PASS — full reactor; ruoyi-admin reports 78 tests, 0 failures, 0 errors, 11 pre-existing environment-dependent skips
```

`git diff --check` passes.

## Self-review

- Configuration routes do not overlap the legacy `/todo/template` base path.
- Simulation only holds `TodoConfigurationSimulationService`; it has no runtime Todo creation/action dependency.
- Controller request-body parameters are typed command records, not raw `Map` bodies. The typed DoD sample wrapper owns its payload map internally.
- Existing immutable publish and rollback operations remain delegated to `TodoDefinitionService`.

## Concerns

- Real MySQL migration execution remains Task 14 work; this task adds the Flyway script and source contract only.
- Permission enforcement is supplied by existing Spring method-security infrastructure. Controller tests assert the exact V0_20_30 permissions on representative routes and validate HTTP request contracts in standalone MVC.

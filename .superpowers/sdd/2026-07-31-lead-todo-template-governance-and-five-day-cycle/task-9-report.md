# Task 9 Report: Seed and Verify the Recurring TD-004 Guided Draft

## Outcome

- Updated the canonical TD-004 definition to consume `LEAD_FIRST_CONTACT_VALID`, resolve the current `LEAD` business owner and require exactly `progressType`, `progressAt` and one `FOLLOWUP_PROOF` material.
- Added the governed `PROGRESS_RECORDED` outcome with `SCHEDULE_SELF`, target `TD-004` and action `REFRESH_FIVE_DAY_WINDOW`.
- Kept the ordinary routing graph terminal (`td004 -> end`). The next five-day Todo is created by the schedule effect, never by a graph self-loop or an independent TD-004 trigger.
- Added a forward-only Flyway migration that creates one new mutable TD-004 draft from the current published version. Published/current TD-004 history remains byte-for-byte unchanged.
- Bound the outcome target and task node to the newly generated draft version ID, canonicalized the definition, stored identical definition/compiled JSON and calculated the canonical SHA-256 hash.
- Seeded exactly three active required scenarios: successful progress, idempotent replay and the expected missing-proof failure.
- Made the existing `TD-004_COMPLETE` handler safely simulatable. Simulation emits only the server-owned `PROGRESS_RECORDED` routing value and never calls the progress-cycle persistence service.
- Added governed DoD validation to scenario execution so missing fields/materials are rejected by their real Todo error codes before the read-only routing simulation runs.

## Migration and Invariants

- Added `V0_20_77__SeedTd004GuidedDraft.java`. Version `0.20.76` is already owned by Task 8 on this branch; no migration at or below `V0_20_76` was edited.
- The migration fails closed unless exactly one current published TD-004 version and exactly one active `LEAD_PROGRESS_READY` recipe exist.
- The recipe must contain exactly the two governed fields, one governed attachment and empty validator/conditional collections. Missing, duplicate or extended values are rejected.
- The migration rejects any enabled independent trigger targeting TD-004.
- The packaged definition must declare the real `TD-004_COMPLETE` capability and the concrete runtime handler must implement that catalog code.
- The migration inserts a `DRAFT` only; it does not publish, replace `current_version`, activate a trigger or mutate simulation evidence.

## Governed Scenarios

1. `TD004_PROGRESS_RECORDED`: `PHONE`, fixed progress time, optional remark, `FOLLOWUP_PROOF`, expected `SCHEDULE_SELF/TD-004`.
2. `TD004_IDEMPOTENT_REPLAY`: `WECHAT`, fixed progress time, `FOLLOWUP_PROOF`, `replay: true`, expected `SCHEDULE_SELF/TD-004`.
3. `TD004_PROOF_REQUIRED`: valid progress fields without proof, expected validation failure `TODO_DOD_ATTACHMENT_MISSING`.

All three scenarios are `ACTIVE`, required for publish, edit only `progressType`, `progressAt` and `remark`, and reference completion node `td004` occurrence 1.

## TDD Evidence

- RED: canonical TD-004 contract tests failed because the owner was still `PAYLOAD/ownerId` and the definition had no recurring governed outcome.
- GREEN: the updated resource passed all 11 lead-template contract tests.
- RED: the migration test failed to compile while `V0_20_77__SeedTd004GuidedDraft` did not exist.
- GREEN: the migration contract suite passed 9 tests, including missing/duplicate recipe, malformed recipe and rogue-trigger fail-closed paths.
- RED: the handler simulation test reported `supportsSimulation=false`.
- GREEN: the handler returns `PROGRESS_RECORDED` with no produced graph task and performs no persistence interaction.
- RED: the real missing-proof scenario produced a null-path failure because scenario execution did not validate governed materials.
- GREEN: the scenario now returns the exact expected `TODO_DOD_ATTACHMENT_MISSING` result without entering the routing simulator; the positive scenario resolves `SCHEDULE_SELF/TD-004`.

## Verification

Final combined command:

`mvn -pl law-todo,ruoyi-system,ruoyi-admin -am '-Dtest=LeadTodoPublishedTemplateContractTest,V02PrdDefinitionManifestTest,TodoSimulationScenarioCatalogTest,TodoSimulationScenarioServiceTest,Td004GuidedDraftMigrationTest,LeadTemplateConfigurationMySqlIT,LeadProgressCycleServiceTest,LeadProgressHandoffTodoHandlerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

- Result: `BUILD SUCCESS`; 72 tests passed, 0 failures, 0 errors, 0 skips.
- `law-todo`: 53 tests passed.
- `ruoyi-system`: 9 tests passed.
- `ruoyi-admin`: 10 tests passed.
- The real MySQL integration test migrated an isolated MySQL 8 schema through `0.20.77`, verified the published/current TD-004 fingerprint remained unchanged, verified the canonical draft/hash/self-binding/terminal graph and verified all three scenario records.
- `git diff --check`: exit 0 before commit.

## Scope Notes

- Task 9 required small production changes outside the original file list: pure TD-004 simulation in the existing handler and governed DoD validation in the scenario service. These are necessary for the three required scenarios to execute against real behavior rather than mocked exceptions.
- No Task 10 coordinated-release work was performed.
- User-owned `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, browser/runtime artifacts and test-result output were preserved and excluded from the commit.

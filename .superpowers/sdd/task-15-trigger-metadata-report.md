# Task 15 — Trigger rule identity metadata

## Scope

Implemented the approved `rule_code` / `rule_name` conformance gap only. User-owned dirty paths were left untouched:

- `.superpowers/sdd/task-7-report.md`
- `ruoyi-ui/vue.config.js`
- `.runtime-logs/`

## RED evidence

1. `mvn -pl law-todo -Dtest=TodoTemplateServiceTest,TodoMapperXmlContractTest test`
   initially failed at test compilation because `TodoMapper.countTriggerRulesByCode`, the extended `TriggerCommand` constructor, and `ruleCode()` / `ruleName()` did not exist.
2. `node ruoyi-ui/scripts/check-todo-config-center.js`
   initially failed with `src/views/todo/config/trigger/index.vue missing source contract ruleName`.
3. After adding the concurrent-write regression, `mvn -pl law-todo -Dtest=TodoTemplateServiceTest test`
   failed as expected because `DuplicateKeyException` escaped rather than becoming `TODO_TRIGGER_CODE_DUPLICATE`.

## GREEN evidence

- `mvn -pl law-todo -Dtest=TodoTemplateServiceTest,TodoMapperXmlContractTest test`
  passed: 62 tests, 0 failures/errors/skips.
- `node ruoyi-ui/scripts/check-todo-config-center.js`
  passed: `todo configuration center contract ok`.
- `mvn -pl ruoyi-admin -am -Dtest=TodoTriggerRuleMetadataMigrationContractTest,FlywayMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  passed. The focused migration contract passed; `FlywayMigrationTest` was skipped because `TODO_MIGRATION_DB_URL` is not configured locally.
- `git diff --check` passed.

## Changes

- Added forward-only migration `V0_20_35__todo_trigger_rule_metadata.sql`: nullable additions, deterministic `TRIGGER_<id>` / `Trigger rule <id>` backfill, non-null conversion, and unique rule-code key.
- Updated Flyway version expectation and added a migration SQL contract for columns, backfill, constraints, and uniqueness.
- Extended trigger commands with validated identity fields and backward-compatible deterministic constructors.
- Persisted, listed, locked, searched, and duplicate-checked identity data; concurrent database uniqueness errors map to `TODO_TRIGGER_CODE_DUPLICATE`.
- Added trigger list name/code columns and keyword filtering; drawer now creates, edits, views, and copies identity fields. Copy clears identity/version, adds `-副本`, and proposes a valid `_COPY` code.
- Extended backend mapper/service and frontend contract tests.

## Commit

`fix(todo-config): add trigger rule identity metadata`

## Concerns

No local migration database was supplied, so full Flyway execution is covered by the CI quality gate; the focused migration contract and all available focused checks pass locally.

## Review hardening follow-up

### RED evidence

1. `node ruoyi-ui/scripts/check-todo-config-center.js` failed with `index.vue missing source contract sortLockedByKeyword`, proving that keyword-filtered rows could still enter the global sort workflow.
2. `mvn -pl law-todo -Dtest=TodoTemplateServiceTest test` failed: a `DuplicateKeyException` for `uk_todo_trigger_rule_binding` was incorrectly translated to `TODO_TRIGGER_CODE_DUPLICATE`.

### GREEN evidence

- `node ruoyi-ui/scripts/check-todo-config-center.js` passed after adding model coverage for keyword-gated sorting, drawer copy-name truncation, and explicit identity length rules.
- `mvn -pl law-todo -Dtest=TodoTemplateServiceTest test` passed: 47 tests, 0 failures/errors/skips. It covers code-key translation and preservation of non-code unique-constraint failures.
- `mvn -pl ruoyi-admin -am -Dtest=TodoTriggerRuleMetadataMigrationContractTest,HistoricalMigrationPreflightEndToEndTest,FlywayMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed. Four focused tests ran; three external-MySQL tests were skipped because `TODO_MIGRATION_DB_URL` is unset.

### Review fixes

- Sorting is disabled and warns clearly when a keyword is active; the shared sort model rejects nonblank keyword mutation so filtered rows never rewrite global positions.
- Copy-name generation now trims to 125 characters before adding the suffix, while the drawer has explicit 64/128 character validation rules.
- Only a duplicate reporting `uk_todo_trigger_rule_code` is translated to `TODO_TRIGGER_CODE_DUPLICATE`; binding-key collisions propagate for their normal handling.
- Added the externally gated MySQL E2E migration test: target `0.20.34`, seed pre-metadata trigger rows, migrate to `0.20.35`, and verify deterministic backfill, non-null columns, unique key, and terminal version.
- Updated the historical preflight external E2E terminal Flyway version to `0.20.35`.

### Follow-up commit

`fix(todo-config): harden trigger metadata migration`

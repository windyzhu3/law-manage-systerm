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

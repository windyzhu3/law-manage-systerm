# Task 9 report — SLA and completion-condition pages

## RED

1. Extended `ruoyi-ui/scripts/check-todo-config-center.js` before either page existed.
2. Ran `npm run test:todo-config` from `ruoyi-ui`.
3. Observed the expected failure: `missing src/views/todo/config/sla/index.vue`.

## GREEN

Implemented the five source-contract files:

- `src/views/todo/config/sla/index.vue`
- `src/views/todo/config/sla/SlaRuleDrawer.vue`
- `src/views/todo/config/sla/SlaTimeline.vue`
- `src/views/todo/config/dod/index.vue`
- `src/views/todo/config/dod/DodRuleDrawer.vue`

The completed flows use the typed configuration APIs, shared page/drawer shell, exact permissions, dictionary-backed fixed values, managed work calendars, and validator capability catalog.

- SLA: list/query/pagination, create/edit/copy, status-only toggle with action/version, persisted work-calendar calculation, and fixed 80/100/150 timeline.
- DoD: list/query/pagination, create/edit/copy, reference-aware status toggle, structured JSON requirements, defensive malformed-JSON preservation/repair, validator catalog references, and all returned sample-validation failures.

## Verification

All commands were run from `ruoyi-ui`:

```text
npm run test:todo-config  PASS
npm run test:todo          PASS
npm run test:todo-schema   PASS
npm run build:prod         PASS
```

`build:prod` emitted only the repository's existing bundle-size warnings. No compile errors occurred.

## Self-review

- No new `el-dialog`; operational UI uses `ConfigDetailDrawer`.
- Every mutation creates a unique `actionId`; update/toggle requests carry server-returned `expectedVersion`.
- SLA toggles send only status, action ID, and version.
- DoD rule JSON is never silently normalized when stored JSON is malformed: the raw content is shown, preserved, and must be explicitly repaired before save.
- Files are UTF-8 and are covered by the source-contract checker.
- Excluded pre-existing `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, and `.runtime-logs/` from this task.

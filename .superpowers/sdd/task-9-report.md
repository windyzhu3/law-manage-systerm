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

## Reviewer remediation

The reviewer found that list-level status controls opened the detail drawer rather than executing the permitted status-only mutation, and that persisted edit forms still exposed mutable status.

### RED

The configuration checker was extended with localized method assertions and negative fixtures. It then failed as intended with:

```text
src/views/todo/config/sla/index.vue row toggle must stop propagation and call toggleRow
```

### GREEN

- SLA and DoD list rows now call direct `toggleRow` handlers with per-row loading guards set before confirmation, unique action IDs, row versions, status-only payloads, and list reload after success.
- DoD row disable fetches the live reference count and displays it in the explicit confirmation before mutating.
- Persisted-rule drawers render status read-only; update payloads preserve `serverStatus` regardless of any client-side form mutation. Only the toggle path changes status.
- The checker now inspects the local toggle handler and persisted drawer payload contracts rather than relying on aggregate token matches; negative mutation fixtures prove those assertions reject old-style handlers.

The full verification set was re-run after these changes:

```text
npm run test:todo-config  PASS
npm run test:todo          PASS
npm run test:todo-schema   PASS
npm run build:prod         PASS (existing bundle-size warnings only)
git diff --check           PASS
```

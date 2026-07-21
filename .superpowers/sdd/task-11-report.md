# Task 11 completion report

## Outcome

Implemented the database-backed Todo template configuration list, summary view, and 78%-wide eight-step drawer for basic metadata, trigger, owner, SLA, DoD, routing, simulation, and version history.

## Frontend

- Added the paginated template list with dictionary filters, metrics, detail, create, edit, copy, import, simulation, publish, version, and dedicated optimistic toggle actions.
- Added the eight-step editor and a canonical draft model. Malformed canonical JSON fails closed with `TODO_DEFINITION_CANONICAL_INVALID`.
- Added nested SLA and DoD rule drawers with post-create list refresh and automatic selection.
- Added a schema-aware trigger condition builder and routing conditions. Downstream TASK nodes use a purpose-specific catalogue containing active templates and published versions only; END is the explicit termination node.
- Added real simulation gated by the same saved definition token and successful preflight. Dirty drafts cannot simulate an older persisted definition.
- Added version provenance/diff and a summary including trigger, owner, SLA, DoD, routing, version metadata, and Todo card preview.
- Added safe versioned JSON import. Import only creates a new template plus DRAFT; it cannot overwrite or publish.
- Create/copy preserves the complete unsaved draft when the aggregate identity is first persisted. Incremental Save Draft refreshes the list without closing the editor.

## Backend contracts

- Added typed DB pagination and aggregate detail projections for template management.
- Added template event, owner, handler, validator, auto-action, and published routing-target catalogues.
- Added audited/idempotent JSON import and dedicated audited/idempotent optimistic template toggle.
- Added a separate `TemplateMetadataCommand` with action ID and expected version plus a conditional metadata-only SQL update. The legacy `TemplateCommand` and legacy controller contract remain unchanged. Generic edit cannot change template status.
- Added publish preflight access for either release publishers or simulation users.
- Added migration `V0_20_31__todo_template_management_contract.sql` for template concurrency version/index and import/toggle permissions.

## Verification

- `npm run test:todo-config` — PASS.
- `node scripts/check-todo-definition-roundtrip.js` — PASS, no canonical round-trip difference.
- Targeted backend reactor — PASS: 76 law-todo tests and 11 controller tests.
- Final controller permission/compatibility reactor — PASS: 11 tests.
- `npm run build:prod` — PASS. Only the repository's existing asset and entrypoint size warnings remain.
- `git diff --check` — PASS (line-ending conversion notices only).

## Deliberately excluded

- No runtime Todo creation semantics were changed.
- No legacy `/todo/template` write contract was broken.
- No user-owned `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, or `.runtime-logs/` changes are included in the Task 11 commit.

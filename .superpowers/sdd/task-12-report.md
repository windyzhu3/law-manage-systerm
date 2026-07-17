# Task 12 Report: Frontend Schema Engine and Runtime Actions

## RED evidence

- Command: `cd ruoyi-ui && node scripts/check-todo-schema-runtime.js`
- Result: exit 1.
- Expected failure: `Error: missing runtime file: src/components/TodoDynamicForm/field-registry.js`.
- Why this was the correct RED: the runtime registry did not exist and the then-current `TodoActionDialogs.vue` still contained a hard-coded template-code schema catalog. The contract was added before production changes and also rejects template-code branching in the dialog, page integration, or runtime.

## Delivered behavior and files

- Added the exact field registry and executable schema runtime in `src/components/TodoDynamicForm/`.
- Added schema normalization, action-aware DoD validation, defaults/material hydration, stable `fields` payload creation, and strong deduplicated `fileObjectIds` extraction.
- Added `BusinessFilePicker.vue` using the governed `/files/register` then `/files/{uploadIntentId}/complete` flow, SHA-256 metadata, file object IDs, business relation metadata, material types, and no URL-only upload value.
- Added `TodoMaterialChecklist.vue` for required material counts, missing-state feedback, typed material uploads, and read-only detail rendering.
- Replaced the hard-coded action catalog in `TodoActionDialogs.vue` with `GET /todo/{id}/form` plus `TodoDynamicForm`. Failed action responses leave the dialog and entered state intact; state resets only for a new open session, while the successful page flow closes it.
- Added `TodoExtensionDialog.vue` using only `POST /todo/{id}/extension-requests` with `requestedDueAt`, `reason`, and `proofFileObjectIds`. It displays remaining-count, maximum-duration, and proof-rule policy slots and preserves input after rejection.
- Integrated runtime action submission and removed the legacy lead/template/file-URL branch in `src/views/todo/index.vue`. This file is beyond the brief's enumerated list but is required because it owns the real API submission; without it, the new `fields` and `fileObjectIds` output would be discarded.
- Integrated governed materials and the normal extension dialog in `TodoDetailDrawer.vue` while preserving existing permissions and detail aggregates.
- Added the form, extension, and file-center API functions to `src/api/todo.js`, including the controller-confirmed normal extension path (not `/todo/sla/...` and not the administrator waiver path).
- Updated `scripts/check-todo-ui.js` because its prior contract required the hard-coded template catalog and legacy `<file-upload>`, both of which Task 12 explicitly removes. Coverage was replaced with runtime integration assertions (`TodoDynamicForm`, `getTodoForm`, `createActionPayload`, `fileObjectIds`) plus a ban on template-code branching; the broader Todo UI checks remain intact.
- Added `test:todo-schema` to `package.json`.

## GREEN evidence

- Focused check: `cd ruoyi-ui && node scripts/check-todo-schema-runtime.js`
  - Exit 0: `todo schema runtime contract passed`.
  - The check validates the exact registry mappings, real named runtime exports and executable payload/validation behavior, component/API source contracts, strong file IDs, the exact extension endpoint, absence of waiver usage, and direct/indirect template-code branching rejection.
- Required acceptance chain (run through `cmd /c` because Windows PowerShell 5 cannot parse `&&`):
  - `cd ruoyi-ui && npm run test:encoding && npm run test:todo && node scripts/check-todo-schema-runtime.js && npm run build:prod`
  - Exit 0.
  - `test:encoding`: `All frontend source files are valid UTF-8.`
  - `test:todo`: `todo ui contract ok`.
  - Schema runtime: `todo schema runtime contract passed`.
  - Production build: `DONE Build complete.`
- `git diff --check`: exit 0; only Git line-ending conversion notices were printed.

## Build warnings

The production build compiled with two non-blocking pre-existing webpack performance warnings:

1. Several image/CSS/JS assets exceed the recommended 244 KiB asset-size limit.
2. The `app` entrypoint is approximately 1.94 MiB and exceeds the recommended entrypoint-size limit.

## Commit

- `07f962a582732690ce76e8e807de8bd594dde5ea`
- `feat(todo-ui): render runtime actions from schema`

## Concerns

- The current `GET /todo/{id}` and `GET /todo/{id}/form` responses do not expose the versioned extension policy's approved-use count or its `maxExtension*`/`proofRequired` summary. The dialog renders real values whenever those policy properties are supplied and otherwise shows an explicit policy placeholder rather than inventing limits; the server remains authoritative and rejects invalid requests without clearing user input. A future read-model/API addition is needed for exact remaining-count and maximum/proof values in today's detail view.

## Review-fix wave (post-commit 07f962a5)

### Findings closed

- File/material rendering now reads active file-center relations for the Todo's authoritative `businessType` and `businessId`, filters each relation through file-center read authorization, and returns current-version file names. `GET /todo/{id}` returns this governed `materials` aggregate and the form endpoint returns the same authorized material view.
- The form read model includes the authoritative business context and a versioned extension-policy projection: approved count, remaining request count, maximum duration, proof rule, pending-SLA mode, and current due time. The detail drawer and extension dialog use only this projection instead of inferring policy from an SLA snapshot.
- All runtime file/material/proof uploads now require the form's business context; the former `TODO`/todo-ID relation fallback is removed. The executable Node contract rejects that fallback and exercises the business-context helpers.
- Extension proof IDs are resolved through `TodoMaterialLookup` for the Todo's business context and authenticated actor, then must have the exact `EXTENSION_PROOF` material relation. A focused regression test verifies a readable but wrong material type is rejected before any extension request is inserted.

### Verification evidence

- Focused Maven command: `mvn -pl law-file,law-todo,ruoyi-admin test`
  - Exit 1 only because the selected partial reactor omitted `ruoyi-framework`, `ruoyi-quartz`, and `ruoyi-generator`; Maven consequently could not resolve those local `ruoyi-admin` dependencies from external repositories.
  - Before that dependency-resolution failure, `law-file` passed 32 tests and `law-todo` passed 297 tests, both with 0 failures, 0 errors, and 0 skipped.
- Full root command: `mvn test`
  - Exit 0. Full reactor totals: 508 tests, 0 failures, 0 errors, 7 skipped. `ruoyi-admin` ran 34 tests with 0 failures/errors and 7 skipped.
- Exact required frontend chain (via Windows command interpreter): `cmd /c "cd ruoyi-ui && npm run test:encoding && npm run test:todo && node scripts/check-todo-schema-runtime.js && npm run build:prod"`
  - Exit 0. Encoding check: valid UTF-8; Todo UI contract: passed; schema-runtime contract: passed; production build: complete.
  - The build emitted the two existing webpack performance warnings for oversized assets and the 1.94 MiB `app` entrypoint.
- `git diff --check`
  - Exit 0. Git emitted only LF-to-CRLF working-copy notices; no whitespace errors.
- Final diff/status review confirmed only the intended Task 12 review-fix files are present, including the new file-center material query service and its focused test. No untracked plan document was introduced.

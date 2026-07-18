# Foundation PRD Material E2E Plan

> **For Codex:** Execute on `v0.2-Foundation` with TDD. Automated material evidence may close only `PRD_MATERIAL_TYPE_E2E`; it must not approve or imitate the independent security review.

**Goal:** Exercise every distinct PRD-defined material type through the real MyBatis repository, local object storage, access policy, upload, preview, download, one-time token and access-audit pipeline, then make that executable evidence visible to G-05.

**Architecture:** A MySQL-backed integration test reads material types directly from `todo_prd_definition_catalog`, builds the production file repository and storage adapters, and executes the complete file journey for each distinct type. Forward migration `V0_20_22` changes only the E2E checklist source to `CONFIRMED`; `SECURITY_REVIEW_SIGNOFF` remains `NEEDS_REVIEW`, so G-05 stays blocked at 6/7.

## Tasks

- [x] Add a failing migration contract for `V0_20_22` and the 6/7 baseline.
- [x] Add a MySQL-backed file material E2E covering every PRD material type.
- [x] Assert upload bytes, preview/download disposition, token replay rejection and persisted audit outcomes.
- [x] Add forward-only `V0_20_22` and advance the terminal Flyway assertion.
- [x] Add the integration test to the migration CI job.
- [x] Update the G-05 frontend fixture and admission report to 6/7 without claiming security approval.
- [x] Run real MySQL, full backend, frontend contracts/build/E2E and commit locally without pushing.

# Foundation File Security Readiness Plan

> **For Codex:** Execute in `v0.2-Foundation` with TDD. Repository implementation and executable tests are authoritative; technical controls never substitute for an independent security review.

**Goal:** Make G-05 file-center security readiness machine-verifiable across backend, database and frontend without fabricating a security sign-off.

**Architecture:** Add a fixed, forward-migration security checklist. A read service combines repository source status with live MySQL table/column/index invariants. G-05 approval is rejected unless every control and required review artifact is ready. The Todo configuration center displays technical controls and outstanding review evidence read-only.

**Boundary:** No permission is granted, no security reviewer is assigned, and no G-05 evidence is approved by this implementation.

## Tasks

- [x] Add red service, migration-contract, approval-guard and API-permission tests.
- [x] Add `V0_20_19` fixed security checklist without changing file runtime data.
- [x] Compute object/version/relation, one-time relation-bound token, access audit and cleanup compensation readiness.
- [x] Keep PRD material-type E2E and independent security review as explicit source blockers.
- [x] Add a read-only G-05 endpoint and Todo configuration tab.
- [x] Add frontend contract and E2E coverage.
- [x] Run real MySQL migration/invariants and full backend/frontend gates.
- [x] Update the measured admission report and commit locally without pushing.

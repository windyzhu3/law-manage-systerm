# Foundation Finance Readiness Plan

> **For Codex:** Repository facts are authoritative. Do not invent receivable nodes, collection ownership, risk formulas, rounding, tax or refund policy.

**Goal:** Make G-06 finance/formula readiness machine-verifiable and visible while preserving Q-009/Q-012 and finance sign-off as hard blockers.

**Architecture:** A forward-only checklist migration plus a read service checks current fee-plan precision, missing node-fee columns, missing receivable/collection/refund tables and missing risk-fee schema. G-06 evidence approval consults the computed gate. A read-only Todo configuration tab separates technical schema gaps from decisions and sign-off.

## Tasks

- [x] Add TDD coverage for finance readiness, migration safety, approval guard and API permission.
- [x] Add `V0_20_20` checklist without changing finance facts or formulas.
- [x] Add backend live schema readiness and G-06 approval guard.
- [x] Add frontend finance-readiness visualization and E2E.
- [x] Run real MySQL and full backend/frontend verification.
- [x] Update measured admission evidence and commit locally without pushing.

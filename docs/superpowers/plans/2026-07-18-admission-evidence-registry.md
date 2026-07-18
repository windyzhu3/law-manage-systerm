# Foundation Admission Evidence Registry Implementation Plan

> **Repository authority:** implementation and readiness claims are based only on `v0.2-Foundation` code, Flyway state and automated verification.

**Goal:** Provide an auditable backend and frontend registry for the five still-missing non-decision admission artifacts (G-02, G-04, G-05, G-06 and G-07), without manufacturing business approvals.

**Architecture:** Add a fixed admission-evidence catalog through a forward-only migration. A dedicated application service and mapper expose optimistic, idempotent lifecycle updates. The Todo configuration center gains an evidence tab showing accountable owner, independent reviewer, deadline, artifact reference and approval state. An item can become approved only when an active reviewer performs the action and supplies traceable evidence and a conclusion.

**Tech Stack:** Java 17, Spring Boot, MyBatis, Flyway, MySQL 8, Vue 2, Element UI, Node contract tests and Playwright.

## Constraints

- Do not change G-02/G-04/G-05/G-06/G-07 to PASS merely because the registry exists.
- Seeded evidence items remain `OPEN`, unassigned and unapproved.
- `APPROVED` and `REJECTED` actions must be performed by the selected active reviewer.
- Evidence uses a repository/file artifact reference and a review conclusion; no free-form flag can bypass these requirements.
- Updates use an action id, request fingerprint and optimistic version.
- New permissions are independently assignable; the migration grants none automatically.

## Task 1: Domain contract and service (TDD)

- [x] Add command/view models for an evidence lifecycle update.
- [x] Add tests for accountability, reviewer authority, approval evidence, replay and version conflicts.
- [x] Implement `TodoAdmissionEvidenceService` and a dedicated mapper/XML.
- [x] Expose list, governance options and update endpoints with view/edit permissions.

## Task 2: Forward migration and live invariants

- [x] Add failing assertions for terminal Flyway version `0.20.16` and the five OPEN evidence rows.
- [x] Create evidence, evidence-action and permission records in a forward migration.
- [x] Prove the full chain on isolated MySQL, including unique action and evidence codes.

## Task 3: Frontend evidence registry

- [x] Add API functions and resource-contract validation.
- [x] Add an admission evidence tab with owner/reviewer/deadline/artifact/conclusion controls.
- [x] Show unassigned, overdue, in-review, approved and rejected states.
- [x] Add Playwright coverage for assign, submit, approve, reload and reviewer enforcement contracts.

## Task 4: Verification and reporting

- [x] Update the admission report to record the mechanism separately from actual approvals.
- [x] Run Maven, frontend contracts, UTF-8, production build and all E2E.
- [x] Inspect the diff and commit locally.

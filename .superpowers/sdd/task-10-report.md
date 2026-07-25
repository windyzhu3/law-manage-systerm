# Task 10 implementation report

Status: DONE

## Implemented

- Added the paginated trigger-rule list with real `rows`/`total`, derived source/action/mode presentation, condition summaries, view/edit/create drawers, guarded status-only toggles, named simulation handoff and explicit persisted sort flow.
- Added `TriggerRuleDrawer` using purpose-specific read-only event/template/version catalogs, event-derived payload version/business object/schema, published-template-version enforcement, server-enabled preservation, unique action IDs, optimistic versions, double-submit prevention and dirty-close protection.
- Added `TriggerConditionBuilder` for canonical `$expression.version=1`, including dictionary-driven operators, `NOT_EXISTS`, payload-schema fields, AND/OR predicates, raw JSON preservation/repair and malformed-input dirty tracking.
- Sorting uses a complete server snapshot: moves cross page boundaries, dirty pagination slices the snapshot, and one optimistic command sends every globally changed row with its server version.
- Historical inactive events remain visible as disabled history values; the editor explains the state and requires an active version before save.
- Trigger editor version lookup exposes only a published projection of `versionId`, `versionNo` and `status`; definition, compiled, governance, validation, schema and rule payloads are never returned by this endpoint.
- Saving or enabling a trigger requires its business type to equal the ACTIVE event catalog's `business_object_type`. Enabling also locks and reloads a minimal authoritative binding, then revalidates event/payload/schema, dictionary-enabled business type, active template, matching published template version and current condition before the status-only conditional update.
- Enabled create/update saves use a separate minimal template-version binding projection and reject inactive templates. Disabled draft rules may retain an inactive template while still requiring a matching published version.
- The drawer mirrors that policy: new enabled rules list only active templates, historical inactive selections remain visibly labelled, enabled saves are blocked, and disabled drafts can retain or select inactive templates.

## TDD evidence

- RED: the initial UI contract failed because the trigger configuration page did not exist; later contract REDs identified missing event business-object fallback and dirty-state propagation.
- RED: `ConditionEvaluatorTest` initially failed compilation because `NOT_EXISTS` did not exist in the runtime enum.
- RED: the first reviewer remediation checker failed because trigger-purpose catalog helpers/routes did not exist.
- RED: the second reviewer remediation tests failed compilation on missing `TriggerTemplateVersionCatalogView`, `selectPublishedTemplateVersionCatalog`, `listPublishedVersionCatalog` and `selectTriggerBindingForUpdate`.
- RED: save/re-enable mismatch tests initially failed because the backend trusted a dictionary-valid business type even when it disagreed with the ACTIVE event catalog.
- RED: final eligibility tests failed on the missing `selectTriggerTemplateBinding` projection, and the UI checker failed on missing `selectableTemplateCatalog`.
- GREEN: expression, trigger management, mapper projection, controller response-shape and permission tests pass.

## Verification

- `mvn -pl law-todo,ruoyi-admin -am test`: PASS.
- Targeted trigger service/mapper/controller tests: PASS.
- `mvn -pl ruoyi-admin -am -DskipTests "-Dspring-boot.repackage.skip=true" package`: PASS. Repackage replacement is skipped because local verification servers hold the executable jar open; all reactor modules still compile and package.
- `npm run test:todo-config`: PASS.
- `npm run test:todo`: PASS.
- `npm run test:todo-schema`: PASS.
- `npm run build:prod`: PASS with only the two existing bundle-size warnings.
- `git diff --check`: PASS.

## Round 5 reviewer remediation

### Mandatory verified capability for every fixture mutation

- `verifyBackendIdentity` now returns an opaque, frozen, null-prototype capability. Its backing state is held
  only in a module-private `WeakMap` and binds the exact frozen run context, authenticated backend request
  context, JDBC database, fixture marker and nonce.
- `setupLeadTodoFixtures`, `cleanupLeadTodoFixtures`, `withLeadTodoFixtures` and production file retirement all
  reject missing, forged, wrong-context or wrong-backend capabilities before reaching SQL or HTTP mutation.
- `withVerifiedLeadTodoFixtures` is the supported one-step lifecycle: authenticated HMAC/JDBC verification,
  fixture setup, caller work, scoped cleanup and zero-leak assertion.
- The Playwright suite retains the capability returned by the handshake and performs failure cleanup only after
  verification succeeded and setup was attempted.
- Negative tests invoke the complete lifecycle. Wrong database, missing backend and wrong backend produce zero
  SQL calls and zero file-retirement POST calls; an unverified or forged value cannot invoke setup or cleanup.

### Serialized file-object relation lifecycle

- File attachment and retirement now use one canonical database lock order: claim/replay the action where
  applicable, lock the `file_object` row, then lock and reload the complete active-relation set ordered by
  relation ID before deciding the mutation.
- Relation creation takes the same object lock and rejects disabled objects, preventing a late active relation
  from surviving a concurrent final retirement.
- Plain revocation of the final active relation is rejected with
  `FILE_LAST_RELATION_REQUIRES_RETIREMENT`. A final retirement with
  `retireObjectIfUnreferenced=false` is also rejected, so an active object cannot be stranded with zero active
  relations.
- Final object retirement remains owner-only. Shared-object retirement revokes only the authorized relation;
  the final owner retirement disables the object, persists lifecycle audit and exactly one cleanup job per
  physical version, then deletes physical storage only after transaction commit.
- A contender that waited for the object lock rechecks the action ledger before disabled-object validation, so
  an already committed winner is replayed. Insert-ignore losers reload the winning action with a locking current
  read, preserving exact-action convergence under MySQL repeatable-read isolation.
- The real-MySQL concurrency suite covers two different relations retired concurrently and a late attach racing
  final retirement. It asserts no active zero-relation object, no active relation to a disabled object, exact
  action/audit counts, one cleanup job per version and one physical deletion per object key.

### Round 5 RED/GREEN evidence

- RED: six capability-gate tests failed before the opaque capability was required by mutation helpers.
- GREEN: Lead E2E helper and MySQL runner tests pass, 8/8.
- RED on the pre-lock implementation: concurrent R1/R2 retirement left an ACTIVE object with zero active
  relations, and final retirement versus late attach left an active relation pointing at a disabled object.
- GREEN after object-row serialization plus an authoritative `FOR UPDATE` relation read: both real-MySQL races
  pass, 2/2, without deadlock.
- `FileObjectGovernanceReviewTest`: PASS, 22/22, including the action that becomes visible while waiting for the
  object lock.
- `FileObjectServiceTest`: PASS, 8/8.
- External-MySQL CI gate contract: PASS, 1/1; CI explicitly includes
  `FileObjectLockOrderExternalMysqlIT` when external credentials are available.
- Clean disposable MySQL migration from the v0.15 baselines through Flyway `0.20.54`: PASS.
- Full reactor `mvn test`: PASS across 10 modules. `law-file` ran 83 tests; `ruoyi-admin` ran 135 tests with zero
  failures/errors and 13 expected external-gate skips.
- Lead flow UI contract, dynamic-form contract and source UTF-8 validation: PASS.
- Lead E2E helper suite: PASS, 8/8; Playwright discovery finds exactly 10 substantive gated journeys.
- `npm --prefix ruoyi-ui run build:prod`: PASS with only the existing CSS-order and bundle-size warnings.
- Actual runtime proof used the current build on an independent port. The authenticated HMAC/JDBC identity
  handshake returned `runtime_round4_migration_e2e`; its opaque capability then gated setup of 9 owned leads and
  one assignment policy, followed by exact cleanup and the built-in zero-leak assertion. Temporary proof users
  and the independent process were removed without touching the user's existing PID 7116.
- `git diff --check`: PASS.

## Self-review

- Client toggle commands contain only `enabled`, `actionId` and `expectedVersion`; eligibility inputs are never trusted from the browser.
- Catalog and locked-binding SQL use explicit minimal columns and exclude definition/compiled JSON, owner/DoD/SLA/next/UI schemas and governance/draft data.
- A failed eligibility or optimistic-lock check rolls back the claimed action and never completes it as applied.
- No hard-coded event/template/operator choice list or direct trigger simulation mutation is present.

## Round 4 reviewer remediation

### E2E backend/database identity gate

- Added an authenticated `GET /foundation/e2e/backend-identity` handshake that is available only under the
  `test` or `e2e` Spring profile and only when `foundation.e2e-identity.enabled=true`.
- The endpoint requires the dedicated `foundation:e2e:identity` permission, a minimum-32-character shared
  secret, a per-run nonce and an exact fixture marker. It returns the actual JDBC catalog and schema, build
  version and an HMAC-SHA256 proof over all returned identity fields.
- The Playwright fixture helper verifies the HMAC, nonce, marker and exact configured E2E database name before
  any fixture setup or cleanup mutation. Wrong database identity, missing backend and wrong backend all fail
  closed with zero SQL fixture calls and zero file-retirement calls.
- The E2E suite now authenticates directly against the configured backend, performs the identity handshake in
  `beforeAll`, and never attempts failure cleanup unless that handshake succeeded.
- The identity permission remains inert in the migration. A profile/property-gated runtime seeder grants it to
  the sales test role only in the dedicated test/E2E process; production cannot expose the controller.

### Relation-scoped durable file retirement

- Replaced whole-object deletion semantics with an explicit retirement command containing `actionId`,
  `relationId` and `retireObjectIfUnreferenced`.
- Every request first authorizes the exact active relation. Shared objects revoke only the requested relation;
  they preserve all other relations, object metadata and physical versions.
- Retiring the final relation and object additionally requires object-owner authority. Relation revocation,
  action ledger, lifecycle audit, object disablement and one cleanup job per stored version commit atomically.
- Physical storage deletion is registered only after the durable database transaction commits. Metadata/job
  failure causes a rollback and no physical deletion.
- The action ledger stores an exact request fingerprint. Exact replay returns the durable winner without a
  second mutation; conflicting reuse is rejected; concurrent claims converge on the persisted winner.
- Cleanup/audit uniqueness constraints cover actor, action, object, relation/event and physical target. The E2E
  cleanup records exact fixture relation IDs and retires the object only after the final exclusive fixture
  relation, so unrelated sharing authority is never removed.

### Round 4 verification

- `FileObjectGovernanceReviewTest`: PASS, 18 tests including mixed relation authorization, partial metadata
  failure, exact replay, concurrent claim and multi-version after-commit deletion.
- E2E identity service/controller/permission-seeder tests: PASS.
- Lead E2E database helper tests: PASS, 6 tests including wrong database and wrong/missing backend zero-write
  assertions.
- Full reactor `mvn test`: PASS in the normal no-external-database environment.
- Clean disposable MySQL migration through `0.20.54`: PASS.
- `npm run test:lead-e2e-helpers`: PASS.
- Playwright real-suite discovery: exactly 10 gated tests.
- `npm run test:todo-lead-dynamic-form`: PASS.
- `npm run test:source-encoding`: PASS.
- `npm run build:prod`: PASS with only the existing CSS-order and bundle-size warnings.
- Actual runtime proof on an independent port: authenticated backend returned catalog/schema
  `runtime_round4_migration_e2e`, matching nonce/marker, build `3.9.2` and a valid 64-character HMAC proof.
  In the same process the verified gate preceded setup of 9 leads, 12 todos and one policy; scoped cleanup then
  passed the zero-leak assertion. Temporary proof identities were removed and the independent process was
  stopped without touching the user's existing server.
- `git diff --check`: PASS.

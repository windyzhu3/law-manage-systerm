# Task 12 Report — Simulation and Release Records

## Outcome

Implemented the two remaining Todo configuration-centre menu pages without introducing a second release ledger or a simulation write path:

- A real API-driven simulation page and 78% right drawer.
- Active event/payload-version selection, remotely paged template/version selection, typed Payload rows, a real remotely paged business-object picker, and effective time.
- A visible `只读模拟，不创建真实待办` guarantee.
- Ordered result sections: state, template, owner, SLA, DoD, next route, card preview, and technical log.
- Trigger-rule handoff consumes the routed `eventType` and `payloadVersion` and opens a prefilled drawer.
- Release records aggregate immutable version/action facts and include PUBLISHED and RETIRED history.
- Real template/status/publisher/date filters, paginated list, bounded full-query CSV export, version detail, rule snapshots, immutable-only semantic diff, copy-as-new-version, and rollback-as-new-draft.
- Copy and rollback only create new DRAFT versions; published history remains immutable.

## Minimal backend additions

- Added `status` and `publisher` to the typed release query.
- Kept retired immutable versions visible in release list/detail projections.
- Added an immutable release-version directory and a minimal read-only business-object directory for LEAD, CUSTOMER, CONTRACT, CASE, and MATTER.
- Copy and rollback now accept only an idempotency `actionId`; the backend serializes allocation with a template row lock and allocates the next version number itself.
- Reused the existing simulation facade, preflight gate, semantic diff, rollback, version ledger, and Foundation publish guards.

## Review follow-up hardening

- The simulation command now carries the exact `eventType + payloadVersion + businessType` contract and validates it against the same target definition snapshot and ACTIVE event catalog entry.
- `todo:simulation:list` can read only the template, event, version, and business-object catalogs required to configure a simulation; execution still requires `todo:simulation:simulate`.
- Release comparison and comparison choices are restricted to PUBLISHED/RETIRED versions at the server boundary.
- Release list defaults to offset `0`, limit `20`; the service clamps internal callers to `500`.
- CSV export walks every matching page, fails clearly on incomplete responses, and enforces a documented 10,000-row browser-export ceiling.
- Failed or timed-out copy/rollback requests retain the same action ID for a safe retry; successful requests rotate it.
- Release status rendering uses `law_todo_version_status`, and page-local metrics are labelled as current-page counts.

## Second review follow-up: data scope and dictionary repair

- Replaced the generic unscoped business-object mapper with an Actor-scoped `TodoBusinessDirectoryAccess` SPI.
- Added one RuoYi SQL adapter whose shared authorized source powers list, count, and direct lookup, so filtering occurs before pagination/projection and does not use N+1 checks.
- Covered LEAD, CUSTOMER, CONTRACT, CASE, and MATTER with the existing role/menu/data-scope semantics; CASE/MATTER personal scope includes owner, main lawyer, and assistant lawyer.
- The controller now supplies the authenticated Actor to directory reads, and simulation reuses the same Actor-scoped direct lookup before any definition execution.
- Unauthorized and nonexistent objects share the stable not-found response, preventing identity/count/name/number disclosure.
- Kept historical `V0_20_30` unchanged and added forward-only `V0_20_32` to seed/enable RETIRED idempotently and disable the incorrect ROLLED_BACK dictionary option.
- The release filter exposes only immutable PUBLISHED/RETIRED options while retaining DRAFT for other editor pages.

## TDD evidence

RED was observed before production changes:

1. Frontend contract failed with `missing src/views/todo/config/simulation/index.vue`.
2. Backend mapper contract failed because release history did not include retired versions or status/publisher filters.
3. Controller contract failed until the typed query and copy-draft endpoint existed.
4. Handoff contract failed until `$route.query` was consumed by the simulation page.
5. Retired-detail contract failed until `selectReleaseRecord` accepted immutable RETIRED versions.
6. The second review RED failed test compilation because no Actor-scoped directory SPI/signatures existed and the forward-only status repair migration was absent.
7. Focused GREEN now covers actor propagation, unauthorized list/count/lookup behavior, simulation denial before execution, all five business types, shared SQL scope, and the migration value-set contract.

## Verification

- `npm run test:todo-config` — PASS.
- `npm run test:todo-schema` — PASS.
- `npm run test:todo` — PASS.
- `node scripts/check-todo-definition-roundtrip.js` — PASS.
- `npm run build:prod` — PASS; only the repository's existing asset/entrypoint size warnings remain.
- `mvn -pl law-todo,ruoyi-admin -am "-Dtest=TodoDefinitionServiceTest,TodoConfigurationMapperXmlContractTest,TodoConfigurationControllerValidationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` — PASS (law-todo 34 tests; ruoyi-admin 12 tests).
- `mvn test` — PASS across the complete reactor.
- `git diff --check` — PASS (line-ending notices only).

## Remaining validation and risks

- Real MySQL, authenticated browser E2E, screenshot comparison, and visual QA remain Task 14.
- Browser CSV export is intentionally bounded to 10,000 matching records; larger exports must use a future asynchronous server export.
- The existing production build still reports pre-existing bundle-size warnings.
- User-owned `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, and `.runtime-logs/` were not modified or staged by this task.

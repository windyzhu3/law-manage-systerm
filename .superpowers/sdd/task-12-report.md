# Task 12 Report — Simulation and Release Records

## Outcome

Implemented the two remaining Todo configuration-centre menu pages without introducing a second release ledger or a simulation write path:

- A real API-driven simulation page and 78% right drawer.
- Active event/payload-version selection, template/version selection, typed Payload rows, business object ID, and effective time.
- A visible `只读模拟，不创建真实待办` guarantee.
- Ordered result sections: state, template, owner, SLA, DoD, next route, card preview, and technical log.
- Trigger-rule handoff consumes the routed `eventType` and `payloadVersion` and opens a prefilled drawer.
- Release records aggregate immutable version/action facts and include PUBLISHED and RETIRED history.
- Real template/status/publisher/date filters, paginated list, CSV export, version detail, rule snapshots, semantic diff, copy-as-new-version, and rollback-as-new-draft.
- Copy and rollback only create new DRAFT versions; published history remains immutable.

## Minimal backend additions

- Added `status` and `publisher` to the typed release query.
- Kept retired immutable versions visible in release list/detail projections.
- Added `/todo/config/release-records/{id}/copy-draft`, protected by `todo:template:copy`, as a configuration-centre adapter over the existing `TodoDefinitionService.copyVersion` behavior.
- Reused the existing simulation facade, preflight gate, semantic diff, rollback, version ledger, and Foundation publish guards.

## TDD evidence

RED was observed before production changes:

1. Frontend contract failed with `missing src/views/todo/config/simulation/index.vue`.
2. Backend mapper contract failed because release history did not include retired versions or status/publisher filters.
3. Controller contract failed until the typed query and copy-draft endpoint existed.
4. Handoff contract failed until `$route.query` was consumed by the simulation page.
5. Retired-detail contract failed until `selectReleaseRecord` accepted immutable RETIRED versions.

## Verification

- `npm run test:todo-config` — PASS.
- `npm run test:todo-schema` — PASS.
- `npm run test:todo` — PASS.
- `node scripts/check-todo-definition-roundtrip.js` — PASS.
- `npm run build:prod` — PASS; only the repository's existing asset/entrypoint size warnings remain.
- `mvn -pl law-todo,ruoyi-admin -am "-Dtest=TodoDefinitionServiceTest,TodoConfigurationMapperXmlContractTest,TodoConfigurationControllerValidationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` — PASS (law-todo 34 tests; ruoyi-admin 12 tests).
- `git diff --check` — PASS (line-ending notices only).

## Remaining validation and risks

- Real MySQL, authenticated browser E2E, screenshot comparison, and visual QA remain Task 14.
- CSV export intentionally uses the real filtered API and its maximum supported page size of 500 records; bulk asynchronous export is out of Task 12 scope.
- The existing production build still reports pre-existing bundle-size warnings.
- User-owned `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, and `.runtime-logs/` were not modified or staged by this task.

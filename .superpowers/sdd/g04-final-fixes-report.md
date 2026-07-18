# G-04 final review fix report

Date: 2026-07-19

Branch: `v0.2-Foundation`

Review base: `5b7d2bea`

Scope: five final-review findings plus the final generation-stage audit correction; local commits; no push

## Outcome and preserved invariants

All five original findings and the final audit lifecycle gap were implemented with an observed RED before production changes and a focused GREEN afterward. The final fresh MySQL/backend matrix is green; the backend-only audit correction did not change the already-green frontend. No default business line was selected, no historical case/Todo/governance state was changed, and no migration was added.

The measured admission truth remains unchanged:

- G-04 sources: `3 CONFIRMED + 1 NEEDS_DECISION + 4 NEEDS_EVIDENCE`.
- `G04-HISTORICAL-MIGRATION`: `OPEN`.
- Q-001: `OPEN`.
- All fixed admission evidence: 5 `OPEN`; Q-001 through Q-012: 12 `OPEN`.
- `biz_case.business_line`: absent.
- Foundation: `2/8 NOT_ADMITTED`.

## TDD evidence by finding

### 1. Complete deterministic Manifest contract

Test-first command used for RED and GREEN:

```text
mvn -pl law-todo -am "-Dtest=HistoricalMigrationExportArchiveWriterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RED: 5 tests ran and 1 failed. The archive's actual Manifest omitted `schemaVersion`, `gateCode`, and `fileName`, so the asserted complete key order/value contract failed.

GREEN: 5/5 passed after the writer emitted this exact `LinkedHashMap` order:

```text
schemaVersion, gateCode, fileName, rowCount, csvSha256, generatedAt,
classificationState, allowedBusinessLines
```

The unit test and real-MySQL E2E both assert the full order and exact fixed values (`1`, `G-04`, `historical-case-exceptions.csv`, `UNREVIEWED`, and the three allowed business lines).

Commit: `1f7dd1a8 fix(todo): complete G-04 export manifest`

### 2. Reject JSON/business-error Blob responses

RED commands:

```text
cd ruoyi-ui
npm run test:todo
node node_modules/@playwright/test/cli.js test e2e/todo-foundation-config-resources.spec.js --grep "historical migration JSON blob export failure"
```

RED evidence: the Node contract failed because the response unwrapping helper did not exist. The browser scenario timed out waiting for a local stable business error because an HTTP-200 JSON Blob was treated as a successful ZIP response.

GREEN commands:

```text
npm run test:todo
npm run build:prod
node node_modules/@playwright/test/cli.js test e2e/todo-foundation-config-resources.spec.js --grep "historical migration JSON blob export failure"
```

GREEN evidence: the contract and build passed; the focused failure scenario passed 1/1. The helper recognizes `application/json`, `text/json`, and `application/*+json`, decodes Blob/ArrayBuffer UTF-8, removes an optional BOM, parses RuoYi's logical response, and rejects a `HistoricalMigrationExportError` with stable `businessCode`, `message`, and response code. The component displays that error locally and never calls `saveAs` or populates `lastExport`; readiness and preflight remain visible. The global binary `returnFullResponse` opt-in behavior was not changed.

Commit: `211f3655 fix(ui): reject G-04 JSON export blobs`

### 3. Record the true deferred-stream audit result

Test-first command used for RED and GREEN:

```text
mvn -pl ruoyi-admin -am "-Dtest=HistoricalMigrationPreflightApiTest,RuoYiHistoricalMigrationExportAuditTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RED: test compilation failed because the focused audit collaborator and its transfer lifecycle did not yet exist.

GREEN: 6/6 passed. Controller tests use real input/output streams and close-aware artifacts to prove:

- no success record exists before the deferred body executes;
- success is recorded only after copy and artifact close complete;
- an output-stream failure records failure and preserves the original transfer failure;
- artifacts close on both success and failure;
- audit metadata/persistence failures do not mask a successful transfer or the original failed transfer.

The method-level `@Log` was removed. The final lifecycle now calls `HistoricalMigrationExportAudit.begin()` before archive generation, attaches the row count through `generated(rowCount)` only after an artifact exists, and records one terminal result. Generation rejection records synchronously; transfer success/failure retains deferred callback timing. The adapter uses the correctly encoded title `G-04历史迁移异常清单` and stores only row count/outcome plus a generic failure description—never case names, CSV/body, temporary paths, SQL, hashes, business codes, or exception details.

Commit: `fccd55c7 fix(todo): audit deferred G-04 exports`

### 4. Make cleanup retry effective in one close

Test-first command used for RED and GREEN:

```text
mvn -pl law-todo -am "-Dtest=HistoricalMigrationExportArchiveWriterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RED: 8 tests ran with 2 failures and 2 errors. Transient first/second deletion failures escaped the first production `close()`, permanent failure attempted deletion once rather than three times, and generation-failure cleanup did not use the requested bounded retry.

GREEN: 8/8 passed. ZIP and CSV cleanup now attempt deletion up to three times within one cleanup call, including generation failure. Exhaustion registers one `deleteOnExit` fallback and returns only the sanitized path-free export error. A successful close remains idempotent; repeated close after a permanent failure remains safe and does not duplicate fallback registration.

Commit: `e38faa01 fix(todo): retry G-04 artifact cleanup`

### 5. Canonicalize and revalidate the dedicated temporary root

The Windows tests explicitly skip only when the OS/filesystem cannot create a symbolic link. To obtain an active RED/GREEN signal for both symlink cases, the same suite was run in Linux:

```text
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-17 mvn -pl law-todo -am "-Dtest=HistoricalMigrationExportArchiveWriterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RED on Linux: 10 tests, 2 failures, 0 skips. Files were created through the lexical alias instead of the canonical root, and swapping the initialized root to a symlink targeting an outside directory was accepted.

GREEN on Linux: 10/10 passed, 0 skips. The writer creates the dedicated directory, caches its `toRealPath()` result, revalidates that canonical directory before each file creation, creates files only through the canonical path, and validates the created regular file's real path and direct real parent. A root target swap is rejected with the existing sanitized business error before any outside artifact is created.

Windows final result for this suite: 10 tests, 8 passed and 2 explicit capability-assumption skips.

Commit: `5f15b4f5 fix(todo): canonicalize G-04 export root`

### 6. Capture generation-stage export attempts

RED command:

```text
mvn -pl ruoyi-admin -am "-Dtest=HistoricalMigrationPreflightApiTest,RuoYiHistoricalMigrationExportAuditTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Behavioral RED: 9 tests ran with 3 failures and no skips. Both mocked `TODO_MIGRATION_EXPORT_FAILED` and unsupported-gate rejections observed zero audit attempts because `exports.export(gateCode)` ran before `audit.begin(rowCount)`. The adapter also exposed the internal sentinel as `rowCount=-1` instead of the sanitized `UNAVAILABLE`. Changing the tests to the desired attempt lifecycle then produced the expected compile RED because `Attempt`/`begin()`/`generated(rowCount)` did not exist.

GREEN command:

```text
mvn -pl ruoyi-admin -am "-Dtest=HistoricalMigrationPreflightApiTest,RuoYiHistoricalMigrationExportAuditTest,TodoHistoricalMigrationExportServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

GREEN: 15/15 passed with no failures, errors, or skips (`TodoHistoricalMigrationExportServiceTest` 5, controller 7, audit adapter 3). The controller now captures an attempt before generation. A generation exception invokes one failure record and rethrows the same `TodoException`; a generated artifact attaches its row count and retains deferred copy/close success/failure timing. An `AtomicBoolean` prevents duplicate terminal persistence. Metadata capture, row-count attachment, or log persistence failure is contained and cannot replace the generation/transfer outcome. Pre-generation logs use only `rowCount=UNAVAILABLE`, `outcome=FAILURE`, and `Historical migration export failed`.

Commit: `0b82a721 fix(todo): audit G-04 generation failures`

## Changed files

Production/backend:

- `law-todo/src/main/java/com/law/todo/application/HistoricalMigrationExportArchiveWriter.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/audit/HistoricalMigrationExportAudit.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/audit/RuoYiHistoricalMigrationExportAudit.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoHistoricalMigrationReadinessController.java`

Production/frontend:

- `ruoyi-ui/src/api/historical-migration-export-response.js`
- `ruoyi-ui/src/api/todo-definition.js`
- `ruoyi-ui/src/views/todo/config/components/HistoricalMigrationReadiness.vue`
- `ruoyi-ui/package.json`

Tests/contracts:

- `law-todo/src/test/java/com/law/todo/application/HistoricalMigrationExportArchiveWriterTest.java`
- `ruoyi-admin/src/test/java/com/ruoyi/web/audit/RuoYiHistoricalMigrationExportAuditTest.java`
- `ruoyi-admin/src/test/java/com/ruoyi/web/migration/HistoricalMigrationPreflightEndToEndTest.java`
- `ruoyi-admin/src/test/java/com/ruoyi/web/todo/HistoricalMigrationPreflightApiTest.java`
- `ruoyi-ui/scripts/check-historical-migration-export-response.js`
- `ruoyi-ui/e2e/todo-foundation-config-resources.spec.js`

Measured evidence/docs:

- `doc/v0.2-foundation-admission-report.md`
- `doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md`
- `docs/superpowers/plans/2026-07-18-g04-historical-migration-preflight.md`
- `.superpowers/sdd/g04-final-fixes-report.md`

## Final verification

### Fresh MySQL/backend

A disposable `mysql:8.4` container (`8.4.10`) was created on local port 13317. An initial container was discarded before testing when a PowerShell text pipe corrupted Chinese SQL bytes; the replacement was rebuilt from scratch and the exact eleven-file v0.15 baseline was imported byte-for-byte via `docker cp` plus an in-container redirect. The configured test run then migrated it through Flyway `0.20.27`.

Final audit-focused command:

```text
mvn -pl ruoyi-admin -am "-Dtest=HistoricalMigrationPreflightApiTest,RuoYiHistoricalMigrationExportAuditTest,TodoHistoricalMigrationExportServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Final audit-focused result:

- `law-todo`: 5 tests, 0 failures, 0 errors, 0 skips.
- `ruoyi-admin`: 10 tests, 0 failures, 0 errors, 0 skips.
- Full verification below executed `HistoricalMigrationPreflightEndToEndTest` and `FlywayMigrationTest` against the disposable database; neither skipped.

Full command:

```text
mvn clean verify
```

Surefire XML totals from 149 suites:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `law-file` | 72 | 0 | 0 | 0 |
| `law-business` | 28 | 0 | 0 | 0 |
| `law-todo` | 404 | 0 | 0 | 2 |
| `ruoyi-system` | 123 | 0 | 0 | 0 |
| `ruoyi-admin` | 52 | 0 | 0 | 0 |
| **Total** | **679** | **0** | **0** | **2** |

Thus 677 tests passed and the only two non-executions were the explicit Windows symlink capability assumptions; the equivalent supported Linux suite ran 10/10 with no skips.

Live database truth after verification:

```text
MySQL=8.4.10
Flyway=0.20.27
G-04 sources=CONFIRMED:3,NEEDS_DECISION:1,NEEDS_EVIDENCE:4
G-04 evidence=OPEN
Q-001=OPEN
open fixed evidence=5
open Q-001..Q-012 decisions=12
biz_case.business_line column count=0
Foundation=2/8 NOT_ADMITTED (asserted by real-DB E2E)
```

### Frontend/browser

The final audit correction is backend-only, so the frontend was not rerun as directed. The immediately preceding fix-wave evidence remains:

Commands:

```text
cd ruoyi-ui
npm run test:todo
npm run test:todo-schema
npm run test:encoding
npm run test:foundation-ci
npm run build:prod
node node_modules/@playwright/test/cli.js test e2e/todo-foundation-config-resources.spec.js --grep "historical migration"
npm run test:e2e
```

Results:

- Todo UI + export-response contracts: PASS.
- Todo Schema runtime contract: PASS.
- UTF-8 validation: PASS.
- Foundation CI contract: PASS.
- Production build: PASS with the pre-existing asset/entrypoint size warnings.
- Focused historical-migration Playwright: 4/4 passed.
- Full Playwright: 34/34 passed.

### Cleanup

The disposable database was removed after the live-truth query:

```text
docker stop foundation-g04-final-audit-20260719
docker rm foundation-g04-final-audit-20260719
docker ps -a --filter name=foundation-g04-final-audit-20260719
```

The final listing was empty. Linux Maven containers used `--rm`.

## Security and audit review

- Manifest key order and fixed identity fields prevent ambiguous or incomplete evidence metadata.
- JSON content-type detection occurs in the G-04 wrapper, preserving all existing binary callers and preventing a logical error payload from being downloaded as evidence.
- The attempt audit replaces premature aspect success, begins before archive generation, attaches row count only after artifact creation, records generation failure immediately or the actual deferred transfer/close outcome, is exactly-once per attempt, and deliberately excludes evidence content, SQL, exception details, and sensitive file metadata.
- Audit exceptions are contained so they cannot change export semantics; a warning remains in the server log for operational detection.
- Cleanup is bounded in the request path, sanitized, applies equally to generation and artifact close, and has a last-resort JVM-exit fallback.
- Canonical root validation rejects lexical-alias confusion and a detected link-target swap; files must be direct regular-file children of the canonical dedicated root.
- Real-DB snapshots prove the export path still does not mutate cases, Todo instances, G-04 requirements/evidence, Q-001, or the orphan-version invariant.

## Self-review

- Reviewed `git diff 5b7d2bea..HEAD` finding by finding and confirmed each implementation commit contains its production change plus the test that drove it.
- Parsed every fresh Surefire XML report rather than relying only on Maven's reactor summary.
- Confirmed the two Windows skips are named capability assumptions and reran the same suite on a supported Linux filesystem with zero skips.
- Confirmed the endpoint no longer has method-level `@Log`; generation failures are recorded after the generation attempt fails, while transfer success/failure remains inside the deferred callback, with no premature success.
- Confirmed `returnFullResponse` remains opt-in and the global Axios binary branch was not changed.
- Confirmed all public cleanup/root failures retain `TODO_MIGRATION_EXPORT_FAILED` and the path-free message `Historical migration export could not be generated`.
- Confirmed `git diff --check` is clean and documentation totals match fresh XML/Playwright output.
- Confirmed no push was performed.

## Residual concerns and blockers

1. Windows did not provide symbolic-link creation capability in this run, so two canonical-root cases are explicitly skipped there. The same 10-test suite passed without skips in Linux; CI should retain a Linux execution for this security boundary.
2. A permanently locked artifact may remain until JVM exit after all three deletion attempts fail. This is the intentional safe fallback; operations should monitor cleanup/audit warnings and the dedicated root.
3. Audit persistence failure is intentionally non-blocking. In that event the export outcome is preserved and the server warning is the operational signal.
4. Flyway reports that MySQL 8.4 is newer than its verified MySQL 8.1 compatibility range, although all migrations and real-DB tests passed. Dependency compatibility should remain monitored.
5. The production build retains existing bundle-size warnings.
6. G-04 remains blocked by the absent/default business-line decision (Q-001), signed target-data exception classification, forward/rollback rehearsal evidence, architecture/case/DBA review, and independent evidence approval. This fix wave does not satisfy or waive any of those business/governance requirements.

# Lead Todo guided acceptance harness

This runbook reproduces the two governed Lead Todo browser journeys on one
fresh database, one backend application and one Playwright invocation. The
harness is intended for release evidence, not for development data.

## Safety contract

- `TODO_E2E_DB_NAME` is mandatory and must match `^[A-Za-z0-9_]+_e2e$`.
- The database must not already exist. The harness queries
  `information_schema.schemata` and refuses to reuse or pre-drop it.
- MySQL credentials are read from environment variables. The password and the
  configuration-user password/hash are never included in the recorded command
  lines or manifest.
- The Redis endpoint must be disposable. The harness refuses to flush its
  selected Redis database unless `TODO_E2E_REDIS_DISPOSABLE=true` is set.
- Database removal uses the repository's guarded global teardown. A second
  information-schema query proves absence. A guarded fallback drop runs only
  if an earlier stage fails after this harness created the database.
- The backend is started exactly once by the harness. Its launcher PID and the
  Java listener PID are captured separately. Every registered root stores PID,
  `CreationDate`, name, executable path and command line immediately after
  `Start-Process` and before any wait. A PowerShell 5.1-compatible
  `Win32_Process` snapshot accepts a root only when it was created after the
  run began and its immutable identity still matches. Every parent-child edge
  must satisfy child creation time greater than or equal to the current parent,
  and every accepted identity requires nonblank name, executable path, command
  line and authorization signature. Descendants are captured continuously
  while a registered launcher runs. Cleanup may therefore stop an already
  captured child after its launcher exits or Windows reparents it, but it never
  authorizes a previously uncaptured orphan. Cleanup rereads each identity
  immediately before stopping it deepest-first. A missing PID is already
  stopped; a reused PID is refused and never killed.
- Both the configured backend and frontend ports must be unused before the
  run. Final durable evidence records each port and requires both listener
  counts to be zero. A remaining unowned listener fails closed with
  `UNOWNED_SERVICE_LISTENER` and is not killed.

Do not point these variables at a shared, staging or production database or
Redis instance.

## Fresh-checkout prerequisites

- JDK 17 and Maven
- Node.js 20 and npm
- Google Chrome available to Playwright
- a disposable MySQL 8 instance
- a disposable Redis 7 instance
- either local `mysql` / `redis-cli` clients, or Docker containers that expose
  those services

The harness installs frontend dependencies, builds the production frontend,
runs the Flyway migration test, packages the backend, and then starts both
applications needed by Playwright. It does not install Chrome.

Set the following variables in the PowerShell process. Values below are
examples; credentials must come from the local secret store or CI secrets and
must not be committed or pasted into logs.

```powershell
$env:TODO_E2E_DB_NAME = 'lead_todo_release_001_e2e'
$env:TODO_E2E_DB_HOST = '127.0.0.1'
$env:TODO_E2E_DB_PORT = '3306'
$env:TODO_E2E_DB_USER = 'root'
$env:TODO_E2E_DB_PASSWORD = '<secret>'

$env:TODO_E2E_REDIS_HOST = '127.0.0.1'
$env:TODO_E2E_REDIS_PORT = '6379'
$env:TODO_E2E_REDIS_DISPOSABLE = 'true'

$env:TODO_CONFIG_E2E_PASSWORD = '<test-login-secret-between-12-and-20-characters>'
$env:TODO_CONFIG_E2E_PASSWORD_HASH = '<matching-bcrypt-hash>'
$env:TODO_CONFIG_E2E_RUN_MARKER = 'release001'
$env:TODO_CONFIG_E2E_SLA_CODE = 'E2E_SLA_release001'
$env:TODO_CONFIG_E2E_DOD_CODE = 'E2E_DOD_release001'
$env:TODO_CONFIG_E2E_LEAD_NO = 'E2E_LEAD_release001'

$env:TODO_E2E_BROWSER = 'chrome'
$env:TODO_E2E_BACKEND_PORT = '8080'
$env:TODO_E2E_FRONTEND_PORT = '4173'
```

When local clients are absent, also identify the disposable containers:

```powershell
$env:TODO_E2E_MYSQL_CONTAINER = '<disposable-mysql-container>'
$env:TODO_E2E_REDIS_CONTAINER = '<disposable-redis-container>'
```

The Docker MySQL container is contacted on its internal port `3306`; the host
port remains `TODO_E2E_DB_PORT` for the backend. Do not echo the secret
variables while diagnosing a run.

## Validate and run

The validation mode checks the 14 required environment names, safety contract
and source files without creating directories, databases or processes. The
ownership self-test uses an in-memory process fixture and also performs no
mutation:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-lead-todo-guided-acceptance.ps1 -ValidateOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-lead-todo-guided-acceptance.ps1 -ProcessOwnershipSelfTest
npm --prefix ruoyi-ui run test:guided-acceptance-harness
```

Pass a unique, sanitized run ID for reproducible evidence naming. It accepts
only 1-64 ASCII letters, digits, `_` or `-`. `-ValidateOnly` validates the ID
and path without creating the directory or claim. A normal run first acquires
an OS-atomic `CreateNew` claim under `runs/.claims/<runId>.json`, then creates
the run directory. The claim is retained with the evidence. An existing claim
or concurrent claimant is refused before any run-directory or temporary-file
mutation, so no previous success or failure can be overwritten:

```powershell
$runId = 'release-20260806-001'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-lead-todo-guided-acceptance.ps1 -ValidateOnly -RunId $runId
```

For harness-maintainer verification only, setting
`TODO_E2E_HARNESS_FAIL_AFTER_BACKEND_BIND=true` injects one controlled failure
after the backend listener has been lineage-verified. It remains protected by
the same fresh `_e2e` database and disposable-Redis guards. The command must
exit 1, while the manifest must show the failure stage as 1 and
`services.stop-owned-process-tree`, `database.fallback-guarded-drop`,
`database.fallback-absence-proof` and `services.listener-absence` as 0. The
failure bundle also retains a sanitized backend log. Do not set this flag for
normal acceptance.

Run the full acceptance from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-lead-todo-guided-acceptance.ps1 -RunId $runId
```

The browser stage runs exactly:

```text
npx playwright test tests/e2e/todo-config-journey.spec.js --grep "GUIDED_LEAD_" --project=chromium --reporter=list
```

It must report exactly two passed tests and no failure or skip. After the
browser exits, a separate MySQL command rechecks the TD-004 completion,
next-Todo linkage, shared exact version, one fact/plan/occurrence/next Todo and
the 432000-second five-day offset.

## Evidence and CI retention

Each run writes to its own ignored directory:

```text
ruoyi-ui/output/playwright/lead-todo-guided-configuration/runs/<runId>/
```

The harness and CI set `TODO_E2E_ARTIFACT_DIR` explicitly to that exact path.
The Playwright spec has no shared-root fallback: it requires exactly the
governed root followed by `runs/<safeRunId>` and rejects a missing value, the
shared root, nested paths, traversal, external absolute paths and sibling
prefixes. The per-run bundle includes:

- `guided-lead-acceptance-manifest.json`
- `run-claim.json` (matching the persistent `.claims/<runId>.json` lease)
- `guided-lead-playwright-list.log`
- `runtime-evidence.json` and `runtime-requery.log`
- bootstrap, readiness, teardown and the backend/frontend listener proof
- the sanitized backend log and screenshots

The manifest gives every stable stage ID its sanitized command, UTC/local
start and end, numeric exit status, output path and non-secret details. These
files are intentionally ignored by Git and must never be staged or committed.

The harness creates and cleans only a run for which it owns the matching lease;
it never deletes or mixes older run directories. Duplicate/concurrent attempts
cannot change an existing bundle or its temporary fingerprint. CI may upload
the parent `lead-todo-guided-configuration` root so every run remains
independently inspectable.

GitHub Actions retains the same directory in the
`lead-todo-real-e2e-diagnostics` artifact. The broader configuration job also
uploads `ruoyi-ui/output/playwright` as
`todo-config-real-e2e-diagnostics`. CI additionally uploads
`ruoyi-ui/test-results` for the Lead Todo job.

## Review-round-1 reference proof (2026-08-06)

The final safety contract was exercised with two separate retained run leases:

- `20260806-r1-postbind-proof` intentionally exited 1 only at
  `harness.test-only-failure-after-backend-bind`. Owned-process cleanup,
  guarded fallback drop, independent schema-absence proof, both listener
  absence checks and sanitized failure-log capture all exited 0.
- `20260806-r1-guided-final4` exited 0 with all 32 stages at 0. Chrome passed
  both governed tests in one invocation (`34.3s`, `49.5s`, `1.4m` total), and
  the independent runtime row was
  `10 COMPLETED 92 11 CREATED 92 10 1 1 1 1 432000`.

Each `.claims/<runId>.json`, bundle `run-claim.json` and manifest contain the
same run ID and lease token. The final run recorded two roots and 72 immutable
identities across the backend and Playwright trees. After cleanup, every
recorded identity was absent, both disposable schemas were absent, and ports
8080 and 4173 had zero listeners. These ignored bundles are local diagnostic
evidence and must not be committed.

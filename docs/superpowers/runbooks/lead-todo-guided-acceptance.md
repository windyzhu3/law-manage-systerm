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
  Java listener PID are captured separately, then all owned PIDs are stopped
  and the listener absence is verified.

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

The validation mode parses the source contract and checks all required files
without creating directories, databases or processes:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-lead-todo-guided-acceptance.ps1 -ValidateOnly
npm --prefix ruoyi-ui run test:guided-acceptance-harness
```

Run the full acceptance from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-lead-todo-guided-acceptance.ps1
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

Local runtime evidence is written below
`ruoyi-ui/output/playwright/lead-todo-guided-configuration/`, including:

- `guided-lead-acceptance-manifest.json`
- `guided-lead-playwright-list.log`
- `runtime-evidence.json` and `runtime-requery.log`
- bootstrap, readiness, teardown and listener proofs
- the sanitized backend log and screenshots

The manifest gives every stable stage ID its sanitized command, UTC/local
start and end, numeric exit status, output path and non-secret details. These
files are intentionally ignored by Git and must never be staged or committed.

GitHub Actions retains the same directory in the
`lead-todo-real-e2e-diagnostics` artifact. The broader configuration job also
uploads `ruoyi-ui/output/playwright` as
`todo-config-real-e2e-diagnostics`. CI additionally uploads
`ruoyi-ui/test-results` for the Lead Todo job.

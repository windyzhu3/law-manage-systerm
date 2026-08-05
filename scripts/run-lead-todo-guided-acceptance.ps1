[CmdletBinding()]
param(
    [switch]$ValidateOnly,
    [switch]$ProcessOwnershipSelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$uiRoot = Join-Path $repoRoot 'ruoyi-ui'
$artifactRoot = Join-Path $uiRoot 'output\playwright\lead-todo-guided-configuration'
$manifestPath = Join-Path $artifactRoot 'guided-lead-acceptance-manifest.json'
$temporaryRoot = Join-Path $artifactRoot '.harness-temp'
$backendRawLog = Join-Path $temporaryRoot 'backend-raw.log'
$backendRawErrorLog = Join-Path $temporaryRoot 'backend-raw.err.log'
$backendEvidenceLog = Join-Path $artifactRoot 'guided-lead-backend.log'
$script:stages = New-Object System.Collections.ArrayList
$script:secrets = New-Object System.Collections.ArrayList
$script:databaseCreated = $false
$script:databaseDropped = $false
$script:ownedServicesStopped = $false
$script:serviceListenerAbsenceRecorded = $false
$script:backendLauncher = $null
$script:backendApplicationPid = $null
$script:playwrightLauncherPid = $null
$script:backendStartTimeUtc = $null
$script:ownedProcessRootPids = New-Object System.Collections.ArrayList
$script:failure = $null
$script:runStarted = Get-Date
$backendPort = 8080
$frontendPort = 4173

$baselineFiles = @(
    'sql/ry_20260417.sql',
    'sql/quartz.sql',
    'sql/lead_module_20260602.sql',
    'sql/lead_menu_20260602.sql',
    'sql/customer_contract_module_20260603.sql',
    'sql/customer_contract_dict_patch_20260611.sql',
    'sql/case_module_20260611.sql',
    'sql/matter_module_20260615.sql',
    'sql/matter_menu_patch_20260617.sql',
    'sql/finance_module_20260624.sql',
    'sql/customer_tag_assign_permission_fix_20260627.sql'
)

$plannedStageIds = @(
    'preflight.environment', 'preflight.ports', 'db.preflight-absence', 'db.create',
    'baseline.01-ruoyi', 'baseline.02-quartz', 'baseline.03-lead-module', 'baseline.04-lead-menu',
    'baseline.05-customer-contract', 'baseline.06-customer-contract-dict', 'baseline.07-case',
    'baseline.08-matter', 'baseline.09-matter-menu', 'baseline.10-finance', 'baseline.11-customer-tag-permission',
    'database.migrate', 'backend.package', 'frontend.dependencies', 'frontend.build',
    'backend.start', 'backend.readiness-before-bootstrap', 'fixture.bootstrap-once', 'fixture.bootstrap-proof',
    'redis.flush-disposable-db', 'backend.readiness-login', 'browser.guided-lead-2-of-2',
    'database.runtime-requery', 'database.global-teardown', 'database.absence-proof',
    'services.stop-owned-process-tree', 'services.listener-absence', 'evidence.backend-log'
)

function Get-IsoUtc([datetime]$value) { return $value.ToUniversalTime().ToString('o') }
function Get-IsoLocal([datetime]$value) { return $value.ToString('o') }

function Get-RelativeArtifactPath([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return $null }
    $full = [System.IO.Path]::GetFullPath($path)
    if ($full.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($repoRoot.Length).TrimStart('\').Replace('\', '/')
    }
    return $full
}

function Write-Utf8NoBom([string]$path, [string]$content) {
    $parent = Split-Path -Parent $path
    if ($parent -and -not (Test-Path $parent)) { [void](New-Item -ItemType Directory -Force -Path $parent) }
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
}

function Protect-Text([string]$value) {
    $safe = [string]$value
    foreach ($secret in $script:secrets) {
        if (-not [string]::IsNullOrEmpty([string]$secret)) { $safe = $safe.Replace([string]$secret, '[REDACTED]') }
    }
    return $safe
}

function Add-Secret([string]$value) {
    if (-not [string]::IsNullOrEmpty($value) -and -not $script:secrets.Contains($value)) {
        [void]$script:secrets.Add($value)
    }
}

function Add-StageResult(
    [string]$id,
    [string]$command,
    [datetime]$started,
    [datetime]$ended,
    [int]$exitStatus,
    [string]$outputPath,
    $details
) {
    $safeDetails = $details
    if ($null -ne $details) {
        $safeDetails = Protect-Text (($details | ConvertTo-Json -Depth 10 -Compress))
        try { $safeDetails = $safeDetails | ConvertFrom-Json } catch { }
    }
    [void]$script:stages.Add([pscustomobject]@{
        id = $id
        command = Protect-Text $command
        startUtc = Get-IsoUtc $started
        startLocal = Get-IsoLocal $started
        endUtc = Get-IsoUtc $ended
        endLocal = Get-IsoLocal $ended
        exitStatus = [int]$exitStatus
        outputPath = Get-RelativeArtifactPath $outputPath
        details = $safeDetails
    })
}

function Invoke-RecordedOperation(
    [string]$Id,
    [string]$Command,
    [string]$OutputPath,
    [scriptblock]$Action
) {
    $started = Get-Date
    try {
        $result = & $Action
        $ended = Get-Date
        if ($OutputPath) {
            $text = if ($result -is [string]) { $result } else { $result | ConvertTo-Json -Depth 10 }
            Write-Utf8NoBom $OutputPath ((Protect-Text $text).TrimEnd() + [Environment]::NewLine)
        }
        Add-StageResult $Id $Command $started $ended 0 $OutputPath $result
        Write-Host ("[PASS] {0}" -f $Id)
        return $result
    } catch {
        $ended = Get-Date
        $safeMessage = Protect-Text $_.Exception.Message
        if ($OutputPath) { Write-Utf8NoBom $OutputPath ($safeMessage + [Environment]::NewLine) }
        Add-StageResult $Id $Command $started $ended 1 $OutputPath @{ error = $safeMessage }
        throw
    }
}

function Invoke-ProcessStage(
    [string]$Id,
    [string]$Command,
    [string]$FilePath,
    [string[]]$ArgumentList,
    [string]$WorkingDirectory,
    [string]$OutputPath,
    [string]$InputPath
) {
    $started = Get-Date
    $stdoutPath = $OutputPath + '.stdout.tmp'
    $stderrPath = $OutputPath + '.stderr.tmp'
    Remove-Item -Force -ErrorAction SilentlyContinue $stdoutPath, $stderrPath
    try {
        $parameters = @{
            FilePath = $FilePath
            ArgumentList = $ArgumentList
            WorkingDirectory = $WorkingDirectory
            RedirectStandardOutput = $stdoutPath
            RedirectStandardError = $stderrPath
            WindowStyle = 'Hidden'
            Wait = $true
            PassThru = $true
        }
        if ($InputPath) { $parameters.RedirectStandardInput = $InputPath }
        $process = Start-Process @parameters
        $ended = Get-Date
        $stdout = if (Test-Path $stdoutPath) { Get-Content $stdoutPath -Raw } else { $null }
        $stderr = if (Test-Path $stderrPath) { Get-Content $stderrPath -Raw } else { $null }
        $combinedParts = @()
        if (-not [string]::IsNullOrWhiteSpace([string]$stdout)) { $combinedParts += ([string]$stdout).TrimEnd() }
        if (-not [string]::IsNullOrWhiteSpace([string]$stderr)) { $combinedParts += ([string]$stderr).TrimEnd() }
        $combined = Protect-Text ($combinedParts -join [Environment]::NewLine)
        Write-Utf8NoBom $OutputPath ($combined.TrimEnd() + [Environment]::NewLine)
        Add-StageResult $Id $Command $started $ended ([int]$process.ExitCode) $OutputPath @{ processId = $process.Id }
        if ($process.ExitCode -ne 0) { throw "Stage $Id exited with status $($process.ExitCode). See $(Get-RelativeArtifactPath $OutputPath)." }
        Write-Host ("[PASS] {0}" -f $Id)
        return [pscustomobject]@{ ExitCode = [int]$process.ExitCode; Output = $combined; ProcessId = $process.Id }
    } catch {
        $ended = Get-Date
        if (-not ($script:stages | Where-Object { $_.id -eq $Id })) {
            $safeMessage = Protect-Text $_.Exception.Message
            $safeStack = Protect-Text $_.ScriptStackTrace
            Write-Utf8NoBom $OutputPath ($safeMessage + [Environment]::NewLine + $safeStack + [Environment]::NewLine)
            Add-StageResult $Id $Command $started $ended 1 $OutputPath @{ error = $safeMessage; stack = $safeStack }
        }
        throw
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $stdoutPath, $stderrPath
    }
}

function Get-RequiredEnvironment([string]$name) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Required environment variable $name is missing." }
    return $value
}

function Assert-SafeDatabaseName([string]$database) {
    if ($database -notmatch '^[A-Za-z0-9_]+_e2e$') {
        throw "Refusing Todo acceptance operation for unsafe database name. It must match ^[A-Za-z0-9_]+_e2e$."
    }
}

function Assert-SafeFixtureCode([string]$name, [string]$value) {
    if ($value -notmatch '^[A-Za-z0-9_-]{3,64}$') { throw "$name contains unsupported characters or length." }
}

function Resolve-Executable([string[]]$names) {
    foreach ($name in $names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) { return $command.Source }
    }
    throw "Required executable was not found: $($names -join ', ')."
}

function Get-PortOwners([int]$port) {
    @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
}

function Get-Win32ProcessSnapshot {
    @(Get-CimInstance -ClassName Win32_Process -ErrorAction Stop | ForEach-Object {
        $creationUtc = $null
        if ($_.CreationDate) {
            try { $creationUtc = ([datetime]$_.CreationDate).ToUniversalTime() } catch { }
        }
        [pscustomobject]@{
            ProcessId = [int]$_.ProcessId
            ParentProcessId = [int]$_.ParentProcessId
            Name = [string]$_.Name
            CreationUtc = $creationUtc
        }
    })
}

function Get-OwnedProcessTree(
    [int[]]$RootPids,
    [object[]]$ProcessSnapshot,
    [Nullable[datetime]]$MinimumCreationUtc
) {
    $roots = @($RootPids | Where-Object { $_ -gt 0 } | Select-Object -Unique)
    if ($roots.Count -eq 0) { return @() }
    $snapshot = if ($null -eq $ProcessSnapshot -or $ProcessSnapshot.Count -eq 0) { @(Get-Win32ProcessSnapshot) } else { @($ProcessSnapshot) }
    $depthByPid = @{}
    foreach ($rootPid in $roots) { $depthByPid[[int]$rootPid] = 0 }
    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($processRow in $snapshot) {
            $pidValue = [int]$processRow.ProcessId
            $parentValue = [int]$processRow.ParentProcessId
            if (-not $depthByPid.ContainsKey($pidValue) -and $depthByPid.ContainsKey($parentValue)) {
                $creationAllowed = $true
                if ($null -ne $MinimumCreationUtc -and $processRow.CreationUtc) {
                    $creationAllowed = ([datetime]$processRow.CreationUtc) -ge ([datetime]$MinimumCreationUtc).AddSeconds(-2)
                }
                if ($creationAllowed) {
                    $depthByPid[$pidValue] = [int]$depthByPid[$parentValue] + 1
                    $changed = $true
                }
            }
        }
    }
    @($snapshot | Where-Object { $depthByPid.ContainsKey([int]$_.ProcessId) } | ForEach-Object {
        [pscustomobject]@{
            ProcessId = [int]$_.ProcessId
            ParentProcessId = [int]$_.ParentProcessId
            Name = [string]$_.Name
            CreationUtc = $_.CreationUtc
            Depth = [int]$depthByPid[[int]$_.ProcessId]
        }
    })
}

function Register-OwnedProcessRoot([int]$processId) {
    if ($processId -gt 0 -and -not $script:ownedProcessRootPids.Contains($processId)) {
        [void]$script:ownedProcessRootPids.Add($processId)
    }
}

function Assert-OwnedServiceListener([string]$serviceName, [int[]]$listenerPids, [int[]]$ownedPids, [int]$launcherPid) {
    $listeners = @($listenerPids | Select-Object -Unique)
    if ($listeners.Count -ne 1) {
        throw "UNOWNED_SERVICE_LISTENER: expected exactly one $serviceName listener descended from launcher PID $launcherPid; observed $($listeners -join ',')."
    }
    if (@($ownedPids) -notcontains [int]$listeners[0]) {
        throw "UNOWNED_SERVICE_LISTENER: $serviceName listener PID $($listeners[0]) is not descended from owned launcher PID $launcherPid."
    }
    return [int]$listeners[0]
}

function New-SqlInput([string]$name, [string]$sql) {
    $path = Join-Path $temporaryRoot $name
    Write-Utf8NoBom $path ($sql.Trim() + [Environment]::NewLine)
    return $path
}

function Invoke-MysqlStage(
    [string]$Id,
    [string]$DisplayCommand,
    [string]$InputPath,
    [string]$OutputPath,
    [string]$Database
) {
    $arguments = @()
    $filePath = $script:mysqlExecutable
    if ($script:mysqlMode -eq 'local') {
        $arguments += '--protocol=tcp'
        $arguments += ('-h' + $script:dbHost)
        $arguments += ('-P' + $script:dbHostPort)
        $arguments += ('-u' + $script:dbUser)
    } else {
        $arguments += @('exec', '-i', '-e', 'MYSQL_PWD', $script:mysqlContainer, 'mysql', '--protocol=tcp', '-h127.0.0.1', '-P3306', ('-u' + $script:dbUser))
    }
    $arguments += @('--batch', '--skip-column-names')
    if ($Database) { $arguments += $Database }
    Invoke-ProcessStage $Id $DisplayCommand $filePath $arguments $repoRoot $OutputPath $InputPath
}

function Parse-SingleTabRow([string]$path, [int]$columnCount) {
    $lines = @(Get-Content $path | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -ne 1) { throw "Expected exactly one database result row in $(Get-RelativeArtifactPath $path), got $($lines.Count)." }
    $columns = @($lines[0] -split "`t")
    if ($columns.Count -ne $columnCount) { throw "Expected $columnCount columns in $(Get-RelativeArtifactPath $path), got $($columns.Count)." }
    return $columns
}

function Stop-OwnedProcessTree {
    if ($script:ownedServicesStopped) { return }
    $started = Get-Date
    try {
        $stoppedPids = New-Object System.Collections.ArrayList
        $discoveredPids = New-Object System.Collections.ArrayList
        $listenerOwnership = @()
        for ($pass = 0; $pass -lt 3; $pass++) {
            $snapshot = @(Get-Win32ProcessSnapshot)
            $ownedTree = @(Get-OwnedProcessTree @($script:ownedProcessRootPids) $snapshot $script:backendStartTimeUtc)
            $ownedIds = @($ownedTree | Select-Object -ExpandProperty ProcessId)
            foreach ($ownedPid in $ownedIds) {
                if (-not $discoveredPids.Contains([int]$ownedPid)) { [void]$discoveredPids.Add([int]$ownedPid) }
            }
            if ($pass -eq 0) {
                foreach ($service in @(@{ name = 'backend'; port = $backendPort }, @{ name = 'frontend'; port = $frontendPort })) {
                    foreach ($listenerPid in @(Get-PortOwners ([int]$service.port))) {
                        $isOwned = $ownedIds -contains [int]$listenerPid
                        $listenerOwnership += [pscustomobject]@{ service = $service.name; port = [int]$service.port; processId = [int]$listenerPid; owned = [bool]$isOwned }
                        if ($service.name -eq 'backend' -and $isOwned) { $script:backendApplicationPid = [int]$listenerPid }
                    }
                }
            }
            $toStop = @($ownedTree | Where-Object { $_.ProcessId -ne $PID } | Sort-Object Depth -Descending)
            foreach ($ownedProcess in $toStop) {
                $process = Get-Process -Id ([int]$ownedProcess.ProcessId) -ErrorAction SilentlyContinue
                if ($process) {
                    Stop-Process -Id ([int]$ownedProcess.ProcessId) -Force -ErrorAction SilentlyContinue
                    if (-not (Get-Process -Id ([int]$ownedProcess.ProcessId) -ErrorAction SilentlyContinue) -and
                        -not $stoppedPids.Contains([int]$ownedProcess.ProcessId)) {
                        [void]$stoppedPids.Add([int]$ownedProcess.ProcessId)
                    }
                }
            }
            if ($toStop.Count -eq 0) { break }
            Start-Sleep -Milliseconds 500
        }
        $remainingTree = @(Get-OwnedProcessTree @($script:ownedProcessRootPids) @(Get-Win32ProcessSnapshot) $script:backendStartTimeUtc)
        if ($remainingTree.Count -ne 0) {
            throw "Owned process tree did not stop completely: $(@($remainingTree.ProcessId) -join ',')."
        }
        $script:ownedServicesStopped = $true
        $ended = Get-Date
        Add-StageResult 'services.stop-owned-process-tree' 'Get-CimInstance Win32_Process; Stop-Process <verified-owned-descendants-deepest-first>' $started $ended 0 $null @{
            roots = @($script:ownedProcessRootPids | ForEach-Object { [int]$_ })
            discoveredPids = @($discoveredPids | ForEach-Object { [int]$_ })
            stoppedPids = @($stoppedPids | ForEach-Object { [int]$_ })
            listenerOwnershipBeforeStop = $listenerOwnership
        }
        Write-Host '[PASS] services.stop-owned-process-tree'
    } catch {
        $ended = Get-Date
        Add-StageResult 'services.stop-owned-process-tree' 'Get-CimInstance Win32_Process; Stop-Process <verified-owned-descendants-deepest-first>' $started $ended 1 $null @{ error = Protect-Text $_.Exception.Message }
        throw
    }
}

function Assert-ServiceListenerAbsence([int]$backendPort, [int]$frontendPort) {
    if ($script:serviceListenerAbsenceRecorded) { return }
    $started = Get-Date
    $outputPath = Join-Path $artifactRoot 'service-listener-absence.json'
    $backendOwners = @(Get-PortOwners $backendPort)
    $frontendOwners = @(Get-PortOwners $frontendPort)
    $proof = [ordered]@{
        backendPort = $backendPort
        backendListenerCount = $backendOwners.Count
        backendListenerPids = @($backendOwners | ForEach-Object { [int]$_ })
        frontendPort = $frontendPort
        frontendListenerCount = $frontendOwners.Count
        frontendListenerPids = @($frontendOwners | ForEach-Object { [int]$_ })
    }
    Write-Utf8NoBom $outputPath (($proof | ConvertTo-Json -Depth 5) + [Environment]::NewLine)
    $script:serviceListenerAbsenceRecorded = $true
    if ($backendOwners.Count -ne 0 -or $frontendOwners.Count -ne 0) {
        Add-StageResult 'services.listener-absence' 'Get-NetTCPConnection <backend-and-frontend-ports> [expect zero listeners]' $started (Get-Date) 1 $outputPath $proof
        throw "UNOWNED_SERVICE_LISTENER: cleanup refuses to kill listeners that are not verified descendants; backend=$($backendOwners -join ',') frontend=$($frontendOwners -join ',')."
    }
    Add-StageResult 'services.listener-absence' 'Get-NetTCPConnection <backend-and-frontend-ports> [expect zero listeners]' $started (Get-Date) 0 $outputPath $proof
    Write-Host '[PASS] services.listener-absence'
}

function Write-Manifest([string]$status, [string]$database, [string]$marker) {
    if (-not (Test-Path $artifactRoot)) { [void](New-Item -ItemType Directory -Force -Path $artifactRoot) }
    $manifest = [ordered]@{
        schemaVersion = 1
        acceptance = 'GUIDED_LEAD_TEMPLATES + GUIDED_LEAD_RUNTIME'
        status = $status
        repository = $repoRoot
        database = $database
        runMarker = $marker
        harnessPid = [int]$PID
        backendLauncherPid = if ($script:backendLauncher) { [int]$script:backendLauncher.Id } else { $null }
        backendApplicationPid = if ($script:backendApplicationPid) { [int]$script:backendApplicationPid } else { $null }
        backendLauncherIsApplication = if ($script:backendLauncher -and $script:backendApplicationPid) { [int]$script:backendLauncher.Id -eq [int]$script:backendApplicationPid } else { $null }
        playwrightLauncherPid = $script:playwrightLauncherPid
        ownedProcessRootPids = @($script:ownedProcessRootPids | ForEach-Object { [int]$_ })
        servicePorts = @{ backend = $backendPort; frontend = $frontendPort }
        startUtc = Get-IsoUtc $script:runStarted
        startLocal = Get-IsoLocal $script:runStarted
        endUtc = Get-IsoUtc (Get-Date)
        endLocal = Get-IsoLocal (Get-Date)
        artifactPolicy = 'ignored runtime evidence; never stage or commit'
        credentialSources = @('TODO_E2E_DB_PASSWORD', 'TODO_CONFIG_E2E_PASSWORD', 'TODO_CONFIG_E2E_PASSWORD_HASH')
        stages = @($script:stages)
    }
    Write-Utf8NoBom $manifestPath (($manifest | ConvertTo-Json -Depth 12) + [Environment]::NewLine)
}

if ($ProcessOwnershipSelfTest) {
    $now = (Get-Date).ToUniversalTime()
    $fixture = @(
        [pscustomobject]@{ ProcessId = 100; ParentProcessId = 1; Name = 'launcher.exe'; CreationUtc = $now; Depth = 0 },
        [pscustomobject]@{ ProcessId = 101; ParentProcessId = 100; Name = 'java.exe'; CreationUtc = $now; Depth = 0 },
        [pscustomobject]@{ ProcessId = 102; ParentProcessId = 101; Name = 'worker.exe'; CreationUtc = $now; Depth = 0 },
        [pscustomobject]@{ ProcessId = 900; ParentProcessId = 1; Name = 'unowned.exe'; CreationUtc = $now; Depth = 0 }
    )
    $tree = @(Get-OwnedProcessTree @(100) $fixture $now.AddSeconds(-1))
    $ids = @($tree | Select-Object -ExpandProperty ProcessId)
    if ($ids.Count -ne 3 -or $ids -notcontains 100 -or $ids -notcontains 101 -or $ids -notcontains 102 -or $ids -contains 900) {
        throw 'Process ownership self-test failed ancestry isolation.'
    }
    $deepest = $tree | Where-Object ProcessId -eq 102
    if (-not $deepest -or [int]$deepest.Depth -ne 2) { throw 'Process ownership self-test failed depth calculation.' }
    $unownedRefused = $false
    try { [void](Assert-OwnedServiceListener 'fixture' @(900) $ids 100) } catch {
        $unownedRefused = $_.Exception.Message -like 'UNOWNED_SERVICE_LISTENER:*'
    }
    if (-not $unownedRefused) { throw 'Process ownership self-test failed unowned-listener refusal.' }
    [pscustomobject]@{ mode = 'ProcessOwnershipSelfTest'; mutationPerformed = $false; ownedPids = $ids; unownedPidExcluded = $true; unownedListenerRefused = $true; deepestFirstDepth = 2 } | ConvertTo-Json -Depth 4
    exit 0
}

if ($ValidateOnly) {
    $candidateDatabase = [Environment]::GetEnvironmentVariable('TODO_E2E_DB_NAME')
    if ([string]::IsNullOrWhiteSpace($candidateDatabase)) { $candidateDatabase = 'dry_run_validation_e2e' }
    Assert-SafeDatabaseName $candidateDatabase
    foreach ($relativePath in $baselineFiles + @(
        'ruoyi-ui/tests/e2e/bootstrap/todo-config-admin.sql',
        'ruoyi-ui/tests/e2e/todo-config-journey.spec.js',
        'ruoyi-ui/tests/e2e/support/todo-e2e-global-teardown.js'
    )) {
        if (-not (Test-Path (Join-Path $repoRoot $relativePath))) { throw "Required acceptance source is missing: $relativePath" }
    }
    [pscustomobject]@{
        mode = 'ValidateOnly'
        mutationPerformed = $false
        databaseGuard = $candidateDatabase
        requiredEnvironment = @(
            'TODO_E2E_DB_NAME', 'TODO_E2E_DB_HOST', 'TODO_E2E_DB_PORT', 'TODO_E2E_DB_USER', 'TODO_E2E_DB_PASSWORD',
            'TODO_E2E_REDIS_HOST', 'TODO_E2E_REDIS_PORT', 'TODO_E2E_REDIS_DISPOSABLE',
            'TODO_CONFIG_E2E_PASSWORD', 'TODO_CONFIG_E2E_PASSWORD_HASH', 'TODO_CONFIG_E2E_RUN_MARKER',
            'TODO_CONFIG_E2E_SLA_CODE', 'TODO_CONFIG_E2E_DOD_CODE', 'TODO_CONFIG_E2E_LEAD_NO'
        )
        plannedStages = $plannedStageIds
    } | ConvertTo-Json -Depth 5
    exit 0
}

$database = $null
$runMarker = $null
try {
    if (-not (Test-Path $artifactRoot)) { [void](New-Item -ItemType Directory -Force -Path $artifactRoot) }
    if (Test-Path $temporaryRoot) { Remove-Item -Recurse -Force $temporaryRoot }
    [void](New-Item -ItemType Directory -Force -Path $temporaryRoot)

    $database = Get-RequiredEnvironment 'TODO_E2E_DB_NAME'
    $script:dbHost = Get-RequiredEnvironment 'TODO_E2E_DB_HOST'
    $script:dbHostPort = Get-RequiredEnvironment 'TODO_E2E_DB_PORT'
    $script:dbUser = Get-RequiredEnvironment 'TODO_E2E_DB_USER'
    $dbPassword = Get-RequiredEnvironment 'TODO_E2E_DB_PASSWORD'
    $redisHost = Get-RequiredEnvironment 'TODO_E2E_REDIS_HOST'
    $redisPort = Get-RequiredEnvironment 'TODO_E2E_REDIS_PORT'
    $redisDisposable = Get-RequiredEnvironment 'TODO_E2E_REDIS_DISPOSABLE'
    $configurationPassword = Get-RequiredEnvironment 'TODO_CONFIG_E2E_PASSWORD'
    $configurationPasswordHash = Get-RequiredEnvironment 'TODO_CONFIG_E2E_PASSWORD_HASH'
    $runMarker = Get-RequiredEnvironment 'TODO_CONFIG_E2E_RUN_MARKER'
    $slaCode = Get-RequiredEnvironment 'TODO_CONFIG_E2E_SLA_CODE'
    $dodCode = Get-RequiredEnvironment 'TODO_CONFIG_E2E_DOD_CODE'
    $leadNo = Get-RequiredEnvironment 'TODO_CONFIG_E2E_LEAD_NO'
    $backendPort = [int]($(if ($env:TODO_E2E_BACKEND_PORT) { $env:TODO_E2E_BACKEND_PORT } else { '8080' }))
    $frontendPort = [int]($(if ($env:TODO_E2E_FRONTEND_PORT) { $env:TODO_E2E_FRONTEND_PORT } else { '4173' }))
    $browser = $(if ($env:TODO_E2E_BROWSER) { $env:TODO_E2E_BROWSER } else { 'chrome' })

    Add-Secret $dbPassword
    Add-Secret $configurationPassword
    Add-Secret $configurationPasswordHash
    Assert-SafeDatabaseName $database
    Assert-SafeFixtureCode 'TODO_CONFIG_E2E_RUN_MARKER' $runMarker
    Assert-SafeFixtureCode 'TODO_CONFIG_E2E_SLA_CODE' $slaCode
    Assert-SafeFixtureCode 'TODO_CONFIG_E2E_DOD_CODE' $dodCode
    Assert-SafeFixtureCode 'TODO_CONFIG_E2E_LEAD_NO' $leadNo
    if ($script:dbHostPort -notmatch '^\d{1,5}$' -or [int]$script:dbHostPort -lt 1 -or [int]$script:dbHostPort -gt 65535) { throw 'TODO_E2E_DB_PORT is invalid.' }
    if ($redisPort -notmatch '^\d{1,5}$' -or [int]$redisPort -lt 1 -or [int]$redisPort -gt 65535) { throw 'TODO_E2E_REDIS_PORT is invalid.' }
    if ($redisDisposable -ne 'true') { throw 'Refusing Redis cache flush unless TODO_E2E_REDIS_DISPOSABLE=true explicitly identifies a disposable Redis instance.' }
    if ($configurationPasswordHash -notmatch '^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$') { throw 'TODO_CONFIG_E2E_PASSWORD_HASH must be a BCrypt hash.' }
    if ($configurationPassword.Length -lt 12 -or $configurationPassword.Length -gt 20) { throw 'TODO_CONFIG_E2E_PASSWORD must contain 12 to 20 characters because RuoYi rejects longer login passwords before BCrypt validation.' }
    if ($browser -ne 'chrome') { throw 'TODO_E2E_BROWSER must be chrome for this acceptance.' }
    if ($frontendPort -ne 4173) { throw 'TODO_E2E_FRONTEND_PORT must be 4173 because the checked-in Playwright base URL is fixed.' }

    $javaExecutable = Resolve-Executable @('java.exe', 'java')
    $mavenExecutable = Resolve-Executable @('mvn.cmd', 'mvn')
    $npmExecutable = Resolve-Executable @('npm.cmd', 'npm')
    $npxExecutable = Resolve-Executable @('npx.cmd', 'npx')
    $nodeExecutable = Resolve-Executable @('node.exe', 'node')
    $localMysql = Get-Command mysql.exe, mysql -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localMysql) {
        $script:mysqlMode = 'local'
        $script:mysqlExecutable = $localMysql.Source
        $script:mysqlContainer = $null
    } else {
        $script:mysqlMode = 'docker'
        $script:mysqlExecutable = Resolve-Executable @('docker.exe', 'docker')
        $script:mysqlContainer = Get-RequiredEnvironment 'TODO_E2E_MYSQL_CONTAINER'
    }
    $localRedis = Get-Command redis-cli.exe, redis-cli -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localRedis) {
        $redisMode = 'local'
        $redisExecutable = $localRedis.Source
        $redisContainer = $null
    } else {
        $redisMode = 'docker'
        $redisExecutable = Resolve-Executable @('docker.exe', 'docker')
        $redisContainer = Get-RequiredEnvironment 'TODO_E2E_REDIS_CONTAINER'
    }
    $env:MYSQL_PWD = $dbPassword

    Invoke-RecordedOperation 'preflight.environment' 'validate required environment, tools, sources and safety guards' (Join-Path $artifactRoot 'preflight-environment.json') {
        foreach ($relativePath in $baselineFiles + @(
            'ruoyi-ui/tests/e2e/bootstrap/todo-config-admin.sql',
            'ruoyi-ui/tests/e2e/todo-config-journey.spec.js',
            'ruoyi-ui/tests/e2e/support/todo-e2e-global-teardown.js'
        )) {
            if (-not (Test-Path (Join-Path $repoRoot $relativePath))) { throw "Required acceptance source is missing: $relativePath" }
        }
        return [ordered]@{
            database = $database
            mysqlMode = $script:mysqlMode
            redisMode = $redisMode
            browser = $browser
            baselineCount = $baselineFiles.Count
            credentialValuesLogged = $false
        }
    } | Out-Null

    Invoke-RecordedOperation 'preflight.ports' 'Get-NetTCPConnection <backend-and-frontend-ports>' (Join-Path $artifactRoot 'preflight-ports.json') {
        $backendOwners = @(Get-PortOwners $backendPort)
        $frontendOwners = @(Get-PortOwners $frontendPort)
        if ($backendOwners.Count -ne 0 -or $frontendOwners.Count -ne 0) {
            throw "Acceptance requires unused ports. Backend owners=$($backendOwners -join ','); frontend owners=$($frontendOwners -join ',')."
        }
        return @{ backendPort = $backendPort; backendListenerCount = 0; frontendPort = $frontendPort; frontendListenerCount = 0 }
    } | Out-Null

    $preflightSql = New-SqlInput 'db-preflight.sql' "select count(*) from information_schema.schemata where schema_name='$database';"
    $preflightOutput = Join-Path $artifactRoot 'db-preflight-absence.log'
    Invoke-MysqlStage 'db.preflight-absence' 'mysql [credential-env] information_schema < db-preflight.sql' $preflightSql $preflightOutput $null | Out-Null
    $preflightRow = @(Parse-SingleTabRow $preflightOutput 1)
    if ([int]$preflightRow[0] -ne 0) { throw "Refusing to reuse or drop pre-existing database $database. Choose a new disposable _e2e name." }

    $createSql = New-SqlInput 'db-create.sql' "create database ``$database`` character set utf8mb4 collate utf8mb4_unicode_ci;"
    Invoke-MysqlStage 'db.create' 'mysql [credential-env] < create-disposable-database.sql' $createSql (Join-Path $artifactRoot 'db-create.log') $null | Out-Null
    $script:databaseCreated = $true

    $baselineStageIds = @(
        'baseline.01-ruoyi', 'baseline.02-quartz', 'baseline.03-lead-module', 'baseline.04-lead-menu',
        'baseline.05-customer-contract', 'baseline.06-customer-contract-dict', 'baseline.07-case',
        'baseline.08-matter', 'baseline.09-matter-menu', 'baseline.10-finance', 'baseline.11-customer-tag-permission'
    )
    for ($index = 0; $index -lt $baselineFiles.Count; $index++) {
        $source = Join-Path $repoRoot $baselineFiles[$index]
        $log = Join-Path $artifactRoot (($baselineStageIds[$index] -replace '\.', '-') + '.log')
        Invoke-MysqlStage $baselineStageIds[$index] ("mysql [credential-env] $database < " + $baselineFiles[$index]) $source $log $database | Out-Null
    }

    $jdbcUrl = "jdbc:mysql://$($script:dbHost):$($script:dbHostPort)/${database}?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    $env:TODO_MIGRATION_DB_URL = $jdbcUrl
    $env:TODO_MIGRATION_DB_USER = $script:dbUser
    $env:TODO_MIGRATION_DB_PASSWORD = $dbPassword
    Invoke-ProcessStage 'database.migrate' 'mvn --batch-mode --no-transfer-progress -pl ruoyi-admin -am -Dtest=FlywayMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test' $mavenExecutable @('--batch-mode', '--no-transfer-progress', '-pl', 'ruoyi-admin', '-am', '-Dtest=FlywayMigrationTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test') $repoRoot (Join-Path $artifactRoot 'database-migrate.log') $null | Out-Null
    Invoke-ProcessStage 'backend.package' 'mvn --batch-mode --no-transfer-progress -DskipTests package' $mavenExecutable @('--batch-mode', '--no-transfer-progress', '-DskipTests', 'package') $repoRoot (Join-Path $artifactRoot 'backend-package.log') $null | Out-Null
    Invoke-ProcessStage 'frontend.dependencies' 'npm install --legacy-peer-deps --no-audit --no-fund' $npmExecutable @('install', '--legacy-peer-deps', '--no-audit', '--no-fund') $uiRoot (Join-Path $artifactRoot 'frontend-dependencies.log') $null | Out-Null
    Invoke-ProcessStage 'frontend.build' 'npm run build:prod' $npmExecutable @('run', 'build:prod') $uiRoot (Join-Path $artifactRoot 'frontend-build.log') $null | Out-Null

    $jarPath = Join-Path $repoRoot 'ruoyi-admin\target\ruoyi-admin.jar'
    if (-not (Test-Path $jarPath)) { throw "Backend package is missing: $jarPath" }
    $tokenSecret = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
    Add-Secret $tokenSecret
    $env:SPRING_PROFILES_ACTIVE = 'e2e,druid'
    $env:SERVER_PORT = [string]$backendPort
    $env:DB_URL = $jdbcUrl
    $env:DB_USERNAME = $script:dbUser
    $env:DB_PASSWORD = $dbPassword
    $env:REDIS_HOST = $redisHost
    $env:REDIS_PORT = $redisPort
    $env:TOKEN_SECRET = $tokenSecret
    $env:FOUNDATION_E2E_IDENTITY_ENABLED = 'false'
    $env:FOUNDATION_E2E_IDENTITY_SECRET = ''
    $env:FOUNDATION_TEST_IDENTITIES_ENABLED = 'false'
    $env:FOUNDATION_TEST_USER_PASSWORD = ''
    $env:LAW_FILE_STORAGE_ROOT = Join-Path $artifactRoot 'file-center'
    $env:FLYWAY_ENABLED = 'true'
    Remove-Item -Force -ErrorAction SilentlyContinue $backendRawLog, $backendRawErrorLog
    $backendStart = Get-Date
    $script:backendStartTimeUtc = $backendStart.ToUniversalTime()
    try {
        $script:backendLauncher = Start-Process -FilePath $javaExecutable -ArgumentList @('-jar', ('"' + $jarPath + '"')) -WorkingDirectory $repoRoot -RedirectStandardOutput $backendRawLog -RedirectStandardError $backendRawErrorLog -WindowStyle Hidden -PassThru
        Register-OwnedProcessRoot ([int]$script:backendLauncher.Id)
        $startSnapshot = @(Get-Win32ProcessSnapshot)
        $harnessDescendants = @(Get-OwnedProcessTree @([int]$PID) $startSnapshot $script:runStarted.ToUniversalTime())
        if (@($harnessDescendants | Where-Object { $_.ProcessId -eq [int]$script:backendLauncher.Id }).Count -ne 1) {
            throw 'Backend launcher is not a verified descendant of the acceptance harness.'
        }
        Add-StageResult 'backend.start' 'java -jar ruoyi-admin/target/ruoyi-admin.jar [configuration via environment]' $backendStart (Get-Date) 0 $backendEvidenceLog @{
            harnessPid = [int]$PID
            backendLauncherPid = [int]$script:backendLauncher.Id
            launcherOwnershipVerified = $true
        }
        Write-Host '[PASS] backend.start'
    } catch {
        Add-StageResult 'backend.start' 'java -jar ruoyi-admin/target/ruoyi-admin.jar [configuration via environment]' $backendStart (Get-Date) 1 $backendEvidenceLog @{ error = Protect-Text $_.Exception.Message }
        throw
    }

    Invoke-RecordedOperation 'backend.readiness-before-bootstrap' 'GET /captchaImage and inspect listener owner' (Join-Path $artifactRoot 'backend-readiness-before-bootstrap.json') {
        $deadline = (Get-Date).AddSeconds(180)
        $lastError = $null
        while ((Get-Date) -lt $deadline) {
            $snapshot = @(Get-Win32ProcessSnapshot)
            $ownedTree = @(Get-OwnedProcessTree @([int]$script:backendLauncher.Id) $snapshot $script:backendStartTimeUtc)
            $ownedIds = @($ownedTree | Select-Object -ExpandProperty ProcessId)
            $owners = @(Get-PortOwners $backendPort)
            if ($owners.Count -gt 0) {
                $script:backendApplicationPid = Assert-OwnedServiceListener 'backend' $owners $ownedIds ([int]$script:backendLauncher.Id)
                try {
                    $response = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/captchaImage" -TimeoutSec 5
                    return @{
                        ready = $true
                        listenerCount = 1
                        backendLauncherPid = [int]$script:backendLauncher.Id
                        backendApplicationPid = [int]$script:backendApplicationPid
                        listenerOwnershipVerified = $true
                        captchaEndpointReached = ($null -ne $response)
                    }
                } catch { $lastError = $_.Exception.Message }
            }
            if ($script:backendLauncher.HasExited -and $ownedTree.Count -eq 0) {
                $script:backendLauncher.WaitForExit()
                throw "Owned backend process tree exited before readiness with launcher status $($script:backendLauncher.ExitCode)."
            }
            Start-Sleep -Seconds 2
        }
        throw "Backend readiness timed out. Last error: $lastError"
    } | Out-Null

    if ($env:TODO_E2E_HARNESS_FAIL_AFTER_BACKEND_BIND -eq 'true') {
        Invoke-RecordedOperation 'harness.test-only-failure-after-backend-bind' 'throw controlled harness-only failure after verified backend listener binding' (Join-Path $artifactRoot 'controlled-failure-after-backend-bind.log') {
            Assert-SafeDatabaseName $database
            throw 'CONTROLLED_HARNESS_FAILURE_AFTER_BACKEND_BIND'
        } | Out-Null
    }

    $bootstrapTemplate = Get-Content (Join-Path $repoRoot 'ruoyi-ui\tests\e2e\bootstrap\todo-config-admin.sql') -Raw
    $bootstrapSql = $bootstrapTemplate.Replace('TODO_CONFIG_E2E_PASSWORD_HASH', $configurationPasswordHash).
        Replace('TODO_CONFIG_E2E_DATABASE', $database).
        Replace('TODO_CONFIG_E2E_RUN_MARKER', $runMarker).
        Replace('TODO_CONFIG_E2E_SLA_CODE', $slaCode).
        Replace('TODO_CONFIG_E2E_DOD_CODE', $dodCode).
        Replace('TODO_CONFIG_E2E_LEAD_NO', $leadNo)
    $bootstrapPath = New-SqlInput 'bootstrap-rendered.sql' $bootstrapSql
    Invoke-MysqlStage 'fixture.bootstrap-once' 'mysql [credential-env] < tests/e2e/bootstrap/todo-config-admin.sql [rendered once in ephemeral file]' $bootstrapPath (Join-Path $artifactRoot 'bootstrap-once.log') $database | Out-Null

    $proofSql = New-SqlInput 'bootstrap-proof.sql' @"
select '$runMarker',
  (select count(*) from sys_user where user_name='todo_config_admin' and create_by='$runMarker' and del_flag='0'),
  (select count(*) from sys_role where role_key='todo_config_admin' and create_by='$runMarker' and del_flag='0'),
  (select count(*) from biz_lead where lead_no='$leadNo' and create_by='$runMarker' and del_flag='0'),
  (select count(*) from todo_sla_rule where rule_code='$slaCode' and create_by='$runMarker'),
  (select count(*) from todo_dod_rule where rule_code='$dodCode' and create_by='$runMarker');
"@
    $proofOutput = Join-Path $artifactRoot 'bootstrap-proof.log'
    Invoke-MysqlStage 'fixture.bootstrap-proof' 'mysql [credential-env] < bootstrap-proof.sql' $proofSql $proofOutput $database | Out-Null
    $proof = @(Parse-SingleTabRow $proofOutput 6)
    if ($proof[0] -ne $runMarker -or @($proof[1..5] | Where-Object { [int]$_ -ne 1 }).Count -ne 0) {
        throw "Bootstrap proof failed for marker $runMarker."
    }

    if ($redisMode -eq 'local') {
        Invoke-ProcessStage 'redis.flush-disposable-db' 'redis-cli [disposable] FLUSHDB' $redisExecutable @('-h', $redisHost, '-p', $redisPort, 'FLUSHDB') $repoRoot (Join-Path $artifactRoot 'redis-flush.log') $null | Out-Null
    } else {
        Invoke-ProcessStage 'redis.flush-disposable-db' 'docker exec <disposable-redis> redis-cli FLUSHDB' $redisExecutable @('exec', $redisContainer, 'redis-cli', 'FLUSHDB') $repoRoot (Join-Path $artifactRoot 'redis-flush.log') $null | Out-Null
    }

    Invoke-RecordedOperation 'backend.readiness-login' 'GET /captchaImage; POST /login [credential body redacted]' (Join-Path $artifactRoot 'backend-readiness-login.json') {
        $captcha = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/captchaImage" -TimeoutSec 10
        $loginBody = @{ username = 'todo_config_admin'; password = $configurationPassword; code = ''; uuid = '' } | ConvertTo-Json
        $login = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/login" -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 15
        if ([bool]$captcha.captchaEnabled) { throw 'Disposable bootstrap did not disable captcha.' }
        if ([int]$login.code -notin @(0, 200) -or [string]::IsNullOrWhiteSpace([string]$login.token)) { throw 'Readiness login did not return a successful code and non-empty token.' }
        return @{ captchaEnabled = $false; loginCode = [int]$login.code; tokenPresent = $true; tokenLogged = $false }
    } | Out-Null

    $env:TODO_E2E_REAL_BACKEND = 'true'
    $env:TODO_E2E_DROP_DATABASE_AFTER = 'false'
    $env:TODO_E2E_DB_NAME = $database
    $env:TODO_E2E_DB_HOST = $script:dbHost
    $env:TODO_E2E_DB_USER = $script:dbUser
    $env:TODO_E2E_DB_PASSWORD = $dbPassword
    $env:TODO_E2E_DB_PORT = $(if ($script:mysqlMode -eq 'docker') { '3306' } else { $script:dbHostPort })
    $env:TODO_E2E_CONTAINER_DB_HOST = '127.0.0.1'
    $env:TODO_E2E_MYSQL_CONTAINER = $script:mysqlContainer
    $env:TODO_E2E_BACKEND_URL = "http://127.0.0.1:$backendPort"
    $env:TODO_E2E_FRONTEND_PORT = [string]$frontendPort
    $env:TODO_E2E_BROWSER = 'chrome'
    $env:TODO_CONFIG_E2E_PASSWORD = $configurationPassword
    $env:TODO_CONFIG_E2E_RUN_MARKER = $runMarker
    $env:TODO_CONFIG_E2E_SLA_CODE = $slaCode
    $env:TODO_CONFIG_E2E_DOD_CODE = $dodCode
    $env:TODO_CONFIG_E2E_LEAD_NO = $leadNo
    $env:TODO_E2E_FILE_STORAGE_ROOT = Join-Path $artifactRoot 'file-center'

    $playwrightDisplay = 'npx playwright test tests/e2e/todo-config-journey.spec.js --grep "GUIDED_LEAD_" --project=chromium --reporter=list'
    $playwrightLog = Join-Path $artifactRoot 'guided-lead-playwright-list.log'
    $playwright = Invoke-ProcessStage 'browser.guided-lead-2-of-2' $playwrightDisplay $npxExecutable @('playwright', 'test', 'tests/e2e/todo-config-journey.spec.js', '--grep', 'GUIDED_LEAD_', '--project=chromium', '--reporter=list') $uiRoot $playwrightLog $null
    $script:playwrightLauncherPid = [int]$playwright.ProcessId
    Register-OwnedProcessRoot $script:playwrightLauncherPid
    if ($playwright.Output -notmatch '(?m)^\s*2 passed\b' -or $playwright.Output -match '(?m)^\s*\d+ (failed|skipped)\b') {
        throw 'Playwright list output did not prove exactly 2 passed with zero failed or skipped tests.'
    }

    $runtimeEvidencePath = Join-Path $artifactRoot 'runtime-evidence.json'
    if (-not (Test-Path $runtimeEvidencePath)) { throw 'Runtime evidence JSON is missing after Playwright.' }
    $runtimeEvidence = Get-Content $runtimeEvidencePath -Raw | ConvertFrom-Json
    $cycleEvidence = $runtimeEvidence.TD004_PROGRESS_RECORDED_NEXT_TD004
    if (-not $cycleEvidence -or @($cycleEvidence.todos).Count -ne 2) { throw 'Runtime evidence does not contain the two TD-004 Todos.' }
    $firstTodoId = [long]$cycleEvidence.todos[0].todoId
    $nextTodoId = [long]$cycleEvidence.todos[1].todoId
    if ($firstTodoId -le 0 -or $nextTodoId -le 0) { throw 'Runtime evidence contains unsafe Todo identifiers.' }
    $requerySql = New-SqlInput 'runtime-requery.sql' @"
select first_todo.todo_id,first_todo.status,first_todo.template_version_id,
  next_todo.todo_id,next_todo.status,next_todo.template_version_id,next_todo.previous_todo_id,
  (select count(*) from biz_lead_followup where source_todo_id=$firstTodoId),
  (select count(*) from todo_schedule_plan where previous_todo_id=$firstTodoId and schedule_purpose='LEAD_PROGRESS_5D'),
  (select count(*) from todo_schedule_occurrence where todo_id=$nextTodoId),
  (select count(*) from todo_instance where todo_id=$nextTodoId and previous_todo_id=$firstTodoId and template_code='TD-004'),
  (select timestampdiff(second,plan.first_contact_at,window_row.due_at)
    from todo_schedule_plan plan join todo_schedule_window window_row on window_row.plan_id=plan.plan_id and window_row.window_code='P5D'
    where plan.previous_todo_id=$firstTodoId and plan.schedule_purpose='LEAD_PROGRESS_5D' limit 1)
from todo_instance first_todo join todo_instance next_todo on next_todo.todo_id=$nextTodoId
where first_todo.todo_id=$firstTodoId;
"@
    $requeryOutput = Join-Path $artifactRoot 'runtime-requery.log'
    Invoke-MysqlStage 'database.runtime-requery' 'mysql [credential-env] < runtime-requery.sql [Todo ids from runtime-evidence.json]' $requerySql $requeryOutput $database | Out-Null
    $runtimeRow = @(Parse-SingleTabRow $requeryOutput 12)
    if ($runtimeRow[1] -ne 'COMPLETED' -or $runtimeRow[4] -ne 'CREATED' -or $runtimeRow[2] -ne $runtimeRow[5] -or [long]$runtimeRow[6] -ne $firstTodoId) { throw 'Independent runtime requery failed TD-004 status, version or linkage assertions.' }
    if (@($runtimeRow[7..10] | Where-Object { [int]$_ -ne 1 }).Count -ne 0 -or [int]$runtimeRow[11] -ne 432000) { throw 'Independent runtime requery failed exact-once or five-day offset assertions.' }

    $teardownInvoker = Join-Path $temporaryRoot 'invoke-global-teardown.js'
    $teardownModule = (Join-Path $uiRoot 'tests\e2e\support\todo-e2e-global-teardown.js').Replace('\', '/') | ConvertTo-Json -Compress
    Write-Utf8NoBom $teardownInvoker "Promise.resolve(require($teardownModule)()).catch(error => { console.error(error && error.stack || error); process.exitCode = 1 })`n"
    $env:TODO_E2E_DROP_DATABASE_AFTER = 'true'
    Invoke-ProcessStage 'database.global-teardown' 'node tests/e2e/support/todo-e2e-global-teardown.js [TODO_E2E_DROP_DATABASE_AFTER=true]' $nodeExecutable @($teardownInvoker) $uiRoot (Join-Path $artifactRoot 'global-teardown.log') $null | Out-Null
    $script:databaseDropped = $true

    $absenceSql = New-SqlInput 'db-absence.sql' "select count(*) from information_schema.schemata where schema_name='$database';"
    $absenceOutput = Join-Path $artifactRoot 'database-absence-proof.log'
    Invoke-MysqlStage 'database.absence-proof' 'mysql [credential-env] information_schema < db-absence.sql' $absenceSql $absenceOutput $null | Out-Null
    $absenceRow = @(Parse-SingleTabRow $absenceOutput 1)
    if ([int]$absenceRow[0] -ne 0) { throw "Database absence proof failed for $database." }

    Stop-OwnedProcessTree
    Start-Sleep -Seconds 1
    Assert-ServiceListenerAbsence $backendPort $frontendPort

    $backendEvidenceStarted = Get-Date
    try {
        $raw = ''
        if (Test-Path $backendRawLog) { $raw += Get-Content $backendRawLog -Raw }
        if (Test-Path $backendRawErrorLog) { $raw += [Environment]::NewLine + (Get-Content $backendRawErrorLog -Raw) }
        $safeRaw = (Protect-Text $raw).Replace($database, '[ACCEPTANCE_DATABASE]')
        $startedCount = ([regex]::Matches($safeRaw, 'Started RuoYiApplication')).Count
        if ($startedCount -ne 1) { throw "Backend evidence must contain exactly one Started RuoYiApplication record; found $startedCount." }
        Write-Utf8NoBom $backendEvidenceLog "HARNESS_DATABASE_IDENTITY=$database`r`nHARNESS_BACKEND_APPLICATION_PID=$($script:backendApplicationPid)`r`n$safeRaw"
        Add-StageResult 'evidence.backend-log' 'sanitize backend raw log and prepend one database identity' $backendEvidenceStarted (Get-Date) 0 $backendEvidenceLog @{ databaseIdentityCount = 1; applicationStartCount = 1 }
        Write-Host '[PASS] evidence.backend-log'
    } catch {
        Add-StageResult 'evidence.backend-log' 'sanitize backend raw log and prepend one database identity' $backendEvidenceStarted (Get-Date) 1 $backendEvidenceLog @{ error = Protect-Text $_.Exception.Message }
        throw
    }

    Write-Manifest 'PASSED' $database $runMarker
    Write-Host "Guided Lead Todo acceptance passed. Manifest: $(Get-RelativeArtifactPath $manifestPath)"
} catch {
    $script:failure = Protect-Text $_.Exception.Message
    Write-Host ("[FAIL] {0}" -f $script:failure) -ForegroundColor Red
} finally {
    if ($script:ownedProcessRootPids.Count -gt 0 -and -not $script:ownedServicesStopped) {
        try { Stop-OwnedProcessTree } catch { if (-not $script:failure) { $script:failure = Protect-Text $_.Exception.Message } }
    }
    if ($script:databaseCreated -and -not $script:databaseDropped -and $database) {
        try {
            Assert-SafeDatabaseName $database
            $fallbackSql = New-SqlInput 'fallback-drop.sql' "drop database if exists ``$database``;"
            Invoke-MysqlStage 'database.fallback-guarded-drop' 'mysql [credential-env] < guarded-fallback-drop.sql' $fallbackSql (Join-Path $artifactRoot 'fallback-drop.log') $null | Out-Null
            $script:databaseDropped = $true
        } catch {
            if (-not $script:failure) { $script:failure = Protect-Text $_.Exception.Message }
        }
    }
    if (-not $script:serviceListenerAbsenceRecorded -and (Test-Path $artifactRoot)) {
        try { Assert-ServiceListenerAbsence $backendPort $frontendPort } catch { if (-not $script:failure) { $script:failure = Protect-Text $_.Exception.Message } }
    }
    if ($script:backendLauncher -and -not (Test-Path $backendEvidenceLog) -and ((Test-Path $backendRawLog) -or (Test-Path $backendRawErrorLog))) {
        try {
            $raw = ''
            if (Test-Path $backendRawLog) { $raw += Get-Content $backendRawLog -Raw }
            if (Test-Path $backendRawErrorLog) { $raw += [Environment]::NewLine + (Get-Content $backendRawErrorLog -Raw) }
            $safeRaw = Protect-Text $raw
            if ($database) { $safeRaw = $safeRaw.Replace($database, '[ACCEPTANCE_DATABASE]') }
            Write-Utf8NoBom $backendEvidenceLog "HARNESS_DATABASE_IDENTITY=$database`r`nHARNESS_BACKEND_APPLICATION_PID=$($script:backendApplicationPid)`r`nHARNESS_RUN_STATUS=FAILED`r`n$safeRaw"
            $failureEvidenceStart = Get-Date
            Add-StageResult 'evidence.backend-log-failure' 'sanitize backend raw log after failed run' $failureEvidenceStart (Get-Date) 0 $backendEvidenceLog @{ failureEvidencePreserved = $true }
        } catch { }
    }
    if ($database -and (Test-Path $artifactRoot)) {
        Write-Manifest $(if ($script:failure) { 'FAILED' } else { 'PASSED' }) $database $runMarker
    }
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $temporaryRoot
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

if ($script:failure) { exit 1 }
exit 0

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkRoot = Join-Path $projectRoot '.jdk'
$jdkHome = Join-Path $jdkRoot 'jdk-17'
$javaExe = Join-Path $jdkHome 'bin\java.exe'
$downloadPath = Join-Path $jdkRoot 'jdk-17.zip'
$extractPath = Join-Path $jdkRoot 'extract'
$releaseUrl = 'https://api.github.com/repos/adoptium/temurin17-binaries/releases/latest'

if (Test-Path $javaExe) {
    Write-Host "Project JDK 17 is already installed at $jdkHome"
    & $javaExe -version
    exit 0
}

New-Item -ItemType Directory -Force -Path $jdkRoot | Out-Null
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $extractPath
New-Item -ItemType Directory -Force -Path $extractPath | Out-Null

Write-Host 'Downloading Eclipse Temurin JDK 17 for this repository...'
$release = Invoke-RestMethod -Headers @{ 'User-Agent' = 'RuoYi-Vue-project-jdk-setup' } -Uri $releaseUrl
$asset = $release.assets |
    Where-Object { $_.name -match '^OpenJDK17U-jdk_x64_windows_hotspot_.*\.zip$' } |
    Select-Object -First 1

if (-not $asset) {
    throw 'The latest Eclipse Temurin JDK 17 release did not contain a Windows x64 JDK archive.'
}

Invoke-WebRequest -Headers @{ 'User-Agent' = 'RuoYi-Vue-project-jdk-setup' } -Uri $asset.browser_download_url -OutFile $downloadPath

Write-Host 'Extracting JDK 17...'
Expand-Archive -LiteralPath $downloadPath -DestinationPath $extractPath -Force
$extractedHome = Get-ChildItem -Path $extractPath -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
    Select-Object -First 1

if (-not $extractedHome) {
    throw 'The downloaded archive did not contain a JDK.'
}

Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $jdkHome
Move-Item -LiteralPath $extractedHome.FullName -Destination $jdkHome
Remove-Item -Recurse -Force $extractPath
Remove-Item -Force $downloadPath

Write-Host "Project JDK 17 installed at $jdkHome"
& $javaExe -version

@echo off
set "PROJECT_JDK=%~dp0..\.jdk\jdk-17"

if not exist "%PROJECT_JDK%\bin\java.exe" (
    echo [ERROR] Project JDK 17 is not installed.
    echo Run: powershell -ExecutionPolicy Bypass -File "%~dp0setup-jdk17.ps1"
    exit /b 1
)

for %%I in ("%PROJECT_JDK%") do set "JAVA_HOME=%%~fI"
set "PATH=%JAVA_HOME%\bin;%PATH%"

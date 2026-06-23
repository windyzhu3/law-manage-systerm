@echo off
call "%~dp0set-jdk17.bat" || exit /b 1
call mvn %*

@echo off
echo.
echo [信息] 打包Web工程，生成war/jar包文件。
echo.

%~d0
cd %~dp0

cd ..
call "%~dp0set-jdk17.bat" || exit /b 1
call mvn clean package -Dmaven.test.skip=true

pause
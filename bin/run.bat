@echo off
echo.
echo [信息] 使用Jar命令运行Web工程。
echo.

cd %~dp0
cd ../ruoyi-admin/target

call "%~dp0set-jdk17.bat" || exit /b 1
set JAVA_OPTS=-Xms256m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m

java %JAVA_OPTS% -jar ruoyi-admin.jar

cd bin
pause
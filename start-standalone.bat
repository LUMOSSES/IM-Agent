@echo off
cd /d "%~dp0"

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JDK 17 not found at %JAVA_HOME%
    echo Please update JAVA_HOME in this script.
    pause
    exit /b 1
)
set PATH=%JAVA_HOME%\bin;%PATH%

echo Starting InfinteChat-Agent (standalone)...
echo Frontend: http://localhost:8087/api/index.html
echo.

call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=standalone
pause
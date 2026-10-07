@echo off
setlocal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo Java hittades inte. Installera JDK 21 eller senare och forsok igen.
    pause
    exit /b 1
)

where javac >nul 2>nul
if errorlevel 1 (
    echo Java-kompilatorn javac hittades inte. Installera JDK 21 eller senare.
    pause
    exit /b 1
)

if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME pekar inte pa en giltig Java-installation: "%JAVA_HOME%"
    pause
    exit /b 1
)

if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo JAVA_HOME pekar inte pa en giltig JDK-installation: "%JAVA_HOME%"
    pause
    exit /b 1
)

start "Fiskeguiden server" /min cmd /k ""%~dp0mvnw.cmd" -f "%~dp0pom.xml" spring-boot:run"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddMinutes(3); do { try { $response=Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://localhost:8080/'; if ($response.StatusCode -eq 200 -and $response.Content.Contains('Fiskeguiden')) { Start-Process 'http://localhost:8080/'; exit 0 } } catch {}; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); Write-Error 'Fiskeguiden startade inte inom tre minuter. Kontrollera serverns konsolfonster.'; exit 1"
if errorlevel 1 (
    echo Starten misslyckades. Kontrollera att JDK 21 ar installerat och att port 8080 ar ledig.
    pause
    exit /b 1
)

endlocal

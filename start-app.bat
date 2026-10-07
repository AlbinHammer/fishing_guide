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

powershell -NoProfile -ExecutionPolicy Bypass -Command "foreach($port in 8080..8090) { try { $base='http://localhost:'+$port; $page=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 ($base+'/'); $body=ConvertTo-Json -Depth 4 -InputObject @{species='ABBORRE';conditions=@(@{month=10;windSpeed=4;windDir=180;pressureTrend=0;cloudCover=50;dawnOrDusk=$false})}; $forecast=Invoke-RestMethod -TimeoutSec 2 -Method Post -ContentType 'application/json' -Body $body ($base+'/api/evaluate/forecast'); if ($page.StatusCode -eq 200 -and $page.Content.Contains('Fiskeguiden') -and $forecast.Count -eq 1) { Start-Process $base; exit 0 } } catch {} }; exit 1"
if not errorlevel 1 (
    echo Fiskeguiden kor redan. Den befintliga versionen oppnades i webblasaren.
    endlocal
    exit /b 0
)

set "APP_PORT="
for /f %%P in ('powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-free-port.ps1"') do set "APP_PORT=%%P"
if not defined APP_PORT (
    echo Kunde inte hitta en ledig port mellan 8080 och 8090. Stang en server pa dessa portar och forsok igen.
    pause
    exit /b 1
)

echo Startar Fiskeguiden pa port %APP_PORT%.
start "Fiskeguiden server" /min cmd /k ""%~dp0mvnw.cmd" -f "%~dp0pom.xml" spring-boot:run "-Dspring-boot.run.arguments=--server.port=%APP_PORT%""

powershell -NoProfile -ExecutionPolicy Bypass -Command "$port=%APP_PORT%; $base='http://localhost:'+$port; $deadline=(Get-Date).AddMinutes(3); do { try { $page=Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 ($base+'/'); $body=ConvertTo-Json -Depth 4 -InputObject @{species='ABBORRE';conditions=@(@{month=10;windSpeed=4;windDir=180;pressureTrend=0;cloudCover=50;dawnOrDusk=$false})}; $forecast=Invoke-RestMethod -TimeoutSec 3 -Method Post -ContentType 'application/json' -Body $body ($base+'/api/evaluate/forecast'); if ($page.StatusCode -eq 200 -and $page.Content.Contains('Fiskeguiden') -and $forecast.Count -eq 1) { Start-Process $base; exit 0 } } catch {}; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); Write-Error 'Fiskeguiden startade inte korrekt inom tre minuter. Kontrollera serverns konsolfonster.'; exit 1"
if errorlevel 1 (
    echo Starten misslyckades. Kontrollera att JDK 21 eller senare ar installerat och att servern startade pa port %APP_PORT%.
    pause
    exit /b 1
)

endlocal

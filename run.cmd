@echo off
setlocal

cd /d "%~dp0"

set "AIMS_JAR=%~dp0aims-replay-viewer.jar"
if not exist "%AIMS_JAR%" set "AIMS_JAR=%~dp0target\aims-replay-viewer-1.0.1.jar"

if not exist "%AIMS_JAR%" (
  echo Missing AIMS Replay Viewer jar.
  echo Expected one of:
  echo   %~dp0aims-replay-viewer.jar
  echo   %~dp0target\aims-replay-viewer-1.0.1.jar
  echo.
  echo Build it with:
  echo   mvn -q -DskipTests package
  exit /b 1
)

java -jar "%AIMS_JAR%" run %*

endlocal

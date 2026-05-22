@echo off
setlocal

cd /d "%~dp0"

set "VIEWER=%~dp0AIMS-Replay-Viewer.html"
set "LATEST_REPLAY=%~dp0viewer-assets\latest-replay.js"
set "AIMS_JAR=%~dp0aims-replay-viewer.jar"
if not exist "%AIMS_JAR%" set "AIMS_JAR=%~dp0target\aims-replay-viewer-1.0.1.jar"

if not exist "%VIEWER%" (
  goto initViewer
)
if not exist "%LATEST_REPLAY%" (
  goto initViewer
)
goto openViewer

:initViewer
  if not exist "%AIMS_JAR%" (
    echo Missing AIMS Replay Viewer jar.
    echo Expected one of:
    echo   %~dp0aims-replay-viewer.jar
    echo   %~dp0target\aims-replay-viewer-1.0.1.jar
    echo.
    pause
    exit /b 1
  )

  echo Creating an empty replay viewer...
  java -jar "%AIMS_JAR%" init-viewer --viewer-dir .
  if errorlevel 1 (
    echo.
    echo Failed to create the replay viewer.
    pause
    exit /b 1
  )

:openViewer
start "" "%VIEWER%"

endlocal

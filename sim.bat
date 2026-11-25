@echo off
rem BioBuzz simulator launcher for Windows. Same arguments as ./sim on macOS and Linux.
rem   sim                       build, start the engine, open the 3D page in the browser
rem   sim test list             list the OpModes in the checked-out code
rem   sim test OPMODE [--runs N] [--code BRANCH] [--verbose]
rem   sim shots                 shot accuracy table
setlocal
cd /d "%~dp0"
if errorlevel 1 exit /b 1
call gradlew.bat -q :engine:installDist
if errorlevel 1 exit /b 1
call engine\build\install\engine\bin\engine.bat %*
exit /b %ERRORLEVEL%

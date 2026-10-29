@echo off
if not exist "out\bin\com\apisentinel\App.class" (
    call scripts\build.bat
    if errorlevel 1 exit /b 1
)

set PORT=8080
if not "%1"=="" set PORT=%1

echo [APISentinel] Starting server on port %PORT%...
java -cp out\bin com.apisentinel.App %PORT%

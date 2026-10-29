@echo off
echo [APISentinel] Compiling Java 17 application...

if not exist "out\bin" mkdir "out\bin"

dir /s /b src\main\java\*.java > sources.txt
javac -encoding UTF-8 -d out\bin @sources.txt
set COMPILE_ERR=%ERRORLEVEL%
del sources.txt

if %COMPILE_ERR% neq 0 (
    echo [ERROR] Compilation failed!
    exit /b %COMPILE_ERR%
)

xcopy /E /I /Y src\main\resources out\bin >nul

echo [APISentinel] Build successful! Binaries compiled to out\bin.

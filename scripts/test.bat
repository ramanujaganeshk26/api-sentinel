@echo off
call scripts\build.bat
if errorlevel 1 exit /b 1

echo [APISentinel] Compiling test suite...
dir /s /b src\test\java\*.java > test_sources.txt
javac -encoding UTF-8 -cp out\bin -d out\bin @test_sources.txt
del test_sources.txt

if errorlevel 1 (
    echo [ERROR] Test compilation failed!
    exit /b %errorlevel%
)

echo.
java -cp out\bin com.apisentinel.APISentinelTest

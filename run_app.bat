@echo off
setlocal

:: Use system Java from PATH
set "JAVA_EXE=java"
set "JAVAC_EXE=javac"

:: Set Classpath Libraries
set "CP_LIBS=src\lib\jakarta.activation-2.0.1.jar;src\lib\jakarta.mail-2.0.1.jar;src\lib\mysql-connector-j-9.3.0.jar;src\lib\core-3.5.2.jar;src\lib\javase-3.5.2.jar"

if "%1"=="run_only" goto RUN_APP
if "%1"=="compile_only" goto COMPILE_APP

:COMPILE_APP
if not exist bin mkdir bin
echo [INFO] Compiling Campus Event Management System...
"%JAVAC_EXE%" -cp "src;%CP_LIBS%" -d bin src\db\*.java src\model\*.java src\service\*.java src\api\*.java src\main\main.java
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    if "%1"=="" pause
    exit /b 1
)
if "%1"=="compile_only" exit /b 0

:RUN_APP
echo [INFO] Starting Application...
"%JAVA_EXE%" -cp "bin;src;%CP_LIBS%" main.main
if errorlevel 1 (
    echo [ERROR] Application crashed.
    pause
)
exit /b 0


@echo off
setlocal
title Campus Event Management System

:: --- CONFIGURATION ---
set "BACKEND_DIR=%~dp0"
set "FRONTEND_DIR=%~dp0campus-ui"
set "BROWSER_URL=http://localhost:5173"

:: Get Local IP for Mobile Access
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr "IPv4"') do set "LOCAL_IP=%%a"
set "LOCAL_IP=%LOCAL_IP: =%"
set "MOBILE_URL=http://%LOCAL_IP%:5173"

echo ======================================================
echo   Starting Campus Event Management System
echo ======================================================

:: 1. Compile Backend
echo [1/3] Compiling Backend...
call "%BACKEND_DIR%run_app.bat" compile_only
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

:: 2. Start Backend in background
echo [2/3] Starting Backend Server...
start /B "" cmd /c cd /d "%BACKEND_DIR%" ^&^& run_app.bat run_only

:: 3. Start Frontend in background
echo [3/3] Starting Frontend UI...
cd /d "%FRONTEND_DIR%"
if not exist "node_modules" (
    echo [INFO] Installing dependencies...
    call npm install
)
start /B "" cmd /c "npm run dev"

echo Waiting for servers to start...
timeout /t 5 /nobreak > nul

echo Browser should open automatically in Brave...

echo.
echo ======================================================
echo   APP IS RUNNING! (Press any key below to stop)
echo.
echo   MOBILE ACCESS LINK (Share with teammates):
echo   %MOBILE_URL%
echo ======================================================
echo.
pause

echo.
echo [INFO] Stopping servers...
for /f "tokens=5" %%a in ('netstat -aon ^| find "LISTENING" ^| find ":8082"') do taskkill /f /pid %%a > nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| find "LISTENING" ^| find ":5173"') do taskkill /f /pid %%a > nul 2>&1

echo [OK] All systems stopped.
exit

@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0scripts\Start-CreatorHub.ps1"
set "creatorhubExit=%ERRORLEVEL%"
if not "%creatorhubExit%"=="0" pause
exit /b %creatorhubExit%

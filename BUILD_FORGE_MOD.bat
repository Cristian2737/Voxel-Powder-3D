@echo off
setlocal EnableExtensions
title Voxel Powder 3D - Forge mod build
pushd "%~dp0forge"
where java.exe >nul 2>nul
if errorlevel 1 (echo Install JDK 17 first.&pause&exit /b 1)
where powershell.exe >nul 2>nul
if errorlevel 1 (echo PowerShell is required.&pause&exit /b 2)
if not exist "gradlew.bat" (
  echo Preparing official Minecraft Forge MDK 1.20.1-47.4.28...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "SETUP_FORGE_MDK.ps1"
  if errorlevel 1 (echo Forge MDK setup failed.&pause&exit /b 3)
)
echo Building Forge JAR... first build downloads Forge and Minecraft dependencies.
call gradlew.bat build --stacktrace
if errorlevel 1 (echo Build failed. Please send the console output.&pause&exit /b 4)
echo.
echo JAR should be in forge\build\libs\
explorer.exe "build\libs"
pause
popd

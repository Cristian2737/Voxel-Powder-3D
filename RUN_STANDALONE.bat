@echo off
setlocal EnableExtensions
pushd "%~dp0" || (echo Failed to open program folder.&pause&exit /b 1)
title Voxel Powder 3D - Standalone Viewer
if not exist "VoxelPowder3D_Standalone.jar" (
  echo ERROR: VoxelPowder3D_Standalone.jar is missing.
  echo Extract the entire ZIP into one folder first.
  pause
  popd
  exit /b 2
)
where java.exe >nul 2>nul
if errorlevel 1 (
  echo Java 8 or newer is required to run the preview.
  echo Install Java from https://adoptium.net/temurin/releases/
  pause
  popd
  exit /b 3
)
echo Starting Voxel Powder 3D standalone preview (Java 8+)...
echo Java version in use:
java -version
java -jar "VoxelPowder3D_Standalone.jar"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo.
  echo ERROR: Java preview stopped with exit code %RC%.
  echo If you need help, send the error above.
  pause
)
popd
exit /b %RC%

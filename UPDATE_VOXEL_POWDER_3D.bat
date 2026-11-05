@echo off
setlocal EnableExtensions
set "DEST=D:\.Temp\Voxel_Powder_3D_Java_Forge_v0.1"
set "REPO=Cristian2737/Voxel-Powder-3D"
set "VP_URL=https://codeload.github.com/%REPO%/zip/refs/heads/main"
set "WORK=%TEMP%\VoxelPowder3D_%RANDOM%_%RANDOM%"
set "VP_ZIP=%WORK%\project.zip"
set "VP_UNPACK=%WORK%\unpacked"
set "VP_SOURCE="
title Voxel Powder 3D - Update and Play

echo Updating from https://github.com/%REPO%
echo Installation: %DEST%
echo.
where powershell.exe >nul 2>nul
if errorlevel 1 (
    echo ERROR: Windows PowerShell is required.
    goto FAIL
)
if not exist "%DEST%" mkdir "%DEST%"
if not exist "%WORK%" mkdir "%WORK%"
if not exist "%VP_UNPACK%" mkdir "%VP_UNPACK%"

powershell.exe -NoProfile -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri $env:VP_URL -OutFile $env:VP_ZIP; Expand-Archive -LiteralPath $env:VP_ZIP -DestinationPath $env:VP_UNPACK -Force"
if errorlevel 1 (
    echo.
    echo DOWNLOAD FAILED: The repository may be empty, private, or not published yet.
    echo Visit https://github.com/%REPO%
    goto FAIL
)

for /d %%D in ("%VP_UNPACK%\*") do (
    if exist "%%~fD\RUN_STANDALONE.bat" if exist "%%~fD\VoxelPowder3D_Standalone.jar" set "VP_SOURCE=%%~fD"
)
if not defined VP_SOURCE (
    echo ERROR: The repository does not contain the standalone launcher and JAR.
    goto FAIL
)

rem Preserve existing files, including any custom modifications, before updating.
if exist "%DEST%\RUN_STANDALONE.bat" (
    call :BACKUP_FILES
    if errorlevel 1 goto FAIL
)

echo.
echo Applying update. Existing extra files will NOT be deleted...
robocopy "%VP_SOURCE%" "%DEST%" /E /R:2 /W:1 /NFL /NDL /NJH /NJS /XD .git .gradle build /XF UPDATE_VOXEL_POWDER_3D.bat
if errorlevel 8 (
    echo ERROR: File update failed. Your previous files are in the backup folder.
    goto FAIL
)

if exist "%WORK%" rmdir /s /q "%WORK%"
echo.
echo Update complete. Starting Voxel Powder 3D...
call "%DEST%\RUN_STANDALONE.bat"
exit /b %ERRORLEVEL%

:BACKUP_FILES
rem The backup is intentionally outside the installation directory.
for /f %%I in ('powershell.exe -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%I"
if not defined STAMP set "STAMP=backup"
set "BACKUP=D:\.Temp\Voxel_Powder_3D_Backups\%STAMP%_%RANDOM%"
echo Backing up current files to "%BACKUP%"...
robocopy "%DEST%" "%BACKUP%" /E /R:1 /W:1 /NFL /NDL /NJH /NJS /XD .git .gradle build
if errorlevel 8 exit /b 1
exit /b 0

:FAIL
echo.
echo Update was not completed. If a file copy failed, restore from the backup folder.
if exist "%WORK%" rmdir /s /q "%WORK%"
pause
exit /b 1

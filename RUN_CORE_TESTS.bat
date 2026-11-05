@echo off
setlocal EnableExtensions
pushd "%~dp0" || (echo Could not open project folder.&pause&exit /b 1)
where javac.exe >nul 2>nul
if errorlevel 1 (echo JDK 8 or newer required to run the tests.&pause&exit /b 1)
if not exist "build\tests" mkdir "build\tests"
set "CORE=core\src\main\java\com\cristian\voxelpowder\core"
set "VIEW=standalone\src\main\java\com\cristian\voxelpowder\standalone"
set "TEST=tests\src\test\java\com\cristian\voxelpowder\tests"
javac -source 8 -target 8 -d "build\tests" "%CORE%\Matter.java" "%CORE%\Electricity.java" "%CORE%\VoxelWorld.java" "%CORE%\Simulator.java" "%CORE%\SeedScenes.java" "%CORE%\WalkableScenes.java" "%VIEW%\FirstPersonCamera.java" "%VIEW%\PlayerController.java" "%VIEW%\VoxelRenderer.java" "%VIEW%\VoxelRaycast.java" "%VIEW%\Viewer.java" "%TEST%\CoreTests.java" "%TEST%\RenderTests.java" "%TEST%\MovementTests.java" "%TEST%\InteractionTests.java" "%TEST%\ElectronicsTests.java"
if errorlevel 1 (echo COMPILE FAILED&pause&exit /b 2)
java -cp "build\tests" com.cristian.voxelpowder.tests.CoreTests
if errorlevel 1 (echo CORE TESTS FAILED&pause&exit /b 3)
java -cp "build\tests" com.cristian.voxelpowder.tests.RenderTests
if errorlevel 1 (echo RENDER TESTS FAILED&pause&exit /b 4)
java -cp "build\tests" com.cristian.voxelpowder.tests.MovementTests
if errorlevel 1 (echo MOVEMENT TESTS FAILED&pause&exit /b 5)
java -cp "build\tests" com.cristian.voxelpowder.tests.InteractionTests
if errorlevel 1 (echo INTERACTION TESTS FAILED&pause&exit /b 6)
java -cp "build\tests" com.cristian.voxelpowder.tests.ElectronicsTests
if errorlevel 1 (echo ELECTRONICS TESTS FAILED&pause&exit /b 7)
echo.
echo All tests passed.
pause
popd

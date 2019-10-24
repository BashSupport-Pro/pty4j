@ECHO ON

rem For Visual Studio 2022, platform can be Win32/x64/ARM/ARM64
rem https://cmake.org/cmake/help/latest/generator/Visual%20Studio%2016%202019.html

SET PLATFORM=%1
SET BUILD_DIR="%~dp0\build-release-%PLATFORM%"
SET CMAKE=C:\Program Files\CMake\bin\cmake.exe

IF EXIST "%BUILD_DIR%" RMDIR /S /Q "%BUILD_DIR%"
MKDIR "%BUILD_DIR%" & CD "%BUILD_DIR%"

"%CMAKE%" -G "Visual Studio 17 2022" -A "%PLATFORM%" ..
IF ERRORLEVEL 1 EXIT /B 1

"%CMAKE%" --build . --config Release
IF ERRORLEVEL 1 EXIT /B 2

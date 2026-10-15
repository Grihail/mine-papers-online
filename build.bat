@echo off
rem Builds the mod for every game version and loader with one command.
rem Jars: <version>-<loader>\build\libs. Extra arguments go to gradlew instead of "build":
rem   build.bat                 - build all jars
rem   build.bat test            - build all jars and run the game tests of every version (task gameTest)
rem   build.bat clean build     - any gradle tasks
rem Gradle itself must run on JDK 25 (RetroFuturaGradle needs it); the game JDKs are picked by toolchains.
setlocal
if not defined MINEPAPERS_JDK set "MINEPAPERS_JDK=C:\Program Files\Java\jdk-25"
set "JAVA_HOME=%MINEPAPERS_JDK%"
if "%~1"=="" (
    call "%~dp0gradlew.bat" -p "%~dp0." build
) else if /i "%~1"=="test" (
    call "%~dp0gradlew.bat" -p "%~dp0." build gameTest
) else (
    call "%~dp0gradlew.bat" -p "%~dp0." %*
)
exit /b %ERRORLEVEL%

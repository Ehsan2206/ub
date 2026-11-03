@echo off
setlocal EnableExtensions
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.13"
set "DIST_ROOT=%USERPROFILE%\.gradle\hesabyar-wrapper"
set "GRADLE_HOME=%DIST_ROOT%\gradle-%GRADLE_VERSION%"
set "ZIP_FILE=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
set "DIST_URL=https://maven.myket.ir/gradle/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run
if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"

echo [HesabYar] Downloading Gradle %GRADLE_VERSION% from Myket mirror...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing '%DIST_URL%' -OutFile '%ZIP_FILE%'; if (Test-Path '%GRADLE_HOME%') { Remove-Item -Recurse -Force '%GRADLE_HOME%' }; Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%DIST_ROOT%' -Force"
if errorlevel 1 (
  echo.
  echo Gradle download failed. Check access to https://maven.myket.ir and try again.
  exit /b 1
)

:run
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%

@echo off
REM =====================================================================
REM  Example 1: download NCSoft Lineage 2 Japan, a specific version.
REM  Edit the variables below, then double-click this file.
REM  (Java is chosen in set_java.bat.)
REM =====================================================================
setlocal
call "%~dp0set_java.bat"
if errorlevel 1 ( pause & exit /b 1 )
cd /d "%~dp0"

REM  Patch version to download (use example_last_version.bat to find the current one).
set VERSION=215

REM  File filters. INCLUDE keeps matching files; EXCLUDE drops matching files.
REM  (';' separates several patterns; '*' is a wildcard.)
set INCLUDE_FILTER=*
set EXCLUDE_FILTER=*/*.dlt;*.torrent

REM  Output sub-folder, relative to THIS .bat's directory.
set INNER_PATH=L2_JP\%VERSION%

"%JAVA_EXE%" -jar Lineage_02_Patch_Downloader.jar ^
  -cdn NC_SOFT_JAPANESE ^
  -version %VERSION% ^
  -include_filter "%INCLUDE_FILTER%" ^
  -exclude_filter "%EXCLUDE_FILTER%" ^
  -inner_path "%INNER_PATH%"

pause

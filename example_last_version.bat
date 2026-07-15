@echo off
REM =====================================================================
REM  Example 3: print the CURRENT NCSoft Lineage 2 Japan patch version.
REM  Live query over the NC "Purple" update protocol (TCP:27500).
REM  Downloads nothing and needs no config file. Change -cdn for other
REM  regions: NC_SOFT_AMERICA / NC_SOFT_TAIWAN / NC_SOFT_KOREAN.
REM  (Java is chosen in set_java.bat.)
REM =====================================================================
setlocal
call "%~dp0set_java.bat"
if errorlevel 1 ( pause & exit /b 1 )
cd /d "%~dp0"

"%JAVA_EXE%" -jar Lineage_02_Patch_Downloader.jar -cdn NC_SOFT_JAPANESE -last_version

pause
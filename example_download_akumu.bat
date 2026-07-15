@echo off
REM =====================================================================
REM  Example 2: download from the akumu.ru mirror.
REM  AKUMU takes a FOLDER URL; the file list comes from its .torrent
REM  (fetched once through the site's antibot). No -version here.
REM  (Java is chosen in set_java.bat.)
REM =====================================================================
setlocal
call "%~dp0set_java.bat"
if errorlevel 1 ( pause & exit /b 1 )
cd /d "%~dp0"

REM  The akumu folder URL (must end with '/').
set AKUMU_URL=http://akumu.ru/lineage2/L2NA/P520/L2NA-P520-D20251022-G545-US_250528_b251022.04_770673/

REM  Output sub-folder, relative to THIS .bat's directory.
set INNER_PATH=akumu_out

"%JAVA_EXE%" -jar Lineage_02_Patch_Downloader.jar ^
  -cdn AKUMU ^
  -akumu_url "%AKUMU_URL%" ^
  -inner_path "%INNER_PATH%"

pause
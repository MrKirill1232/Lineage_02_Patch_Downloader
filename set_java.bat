@echo off
REM =====================================================================
REM  set_java.bat - picks which Java the example launchers use.
REM  It exports JAVA_EXE; the other .bat files then run "%JAVA_EXE%".
REM =====================================================================

REM  USE_CUSTOM_JAVA:
REM    false = use the Java already configured on this machine
REM            (the JAVA_HOME environment variable, or `java` from PATH).
REM    true  = use CUSTOM_JAVA_HOME below (a specific JDK folder).
set "USE_CUSTOM_JAVA=false"
set "CUSTOM_JAVA_HOME=C:\Program Files\Amazon Corretto\jdk25.0.2_10"

REM  Resolve JAVA_EXE: default to `java` (PATH); use JAVA_HOME if set; override if custom.
set "JAVA_EXE=java"
if /I "%USE_CUSTOM_JAVA%"=="true" set "JAVA_EXE=%CUSTOM_JAVA_HOME%\bin\java.exe"
if /I not "%USE_CUSTOM_JAVA%"=="true" if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

REM  Validate the choice so the launchers fail with a clear message.
if /I "%JAVA_EXE%"=="java" (
    where java >nul 2>nul || (
        echo [set_java] 'java' is not on PATH and JAVA_HOME is not set.
        echo [set_java] Set USE_CUSTOM_JAVA=true and CUSTOM_JAVA_HOME, or configure Java on your system.
        exit /b 1
    )
) else (
    if not exist "%JAVA_EXE%" (
        echo [set_java] java.exe not found: "%JAVA_EXE%"
        echo [set_java] Fix JAVA_HOME, or set USE_CUSTOM_JAVA=true and CUSTOM_JAVA_HOME in set_java.bat.
        exit /b 1
    )
)

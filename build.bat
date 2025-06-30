@echo off
echo Distributed Inventory System - Build Script
echo ==========================================

:: Set JavaFX path - Modify this path to your JavaFX installation
set JAVAFX_PATH=D:\openjfx-24.0.1_windows-x64_bin-sdk\javafx-sdk-24.0.1\lib

:: Create output directory
if not exist "out" mkdir out

echo.
echo Compiling Java sources...
if exist "%JAVAFX_PATH%" (
    echo Compiling with JavaFX classpath...
    javac -d out -cp "src;%JAVAFX_PATH%\*" src\main\*.java src\client\*.java src\server\*.java src\communication\*.java src\distributed\*.java src\inventory\*.java src\replication\*.java src\chatroom\*.java
) else (
    echo JavaFX path not found, compiling without JavaFX...
    javac -d out -cp "src" src\main\*.java src\client\*.java src\server\*.java src\communication\*.java src\distributed\*.java src\inventory\*.java src\replication\*.java src\chatroom\*.java
)

if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Compilation successful!
echo.
echo Available commands:
echo   build.bat server BranchA 8001    - Start Branch Server A
echo   build.bat server BranchB 8002    - Start Branch Server B  
echo   build.bat server BranchC 8003    - Start Branch Server C
echo   build.bat client                 - Start JavaFX Client
echo.

if "%1"=="server" (
    if "%2"=="" (
        echo Please specify branch ID and port
        echo Example: build.bat server BranchA 8001
        pause
        exit /b 1
    )
    if "%3"=="" (
        echo Please specify port number
        echo Example: build.bat server BranchA 8001
        pause
        exit /b 1
    )
    echo Starting Branch Server: %2 on port %3
    java -cp "out" main.Main server %2 %3
) else if "%1"=="client" (
    echo Starting JavaFX Client...
    if exist "%JAVAFX_PATH%" (
        java -cp "out;%JAVAFX_PATH%\*" main.Main client
    ) else (
        echo Error: JavaFX path not found at: %JAVAFX_PATH%
        echo Please check your JavaFX installation path
        pause
        exit /b 1
    )
) else if "%1"=="chat" (
    if "%2"=="" (
        echo Please specify port number
        echo Example: build.bat chat 9001
        pause
        exit /b 1
    )
    echo Connecting to chatroom on port %2
    telnet localhost %2
) else if "%1"=="" (
    echo Build completed. Use commands above to start components.
) else (
    echo Unknown command: %1
    echo Use: server, client, or chat
)

pause 
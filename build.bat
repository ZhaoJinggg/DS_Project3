@echo off
echo Distributed Inventory System - Build Script
echo ==========================================

:: Set JavaFX path - Modify this path to your JavaFX installation
set JAVAFX_PATH=C:\javafx\lib

:: Create output directory
if not exist "out" mkdir out

echo.
echo Compiling Java sources...
javac -d out -cp "src" src\main\*.java src\client\*.java src\server\*.java src\communication\*.java src\distributed\*.java src\inventory\*.java src\replication\*.java src\chatroom\*.java

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
        java -cp "out" --module-path "%JAVAFX_PATH%" --add-modules javafx.controls,javafx.fxml main.Main client
    ) else (
        echo Warning: JavaFX path not found. Trying without module path...
        java -cp "out" main.Main client
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
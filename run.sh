#!/bin/bash

echo "Distributed Inventory System - Build Script"
echo "==========================================="

# Set JavaFX path - Modify this path to your JavaFX installation
JAVAFX_PATH="/usr/lib/jvm/javafx/lib"

# Create output directory
mkdir -p out

echo ""
echo "Compiling Java sources..."
javac -d out -cp "src" src/main/*.java src/client/*.java src/server/*.java src/communication/*.java src/distributed/*.java src/inventory/*.java src/replication/*.java src/chatroom/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Compilation successful!"
echo ""
echo "Available commands:"
echo "  ./run.sh server BranchA 8001    - Start Branch Server A"
echo "  ./run.sh server BranchB 8002    - Start Branch Server B"
echo "  ./run.sh server BranchC 8003    - Start Branch Server C"
echo "  ./run.sh client                 - Start JavaFX Client"
echo ""

if [ "$1" = "server" ]; then
    if [ -z "$2" ] || [ -z "$3" ]; then
        echo "Please specify branch ID and port"
        echo "Example: ./run.sh server BranchA 8001"
        exit 1
    fi
    echo "Starting Branch Server: $2 on port $3"
    java -cp "out" main.Main server $2 $3
elif [ "$1" = "client" ]; then
    echo "Starting JavaFX Client..."
    if [ -d "$JAVAFX_PATH" ]; then
        java -cp "out" --module-path "$JAVAFX_PATH" --add-modules javafx.controls,javafx.fxml main.Main client
    else
        echo "Warning: JavaFX path not found. Trying without module path..."
        java -cp "out" main.Main client
    fi
elif [ "$1" = "chat" ]; then
    if [ -z "$2" ]; then
        echo "Please specify port number"
        echo "Example: ./run.sh chat 9001"
        exit 1
    fi
    echo "Connecting to chatroom on port $2"
    telnet localhost $2
elif [ -z "$1" ]; then
    echo "Build completed. Use commands above to start components."
else
    echo "Unknown command: $1"
    echo "Use: server, client, or chat"
fi 
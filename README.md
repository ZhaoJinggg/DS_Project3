# Distributed Inventory System

A comprehensive Java-based distributed inventory management system with client-server architecture, implementing advanced distributed systems concepts including mutual exclusion, logical clocks, and replication.

## Features

### Core Functionality
- **Client Application (JavaFX GUI)**: View stock quantities, submit replenishment requests, real-time status updates
- **Branch Server**: Manages local inventory, handles client requests, coordinates with other branches
- **Branch-to-Branch Communication**: Automatic stock replenishment between branches when inventory is low
- **Distributed Locking**: Ricart-Agrawala algorithm for safe concurrent updates to shared product data
- **Logical Timestamps**: Lamport clocks maintain global event ordering across the distributed system
- **Replication**: Log shipping for synchronizing stock updates across branches
- **Chatroom Module**: Staff communication system between branches
- **Thread-Safe Operations**: Concurrent handling of multiple client requests

### Distributed Systems Concepts Implemented
1. **Ricart-Agrawala Distributed Mutual Exclusion**
2. **Lamport Logical Clocks**
3. **Log-based Replication**
4. **Message Passing Communication**
5. **Fault-Tolerant Design**

## Project Structure

```
src/
├── main/
│   └── Main.java                    # Entry point for client/server launch
├── client/
│   └── InventoryClientApp.java      # JavaFX client application
├── server/
│   ├── BranchServer.java           # Main branch server
│   └── ClientConnectionManager.java # Handles client connections
├── communication/
│   ├── Message.java                # Message class for inter-node communication
│   ├── MessageType.java            # Enumeration of message types
│   ├── NetworkManager.java         # Network communication handler
│   └── NodeConnection.java         # Individual node connection wrapper
├── distributed/
│   ├── LamportClock.java           # Lamport logical clock implementation
│   └── RicartAgrawalaMutex.java    # Distributed mutual exclusion
├── inventory/
│   ├── Product.java                # Product data model
│   └── InventoryManager.java       # Thread-safe inventory operations
├── replication/
│   ├── ReplicationManager.java     # Handles log replication
│   └── LogEntry.java              # Log entry data structure
├── chatroom/
│   ├── ChatroomServer.java        # Chatroom server for staff communication
│   ├── ChatClient.java            # Individual chat client handler
│   └── ChatMessage.java           # Chat message data structure
└── utils/                          # Utility classes (as needed)
```

## Getting Started

### Prerequisites
- Java 11 or higher
- JavaFX runtime (for client GUI)
- IDE with Java support (IntelliJ IDEA recommended)

### Setting Up the Project

1. **Clone or download the project**
2. **Import into your IDE** as a Java project
3. **Add JavaFX to your module path** (if using Java 11+):
   - Download JavaFX SDK from https://openjfx.io/
   - Add JavaFX modules to your project configuration

### Running the System

#### Starting Branch Servers

Each branch server runs on a different port. Start multiple branch servers:

```bash
# Start Branch A on port 8001
java main.Main server BranchA 8001

# Start Branch B on port 8002  
java main.Main server BranchB 8002

# Start Branch C on port 8003
java main.Main server BranchC 8003
```

**Port Allocation:**
- Main server port: 8001, 8002, 8003...
- Client connections: +100 (8101, 8102, 8103...)
- Chatroom: +1000 (9001, 9002, 9003...)

#### Connecting Branches

To connect branches, you can modify the `Main.java` or add connection logic in the server startup to connect branches:

```java
// In BranchServer.java, add after startup:
server.connectToBranch("BranchB", "localhost", 8002);
server.connectToBranch("BranchC", "localhost", 8003);
```

#### Starting Client Applications

```bash
# Launch JavaFX client
java main.Main client
```

In the client GUI:
1. Enter server IP: `localhost`
2. Enter port: `8101` (for BranchA), `8102` (for BranchB), etc.
3. Click "Connect"
4. View inventory and submit replenishment requests

### Testing the System

#### Test Scenario 1: Basic Inventory Operations
1. Start two branch servers (BranchA, BranchB)
2. Connect a client to BranchA
3. View current inventory
4. Submit a replenishment request
5. Observe automatic stock transfer from BranchB

#### Test Scenario 2: Distributed Locking
1. Start multiple branch servers
2. Connect multiple clients
3. Submit simultaneous inventory updates
4. Verify consistency using Ricart-Agrawala mutex

#### Test Scenario 3: Chatroom Communication
1. Connect to chatroom port (e.g., 9001)
2. Use telnet or chat client to send messages
3. Observe staff communication between branches

```bash
# Connect to chatroom
telnet localhost 9001
```

## System Architecture

### Communication Patterns

1. **Client ↔ Server**: Object serialization over TCP sockets
2. **Branch ↔ Branch**: Message passing with Lamport timestamps
3. **Replication**: Log entries broadcast to all connected branches
4. **Chatroom**: Text-based communication for staff

### Message Types

- `CLIENT_CONNECT/DISCONNECT`: Client session management
- `STOCK_QUERY/RESPONSE`: Inventory information requests
- `REPLENISHMENT_REQUEST/RESPONSE`: Stock replenishment coordination
- `STOCK_TRANSFER_REQUEST/RESPONSE`: Inter-branch stock transfers
- `MUTEX_REQUEST/REPLY`: Distributed mutual exclusion
- `LOG_ENTRY/ACK`: Replication synchronization
- `CHAT_MESSAGE`: Staff communication

### Distributed Algorithms

#### Ricart-Agrawala Mutual Exclusion
- Ensures exclusive access to shared resources (product inventory)
- Uses Lamport timestamps to order requests
- Handles concurrent access from multiple branches

#### Lamport Logical Clocks
- Maintains causally ordered events across distributed system
- Updates on local events and message receipt
- Ensures consistent global ordering

#### Log-based Replication
- Synchronizes inventory changes across branches
- Periodic sync requests maintain consistency
- Handles network partitions gracefully

## Configuration

### Default Inventory
Each branch starts with predefined products:
- P001: Laptop (10 units, min: 3)
- P002: Mouse (25 units, min: 5)
- P003: Keyboard (15 units, min: 4)
- P004: Monitor (8 units, min: 2)
- P005: Headphones (12 units, min: 3)

### Automatic Replenishment
- Checks every 30 seconds for low stock
- Requests 2x minimum stock when below threshold
- Broadcasts requests to all connected branches

## Troubleshooting

### Common Issues

1. **Package Declaration Errors**: These are IDE warnings due to the flat src structure. The code will compile and run correctly.

2. **JavaFX Not Found**: Ensure JavaFX is in your module path:
   ```bash
   --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml
   ```

3. **Connection Refused**: Ensure branch servers are started before connecting clients or other branches.

4. **Port Already in Use**: Change port numbers if conflicts occur.

## Extending the System

### Adding New Features
1. **Persistence**: Add database storage for inventory data
2. **Load Balancing**: Implement client request distribution
3. **Fault Recovery**: Add automatic reconnection logic
4. **Security**: Implement authentication and encryption
5. **Web Interface**: Create REST API for web clients

### Performance Optimization
1. **Batch Operations**: Group multiple inventory updates
2. **Compression**: Compress large message payloads
3. **Connection Pooling**: Reuse network connections
4. **Caching**: Cache frequently accessed inventory data

## Dependencies

### Core Java Libraries
- `java.net.*` - Network communication
- `java.io.*` - Serialization and I/O
- `java.util.concurrent.*` - Thread-safe collections and utilities

### External Dependencies (if needed)
- JavaFX SDK - Client GUI framework
- JUnit - Unit testing (for development)

## License

This project is created for educational purposes to demonstrate distributed systems concepts in Java.

## Contributing

This is an educational project. Feel free to:
1. Add new distributed algorithms
2. Improve the GUI interface
3. Add more sophisticated replication strategies
4. Implement additional fault tolerance mechanisms

## Contact

For questions or improvements, please refer to the course materials or contact your instructor. 
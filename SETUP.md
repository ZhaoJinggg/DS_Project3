# Setup Guide - Distributed Inventory System

## Quick Start Guide

### Step 1: Compile the Project

**Windows:**
```cmd
build.bat
```

**Linux/Mac:**
```bash
chmod +x run.sh
./run.sh
```

### Step 2: Start Branch Servers

Open **3 separate terminal windows** and run:

**Terminal 1 - Branch A:**
```cmd
build.bat server BranchA 8001
```

**Terminal 2 - Branch B:**
```cmd
build.bat server BranchB 8002
```

**Terminal 3 - Branch C:**
```cmd
build.bat server BranchC 8003
```

You should see output like:
```
Branch Server 'BranchA' started on port 8001
Client connections on port 8101
Chatroom on port 9001
NetworkManager started on port 8001
Client connection manager started on port 8101
Chatroom server started on port 9001 for branch BranchA
Replication Manager started for node: BranchA
```

### Step 3: Connect Branches (Optional)

To enable inter-branch communication, you can modify the servers to connect to each other. Add this code to the BranchServer constructor or create a separate configuration:

```java
// After server startup, connect branches
if (branchId.equals("BranchA")) {
    connectToBranch("BranchB", "localhost", 8002);
    connectToBranch("BranchC", "localhost", 8003);
}
```

### Step 4: Start Client Application

**New Terminal 4:**
```cmd
build.bat client
```

This will open the JavaFX GUI client.

### Step 5: Connect Client to Server

In the client GUI:
1. Server: `localhost`
2. Port: `8101` (for BranchA)
3. Click "Connect"
4. Click "Refresh" to load inventory

## Testing Scenarios

### Scenario 1: Basic Operations

1. **View Inventory**: Connected client shows all products with quantities
2. **Request Replenishment**: 
   - Enter Product ID: `P001`
   - Enter Quantity: `5`
   - Click "Request Replenishment"
3. **Monitor Status**: Watch the status area for updates

### Scenario 2: Multi-Client Testing

1. Start multiple clients connected to different branches
2. Submit replenishment requests from different clients
3. Observe inter-branch communication in server logs

### Scenario 3: Chat System Testing

Connect to the chatroom using telnet:

```cmd
telnet localhost 9001
```

Type messages and see them broadcast to all connected users.

## Port Reference

| Component | Base Port | BranchA | BranchB | BranchC |
|-----------|-----------|---------|---------|---------|
| Branch Server | 8000+ | 8001 | 8002 | 8003 |
| Client Connections | +100 | 8101 | 8102 | 8103 |
| Chatroom | +1000 | 9001 | 9002 | 9003 |

## Sample Test Flow

### Full System Test

1. **Start all 3 branch servers**
2. **Connect 2 clients** (one to BranchA:8101, one to BranchB:8102)
3. **From BranchA client**: Request 20 units of P001 (Laptop)
4. **Observe**: 
   - BranchA inventory decreases
   - Request sent to BranchB
   - BranchB fulfills request if possible
   - Stock transfer logged and replicated
5. **From BranchB client**: Refresh to see updated inventory

### Expected Behavior

- **Automatic Low Stock Detection**: Every 30 seconds, servers check for low stock
- **Inter-Branch Requests**: When stock is low, requests are sent to other branches
- **Real-time Updates**: Clients receive immediate notifications of stock changes
- **Distributed Locking**: Concurrent updates are handled safely
- **Chat Communication**: Staff can communicate across branches

## Troubleshooting

### Compilation Issues

**Package Declaration Warnings**: Ignore these - they're IDE warnings about package structure but don't affect functionality.

**JavaFX Not Found**: 
- Download JavaFX SDK from https://openjfx.io/
- Update JAVAFX_PATH in build scripts
- Or run with: `java -cp out --module-path /path/to/javafx/lib --add-modules javafx.controls main.Main client`

### Runtime Issues

**Port Already in Use**:
```
java.net.BindException: Address already in use
```
- Change port numbers in the startup commands
- Kill existing processes: `netstat -ano | findstr :8001`

**Connection Refused**:
- Ensure branch servers are started before connecting clients
- Check firewall settings
- Verify correct port numbers

**JavaFX Application Thread Issues**:
- This is normal for JavaFX applications
- The GUI will still function correctly

### Network Issues

**Branches Not Communicating**:
1. Check server logs for connection messages
2. Verify branch connection code is in place
3. Ensure all servers are running on correct ports

**Client Connection Failed**:
1. Verify server is running
2. Check port numbers (use client port, not server port)
3. Try different server IP if not localhost

## Advanced Configuration

### Custom Product Initialization

Modify `InventoryManager.initializeDefaultProducts()` to add your own products:

```java
addProduct(new Product("P006", "Tablet", "10-inch tablet", 299.99, 15, 3));
```

### Adjusting Timers

In `BranchServer.schedulePeriodicTasks()`:
- Change low stock check interval (default: 30 seconds)
- Change heartbeat interval (default: 60 seconds)

### Logging Configuration

Add logging to see detailed system behavior:
```java
System.out.println("Debug: " + message.toString());
```

## Performance Tips

1. **Multiple Clients**: Test with 5-10 clients per branch
2. **Concurrent Requests**: Submit multiple replenishment requests simultaneously
3. **Network Latency**: Add artificial delays to test distributed algorithms
4. **Large Inventories**: Increase the number of products to test scalability

## Next Steps

Once the basic system is working:

1. **Implement Persistence**: Add file or database storage
2. **Add Authentication**: Secure client connections
3. **Enhance GUI**: Improve the JavaFX interface
4. **Add Metrics**: Monitor system performance
5. **Web Interface**: Create a web-based client

## Support

If you encounter issues:

1. Check the console output for error messages
2. Verify all ports are available
3. Ensure Java 11+ is installed
4. Check JavaFX installation for client GUI
5. Review the README.md for additional details 
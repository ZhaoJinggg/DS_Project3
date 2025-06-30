package communication;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles network communication for the distributed system
 */
public class NetworkManager {
    private final String nodeId;
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService executor;
    private final Map<String, NodeConnection> connections;
    private final BlockingQueue<Message> incomingMessages;
    private final BlockingQueue<Message> outgoingMessages;
    private volatile boolean running;

    // Callback interface for message handling
    public interface MessageHandler {
        void handleMessage(Message message);
    }

    private MessageHandler messageHandler;

    public NetworkManager(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.executor = Executors.newCachedThreadPool();
        this.connections = new ConcurrentHashMap<>();
        this.incomingMessages = new LinkedBlockingQueue<>();
        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.running = false;
    }

    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * Start the network manager
     */
    public void start() throws IOException {
        if (running)
            return;

        running = true;
        serverSocket = new ServerSocket(port);

        // Start server thread to accept incoming connections
        executor.submit(this::serverLoop);

        // Start message processing threads
        executor.submit(this::processIncomingMessages);
        executor.submit(() -> {
            try {
                this.processOutgoingMessages();
            } catch (IOException e) {
                // Handle exception (log, rethrow as RuntimeException, etc.)
                e.printStackTrace();
            }
        });

        System.out.println("NetworkManager started on port " + port);
    }

    /**
     * Stop the network manager
     */
    public void stop() {
        if (!running)
            return;

        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

        // Close all connections
        for (NodeConnection connection : connections.values()) {
            connection.close();
        }
        connections.clear();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        System.out.println("NetworkManager stopped");
    }

    /**
     * Connect to another node
     */
    public boolean connectToNode(String remoteNodeId, String host, int remotePort) {
        if (connections.containsKey(remoteNodeId)) {
            return true; // Already connected
        }

        try {
            Socket socket = new Socket(host, remotePort);
            NodeConnection connection = new NodeConnection(remoteNodeId, socket);
            connections.put(remoteNodeId, connection);

            // Send initial connect message
            Message connectMsg = new Message(MessageType.BRANCH_CONNECT, nodeId, remoteNodeId);
            sendMessage(remoteNodeId, connectMsg);

            return true;
        } catch (IOException e) {
            System.err.println("Failed to connect to " + remoteNodeId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Send a message to a specific node
     */
    public void sendMessage(String targetNodeId, Message message) {
        message.setReceiverId(targetNodeId);
        outgoingMessages.offer(message);
    }

    /**
     * Broadcast a message to all connected nodes
     */
    public void broadcastMessage(Message message) {
        for (String nodeId : connections.keySet()) {
            Message copy = createMessageCopy(message);
            copy.setReceiverId(nodeId);
            outgoingMessages.offer(copy);
        }
    }

    /**
     * Get the next incoming message (blocking)
     */
    public Message receiveMessage() throws InterruptedException {
        return incomingMessages.take();
    }

    /**
     * Get the next incoming message with timeout
     */
    public Message receiveMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return incomingMessages.poll(timeout, unit);
    }

    private void serverLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Handle new connection in separate thread
                executor.submit(() -> handleNewConnection(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    private void handleNewConnection(Socket socket) {
        try {
            // For now, we'll identify connections by their address
            // In a real implementation, we'd have a handshake protocol
            String remoteNodeId = socket.getRemoteSocketAddress().toString();
            NodeConnection connection = new NodeConnection(remoteNodeId, socket);
            connections.put(remoteNodeId, connection);
        } catch (IOException e) {
            System.err.println("Error handling new connection: " + e.getMessage());
        }
    }

    private void processIncomingMessages() {
        while (running) {
            for (NodeConnection connection : connections.values()) {
                try {
                    Message message = connection.receiveMessage();
                    if (message != null) {
                        incomingMessages.offer(message);
                        if (messageHandler != null) {
                            messageHandler.handleMessage(message);
                        }
                    }
                } catch (Exception e) {
                    // Connection might be closed or have issues
                    // Handle gracefully
                }
            }

            try {
                Thread.sleep(10); // Small delay to prevent busy waiting
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void processOutgoingMessages() throws IOException {
        while (running) {
            try {
                Message message = outgoingMessages.poll(100, TimeUnit.MILLISECONDS);
                if (message != null) {
                    NodeConnection connection = connections.get(message.getReceiverId());
                    if (connection != null) {
                        connection.sendMessage(message);
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private Message createMessageCopy(Message original) {
        Message copy = new Message(
                original.getType(),
                original.getSenderId(),
                original.getReceiverId(),
                original.getResourceId(),
                original.getTimestamp());
        copy.setData(new ConcurrentHashMap<>(original.getData()));
        return copy;
    }

    /**
     * Get list of connected nodes
     */
    public Set<String> getConnectedNodes() {
        return new HashSet<>(connections.keySet());
    }

    /**
     * Check if connected to a specific node
     */
    public boolean isConnectedTo(String nodeId) {
        return connections.containsKey(nodeId);
    }
}
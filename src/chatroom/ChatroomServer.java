package chatroom;

import communication.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chatroom server for staff communication between branches
 */
public class ChatroomServer {
    private final String branchId;
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService executor;
    private final Map<String, ChatClient> clients;
    private volatile boolean running = false;

    public ChatroomServer(String branchId, int port) {
        this.branchId = branchId;
        this.port = port;
        this.executor = Executors.newCachedThreadPool();
        this.clients = new ConcurrentHashMap<>();
    }

    /**
     * Start the chatroom server
     */
    public void start() throws IOException {
        if (running)
            return;

        running = true;
        serverSocket = new ServerSocket(port);

        // Start accepting chat clients
        executor.submit(this::acceptChatClients);

        System.out.println("Chatroom server started on port " + port + " for branch " + branchId);
    }

    /**
     * Stop the chatroom server
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
            System.err.println("Error closing chatroom server socket: " + e.getMessage());
        }

        // Close all chat clients
        for (ChatClient client : clients.values()) {
            client.close();
        }
        clients.clear();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        System.out.println("Chatroom server stopped");
    }

    private void acceptChatClients() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientId = "user_" + System.currentTimeMillis();
                ChatClient chatClient = new ChatClient(clientId, clientSocket, this);
                clients.put(clientId, chatClient);
                executor.submit(chatClient);

                // Notify other users
                broadcastMessage(new ChatMessage("SYSTEM", clientId + " joined the chat", System.currentTimeMillis()));

                System.out.println("New chat client connected: " + clientId);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting chat client: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Broadcast a message to all connected chat clients
     */
    public void broadcastMessage(ChatMessage message) {
        for (ChatClient client : clients.values()) {
            client.sendMessage(message);
        }
    }

    /**
     * Remove a chat client
     */
    public void removeClient(String clientId) {
        clients.remove(clientId);
        broadcastMessage(new ChatMessage("SYSTEM", clientId + " left the chat", System.currentTimeMillis()));
        System.out.println("Chat client disconnected: " + clientId);
    }

    /**
     * Get list of connected users
     */
    public Set<String> getConnectedUsers() {
        return clients.keySet();
    }

    /**
     * Get branch ID
     */
    public String getBranchId() {
        return branchId;
    }
}
package chatroom;

import java.io.*;
import java.net.Socket;

/**
 * Handles individual chat client connections
 */
public class ChatClient implements Runnable {
    private final String clientId;
    private final Socket socket;
    private final ChatroomServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean running = true;

    public ChatClient(String clientId, Socket socket, ChatroomServer server) {
        this.clientId = clientId;
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Send welcome message
            writer.println("Welcome to " + server.getBranchId() + " chat! You are: " + clientId);

            // Handle incoming messages
            String inputLine;
            while (running && (inputLine = reader.readLine()) != null) {
                if (!inputLine.trim().isEmpty()) {
                    ChatMessage message = new ChatMessage(clientId, inputLine, System.currentTimeMillis());
                    server.broadcastMessage(message);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Error handling chat client " + clientId + ": " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    /**
     * Send a message to this chat client
     */
    public synchronized void sendMessage(ChatMessage message) {
        if (!running || writer == null)
            return;

        try {
            String formattedMessage = String.format("[%s] %s: %s",
                    formatTime(message.getTimestamp()), message.getUsername(), message.getMessage());
            writer.println(formattedMessage);
        } catch (Exception e) {
            System.err.println("Failed to send message to chat client " + clientId + ": " + e.getMessage());
            running = false;
        }
    }

    /**
     * Close the chat client connection
     */
    public void close() {
        running = false;
        server.removeClient(clientId);

        try {
            if (reader != null)
                reader.close();
        } catch (IOException e) {
            /* ignore */ }

        try {
            if (writer != null)
                writer.close();
        } catch (Exception e) {
            /* ignore */ }

        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            /* ignore */ }
    }

    private String formatTime(long timestamp) {
        return java.time.LocalTime.now().toString().substring(0, 8);
    }

    public String getClientId() {
        return clientId;
    }
}
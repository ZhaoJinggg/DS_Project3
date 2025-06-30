package communication;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a connection to a remote node
 */
public class NodeConnection {
    private final String remoteNodeId;
    private final Socket socket;
    private final ObjectInputStream inputStream;
    private final ObjectOutputStream outputStream;
    private final BlockingQueue<Message> messageQueue;
    private final AtomicBoolean connected;

    public NodeConnection(String remoteNodeId, Socket socket) throws IOException {
        this.remoteNodeId = remoteNodeId;
        this.socket = socket;
        this.connected = new AtomicBoolean(true);
        this.messageQueue = new LinkedBlockingQueue<>();

        // Important: Create ObjectOutputStream before ObjectInputStream
        // to avoid blocking during handshake
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.outputStream.flush();
        this.inputStream = new ObjectInputStream(socket.getInputStream());
    }

    /**
     * Send a message to the remote node
     */
    public synchronized void sendMessage(Message message) throws IOException {
        if (!connected.get()) {
            throw new IOException("Connection to " + remoteNodeId + " is closed");
        }

        try {
            outputStream.writeObject(message);
            outputStream.flush();
        } catch (IOException e) {
            connected.set(false);
            throw e;
        }
    }

    /**
     * Receive a message from the remote node (non-blocking)
     */
    public Message receiveMessage() throws IOException, ClassNotFoundException {
        if (!connected.get()) {
            return null;
        }

        try {
            if (inputStream.available() > 0) {
                Object obj = inputStream.readObject();
                if (obj instanceof Message) {
                    return (Message) obj;
                }
            }
        } catch (IOException e) {
            connected.set(false);
            throw e;
        }

        return null;
    }

    /**
     * Close the connection
     */
    public void close() {
        if (!connected.get()) {
            return;
        }

        connected.set(false);

        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Check if connection is still active
     */
    public boolean isConnected() {
        return connected.get() && socket != null && !socket.isClosed();
    }

    /**
     * Get remote node ID
     */
    public String getRemoteNodeId() {
        return remoteNodeId;
    }

    /**
     * Get socket information
     */
    public String getConnectionInfo() {
        if (socket != null) {
            return socket.getRemoteSocketAddress().toString();
        }
        return "Unknown";
    }

    @Override
    public String toString() {
        return String.format("NodeConnection{remoteNodeId='%s', connected=%s, address=%s}",
                remoteNodeId, connected.get(), getConnectionInfo());
    }
}
package server;

import inventory.Product;
import communication.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages connections from client applications to the branch server
 */
public class ClientConnectionManager {
    private final BranchServer branchServer;
    private ServerSocket clientServerSocket;
    private final ExecutorService executor;
    private final Map<String, ClientHandler> clients;
    private volatile boolean running = false;

    public ClientConnectionManager(BranchServer branchServer) {
        this.branchServer = branchServer;
        this.executor = Executors.newCachedThreadPool();
        this.clients = new ConcurrentHashMap<>();
    }

    /**
     * Start accepting client connections
     */
    public void start(int port) throws IOException {
        if (running)
            return;

        running = true;
        clientServerSocket = new ServerSocket(port);

        // Start client acceptance thread
        executor.submit(this::acceptClients);

        System.out.println("Client connection manager started on port " + port);
    }

    /**
     * Stop the client connection manager
     */
    public void stop() {
        if (!running)
            return;

        running = false;

        try {
            if (clientServerSocket != null && !clientServerSocket.isClosed()) {
                clientServerSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing client server socket: " + e.getMessage());
        }

        // Close all client connections
        for (ClientHandler client : clients.values()) {
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

        System.out.println("Client connection manager stopped");
    }

    private void acceptClients() {
        while (running) {
            try {
                Socket clientSocket = clientServerSocket.accept();
                String clientId = "client_" + System.currentTimeMillis();
                ClientHandler handler = new ClientHandler(clientId, clientSocket, this);
                clients.put(clientId, handler);
                executor.submit(handler);

                System.out.println("New client connected: " + clientId);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handle client disconnection
     */
    public void removeClient(String clientId) {
        clients.remove(clientId);
        System.out.println("Client disconnected: " + clientId);
    }

    /**
     * Notify all connected clients of a stock update
     */
    public void notifyStockUpdate(String productId, Product product) {
        if (product == null)
            return;

        Message update = new Message(MessageType.STATUS_UPDATE,
                branchServer.getBranchId(), "");
        update.putData("productId", productId);
        update.putData("quantity", product.getQuantity());
        update.putData("product", product);

        for (ClientHandler client : clients.values()) {
            client.sendMessage(update);
        }
    }

    /**
     * Get the branch server instance
     */
    public BranchServer getBranchServer() {
        return branchServer;
    }

    /**
     * Get number of connected clients
     */
    public int getClientCount() {
        return clients.size();
    }
}

/**
 * Handles individual client connections
 */
class ClientHandler implements Runnable {
    private final String clientId;
    private final Socket socket;
    private final ClientConnectionManager manager;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private volatile boolean running = true;

    public ClientHandler(String clientId, Socket socket, ClientConnectionManager manager) {
        this.clientId = clientId;
        this.socket = socket;
        this.manager = manager;
    }

    @Override
    public void run() {
        try {
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());

            // Send initial connection confirmation
            Message welcome = new Message(MessageType.ACK,
                    manager.getBranchServer().getBranchId(), clientId);
            welcome.putData("message", "Connected to branch " + manager.getBranchServer().getBranchId());
            sendMessage(welcome);

            // Handle client messages
            while (running) {
                try {
                    Object obj = inputStream.readObject();
                    if (obj instanceof Message) {
                        handleClientMessage((Message) obj);
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("Invalid message from client " + clientId);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Connection lost with client " + clientId);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client " + clientId + ": " + e.getMessage());
        } finally {
            close();
        }
    }

    private void handleClientMessage(Message message) {
        switch (message.getType()) {
            case STOCK_QUERY:
                handleStockQuery(message);
                break;
            case REPLENISHMENT_REQUEST:
                handleReplenishmentRequest(message);
                break;
            case CLIENT_DISCONNECT:
                running = false;
                break;
            default:
                System.out.println("Unhandled client message type: " + message.getType());
        }
    }

    private void handleStockQuery(Message message) {
        BranchServer server = manager.getBranchServer();

        if (message.getResourceId() != null) {
            // Query specific product
            Product product = server.getInventoryManager().getProduct(message.getResourceId());
            Message response = new Message(MessageType.STOCK_RESPONSE,
                    server.getBranchId(), clientId);
            response.putData("product", product);
            sendMessage(response);
        } else {
            // Query all products
            Message response = new Message(MessageType.STOCK_RESPONSE,
                    server.getBranchId(), clientId);
            response.putData("products", server.getInventoryManager().getAllProducts());
            sendMessage(response);
        }
    }

    private void handleReplenishmentRequest(Message message) {
        String productId = message.getResourceId();
        Integer quantity = message.getIntData("quantity");

        if (productId != null && quantity != null && quantity > 0) {
            BranchServer server = manager.getBranchServer();
            server.requestStockReplenishment(productId, quantity);

            Message response = new Message(MessageType.REPLENISHMENT_RESPONSE,
                    server.getBranchId(), clientId);
            response.putData("status", "Request submitted");
            response.putData("productId", productId);
            response.putData("quantity", quantity);
            sendMessage(response);
        }
    }

    public synchronized void sendMessage(Message message) {
        if (!running || outputStream == null)
            return;

        try {
            outputStream.writeObject(message);
            outputStream.flush();
        } catch (IOException e) {
            System.err.println("Failed to send message to client " + clientId + ": " + e.getMessage());
            running = false;
        }
    }

    public void close() {
        running = false;
        manager.removeClient(clientId);

        try {
            if (inputStream != null)
                inputStream.close();
        } catch (IOException e) {
            /* ignore */ }

        try {
            if (outputStream != null)
                outputStream.close();
        } catch (IOException e) {
            /* ignore */ }

        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            /* ignore */ }
    }
}
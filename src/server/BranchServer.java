package server;

import inventory.InventoryManager;
import communication.*;
import distributed.*;
import chatroom.ChatroomServer;
import replication.ReplicationManager;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main server class for a branch in the distributed inventory system
 */
public class BranchServer implements NetworkManager.MessageHandler {
    private final String branchId;
    private final int port;
    private final InventoryManager inventoryManager;
    private final NetworkManager networkManager;
    private final LamportClock lamportClock;
    private final RicartAgrawalaMutex mutex;
    private final ClientConnectionManager clientManager;
    private final ChatroomServer chatroomServer;
    private final ReplicationManager replicationManager;
    private final ScheduledExecutorService scheduler;
    private final Set<String> knownBranches;

    private volatile boolean running = false;

    public BranchServer(String branchId, int port) {
        this.branchId = branchId;
        this.port = port;
        this.inventoryManager = new InventoryManager(branchId);
        this.networkManager = new NetworkManager(branchId, port);
        this.lamportClock = new LamportClock();
        this.knownBranches = new HashSet<>();
        this.mutex = new RicartAgrawalaMutex(branchId, knownBranches, lamportClock);
        this.clientManager = new ClientConnectionManager(this);
        this.chatroomServer = new ChatroomServer(branchId, port + 1000);
        this.replicationManager = new ReplicationManager(branchId, networkManager, lamportClock);
        this.scheduler = Executors.newScheduledThreadPool(4);

        // Set up callbacks
        networkManager.setMessageHandler(this);
        mutex.setMessageSender(networkManager::sendMessage);
    }

    /**
     * Start the branch server
     */
    public void start() {
        try {
            running = true;

            // Start network manager
            networkManager.start();

            // Start client connection manager
            clientManager.start(port + 100);

            // Start chatroom server
            chatroomServer.start();

            // Start replication manager
            replicationManager.start();

            // Schedule periodic tasks
            schedulePeriodicTasks();

            System.out.println("Branch Server '" + branchId + "' started on port " + port);
            System.out.println("Client connections on port " + (port + 100));
            System.out.println("Chatroom on port " + (port + 1000));

            // Keep server running
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        } catch (IOException e) {
            System.err.println("Failed to start branch server: " + e.getMessage());
            stop();
        }
    }

    /**
     * Stop the branch server
     */
    public void stop() {
        if (!running)
            return;

        running = false;

        System.out.println("Stopping Branch Server '" + branchId + "'...");

        // Stop all components
        scheduler.shutdown();
        networkManager.stop();
        clientManager.stop();
        chatroomServer.stop();
        replicationManager.stop();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        System.out.println("Branch Server '" + branchId + "' stopped");
    }

    /**
     * Connect to another branch
     */
    public boolean connectToBranch(String otherBranchId, String host, int otherPort) {
        if (knownBranches.contains(otherBranchId)) {
            return true; // Already known
        }

        boolean connected = networkManager.connectToNode(otherBranchId, host, otherPort);
        if (connected) {
            knownBranches.add(otherBranchId);
            System.out.println("Connected to branch: " + otherBranchId);
        }
        return connected;
    }

    /**
     * Handle incoming messages from other branches
     */
    @Override
    public void handleMessage(Message message) {
        lamportClock.update(message.getTimestamp());

        switch (message.getType()) {
            case BRANCH_CONNECT:
                handleBranchConnect(message);
                break;
            case STOCK_TRANSFER_REQUEST:
                handleStockTransferRequest(message);
                break;
            case STOCK_TRANSFER_RESPONSE:
                handleStockTransferResponse(message);
                break;
            case MUTEX_REQUEST:
            case MUTEX_REPLY:
                handleMutexMessage(message);
                break;
            case SYNC_REQUEST:
            case LOG_ENTRY:
                replicationManager.handleMessage(message);
                break;
            case PING:
                handlePing(message);
                break;
            default:
                System.out.println("Unhandled message type: " + message.getType());
        }
    }

    private void handleBranchConnect(Message message) {
        String otherBranchId = message.getSenderId();
        if (!knownBranches.contains(otherBranchId)) {
            knownBranches.add(otherBranchId);
            System.out.println("New branch connected: " + otherBranchId);

            Message ack = new Message(MessageType.ACK, branchId, otherBranchId);
            networkManager.sendMessage(otherBranchId, ack);
        }
    }

    private void handleStockTransferRequest(Message message) {
        String productId = message.getResourceId();
        Integer requestedQuantity = message.getIntData("quantity");
        String requestingBranch = message.getSenderId();

        if (productId != null && requestedQuantity != null) {
            boolean canFulfill = inventoryManager.transferStock(
                    productId, requestedQuantity, branchId, requestingBranch);

            Message response = new Message(MessageType.STOCK_TRANSFER_RESPONSE,
                    branchId, requestingBranch, productId, lamportClock.tick());
            response.putData("quantity", requestedQuantity);
            response.putData("approved", canFulfill);

            networkManager.sendMessage(requestingBranch, response);

            if (canFulfill) {
                System.out.println("Approved stock transfer: " + requestedQuantity +
                        " units of " + productId + " to " + requestingBranch);
            }
        }
    }

    private void handleStockTransferResponse(Message message) {
        String productId = message.getResourceId();
        Integer quantity = message.getIntData("quantity");
        Boolean approved = message.getData("approved", Boolean.class);

        if (productId != null && quantity != null && approved != null && approved) {
            inventoryManager.receiveStock(productId, quantity);
            System.out.println("Received stock transfer: " + quantity +
                    " units of " + productId + " from " + message.getSenderId());

            clientManager.notifyStockUpdate(productId, inventoryManager.getProduct(productId));
        }
    }

    private void handleMutexMessage(Message message) {
        Message response = mutex.handleMessage(message);
        if (response != null) {
            networkManager.sendMessage(message.getSenderId(), response);
        }
    }

    private void handlePing(Message message) {
        Message pong = new Message(MessageType.PONG, branchId, message.getSenderId());
        networkManager.sendMessage(message.getSenderId(), pong);
    }

    /**
     * Request stock from other branches for low inventory
     */
    public void requestStockReplenishment(String productId, int quantity) {
        if (knownBranches.isEmpty()) {
            System.out.println("No other branches available for replenishment");
            return;
        }

        Message request = new Message(MessageType.STOCK_TRANSFER_REQUEST,
                branchId, "", productId, lamportClock.tick());
        request.putData("quantity", quantity);

        for (String otherBranch : knownBranches) {
            Message copy = new Message(request.getType(), request.getSenderId(),
                    otherBranch, request.getResourceId(), request.getTimestamp());
            copy.setData(request.getData());
            networkManager.sendMessage(otherBranch, copy);
        }

        System.out.println("Requested " + quantity + " units of " + productId +
                " from other branches");
    }

    private void schedulePeriodicTasks() {
        scheduler.scheduleAtFixedRate(this::checkLowStock, 30, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 60, 60, TimeUnit.SECONDS);
    }

    private void checkLowStock() {
        inventoryManager.getLowStockProducts().forEach(product -> {
            int neededQuantity = product.getMinimumStock() * 2 - product.getQuantity();
            if (neededQuantity > 0) {
                requestStockReplenishment(product.getProductId(), neededQuantity);
            }
        });
    }

    private void sendHeartbeat() {
        Message heartbeat = new Message(MessageType.BRANCH_HEARTBEAT, branchId, "");
        heartbeat.putData("timestamp", System.currentTimeMillis());
        networkManager.broadcastMessage(heartbeat);
    }

    // Getters
    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public LamportClock getLamportClock() {
        return lamportClock;
    }

    public RicartAgrawalaMutex getMutex() {
        return mutex;
    }

    public String getBranchId() {
        return branchId;
    }

    public Set<String> getKnownBranches() {
        return new HashSet<>(knownBranches);
    }
}
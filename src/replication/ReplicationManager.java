package replication;

import communication.*;
import distributed.LamportClock;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages replication of operations across branch servers
 */
public class ReplicationManager {
    private final String nodeId;
    private final NetworkManager networkManager;
    private final LamportClock lamportClock;
    private final ConcurrentLinkedQueue<LogEntry> logEntries;
    private final Map<String, Long> lastSyncTimestamps;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public ReplicationManager(String nodeId, NetworkManager networkManager, LamportClock lamportClock) {
        this.nodeId = nodeId;
        this.networkManager = networkManager;
        this.lamportClock = lamportClock;
        this.logEntries = new ConcurrentLinkedQueue<>();
        this.lastSyncTimestamps = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    /**
     * Start the replication manager
     */
    public void start() {
        if (running)
            return;

        running = true;

        // Schedule periodic synchronization
        scheduler.scheduleAtFixedRate(this::performPeriodicSync, 10, 10, TimeUnit.SECONDS);

        System.out.println("Replication Manager started for node: " + nodeId);
    }

    /**
     * Stop the replication manager
     */
    public void stop() {
        if (!running)
            return;

        running = false;
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        System.out.println("Replication Manager stopped");
    }

    /**
     * Log an operation for replication
     */
    public void logOperation(String operation, String resourceId, Object data) {
        LogEntry entry = new LogEntry(
                nodeId,
                lamportClock.tick(),
                operation,
                resourceId,
                data);

        logEntries.offer(entry);

        // Broadcast to other nodes
        broadcastLogEntry(entry);
    }

    /**
     * Handle incoming replication messages
     */
    public void handleMessage(Message message) {
        switch (message.getType()) {
            case LOG_ENTRY:
                handleLogEntry(message);
                break;
            case SYNC_REQUEST:
                handleSyncRequest(message);
                break;
            case LOG_ACK:
                handleLogAck(message);
                break;
        }
    }

    private void handleLogEntry(Message message) {
        LogEntry entry = message.getData("logEntry", LogEntry.class);
        if (entry != null && !entry.getNodeId().equals(nodeId)) {
            // Apply the log entry
            applyLogEntry(entry);

            // Send acknowledgment
            Message ack = new Message(MessageType.LOG_ACK, nodeId, message.getSenderId());
            ack.putData("timestamp", entry.getTimestamp());
            networkManager.sendMessage(message.getSenderId(), ack);
        }
    }

    private void handleSyncRequest(Message message) {
        Long fromTimestamp = message.getData("fromTimestamp", Long.class);
        if (fromTimestamp != null) {
            // Send all log entries after the requested timestamp
            for (LogEntry entry : logEntries) {
                if (entry.getTimestamp() > fromTimestamp) {
                    Message logMsg = new Message(MessageType.LOG_ENTRY, nodeId, message.getSenderId());
                    logMsg.putData("logEntry", entry);
                    networkManager.sendMessage(message.getSenderId(), logMsg);
                }
            }
        }
    }

    private void handleLogAck(Message message) {
        Long timestamp = message.getData("timestamp", Long.class);
        if (timestamp != null) {
            lastSyncTimestamps.put(message.getSenderId(), timestamp);
        }
    }

    private void broadcastLogEntry(LogEntry entry) {
        Message logMessage = new Message(MessageType.LOG_ENTRY, nodeId, "");
        logMessage.putData("logEntry", entry);
        networkManager.broadcastMessage(logMessage);
    }

    private void applyLogEntry(LogEntry entry) {
        // This would apply the operation locally
        // Implementation depends on the specific operation type
        System.out.println("Applying log entry: " + entry);
    }

    private void performPeriodicSync() {
        if (!running)
            return;

        // Request synchronization from other nodes
        for (String nodeId : networkManager.getConnectedNodes()) {
            Long lastSync = lastSyncTimestamps.get(nodeId);
            long fromTimestamp = (lastSync != null) ? lastSync : 0;

            Message syncRequest = new Message(MessageType.SYNC_REQUEST, this.nodeId, nodeId);
            syncRequest.putData("fromTimestamp", fromTimestamp);
            networkManager.sendMessage(nodeId, syncRequest);
        }
    }

    /**
     * Get the current log size
     */
    public int getLogSize() {
        return logEntries.size();
    }

    /**
     * Get replication status
     */
    public Map<String, Long> getSyncStatus() {
        return new ConcurrentHashMap<>(lastSyncTimestamps);
    }
}
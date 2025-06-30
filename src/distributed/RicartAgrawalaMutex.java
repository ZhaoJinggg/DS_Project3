package distributed;

import communication.Message;
import communication.MessageType;
import communication.NetworkManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the Ricart-Agrawala distributed mutual exclusion algorithm.
 * This algorithm ensures that only one process can enter the critical section
 * at a time
 * across all distributed nodes using logical timestamps and message passing.
 */
public class RicartAgrawalaMutex {
    private final String nodeId;
    private final NetworkManager networkManager;
    private final LamportClock lamportClock;
    private final Set<String> allNodes;

    // State management
    private final AtomicBoolean requestingCS = new AtomicBoolean(false);
    private final AtomicBoolean inCriticalSection = new AtomicBoolean(false);
    private volatile long requestTimestamp = 0;

    // Reply tracking
    private final Map<String, Boolean> repliesReceived = new ConcurrentHashMap<>();
    private final Set<String> deferredReplies = ConcurrentHashMap.newKeySet();
    private CountDownLatch replyLatch;

    // Thread safety
    private final ReentrantLock stateLock = new ReentrantLock();

    // Statistics
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger grantCount = new AtomicInteger(0);

    /**
     * Constructor for RicartAgrawalaMutex
     * 
     * @param nodeId         Unique identifier for this node
     * @param networkManager Network communication manager
     * @param lamportClock   Logical clock for timestamp ordering
     * @param allNodes       Set of all participating nodes
     */
    public RicartAgrawalaMutex(String nodeId, NetworkManager networkManager,
            LamportClock lamportClock, Set<String> allNodes) {
        this.nodeId = nodeId;
        this.networkManager = networkManager;
        this.lamportClock = lamportClock;
        this.allNodes = new HashSet<>(allNodes);
        this.allNodes.remove(nodeId); // Remove self from the set

        // Initialize replies map
        for (String node : this.allNodes) {
            repliesReceived.put(node, false);
        }

        System.out.println("[" + nodeId + "] RicartAgrawalaMutex initialized with nodes: " + this.allNodes);
    }

    /**
     * Request access to the critical section
     * 
     * @param timeoutSeconds Maximum time to wait for access
     * @return true if access granted, false if timeout
     */
    public boolean requestCriticalSection(int timeoutSeconds) {
        stateLock.lock();
        try {
            if (inCriticalSection.get()) {
                System.out.println("[" + nodeId + "] Already in critical section");
                return true;
            }

            if (requestingCS.get()) {
                System.out.println("[" + nodeId + "] Already requesting critical section");
                return false;
            }

            requestingCS.set(true);
            requestTimestamp = lamportClock.tick();
            requestCount.incrementAndGet();

            System.out.println("[" + nodeId + "] Requesting critical section with timestamp: " + requestTimestamp);

            // Reset reply tracking
            replyLatch = new CountDownLatch(allNodes.size());
            for (String node : allNodes) {
                repliesReceived.put(node, false);
            }

        } finally {
            stateLock.unlock();
        }

        // Send REQUEST messages to all other nodes
        broadcastRequest();

        // Wait for all replies
        try {
            boolean allRepliesReceived = replyLatch.await(timeoutSeconds, TimeUnit.SECONDS);

            if (allRepliesReceived) {
                stateLock.lock();
                try {
                    inCriticalSection.set(true);
                    grantCount.incrementAndGet();
                    System.out.println("[" + nodeId + "] Entered critical section at timestamp: " + requestTimestamp);
                    return true;
                } finally {
                    stateLock.unlock();
                }
            } else {
                System.out.println("[" + nodeId + "] Timeout waiting for critical section access");
                requestingCS.set(false);
                return false;
            }
        } catch (InterruptedException e) {
            System.out.println("[" + nodeId + "] Interrupted while waiting for critical section");
            requestingCS.set(false);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Release the critical section
     */
    public void releaseCriticalSection() {
        stateLock.lock();
        try {
            if (!inCriticalSection.get()) {
                System.out.println("[" + nodeId + "] Not in critical section, cannot release");
                return;
            }

            inCriticalSection.set(false);
            requestingCS.set(false);

            System.out.println("[" + nodeId + "] Released critical section");

            // Send deferred replies
            sendDeferredReplies();

        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Handle incoming Ricart-Agrawala messages
     * 
     * @param message The received message
     */
    public void handleMessage(Message message) {
        lamportClock.update(message.getTimestamp());

        switch (message.getType()) {
            case MUTEX_REQUEST:
                handleRequest(message);
                break;
            case MUTEX_REPLY:
                handleReply(message);
                break;
            default:
                // Not a mutex message, ignore
                break;
        }
    }

    /**
     * Handle REQUEST message from another node
     */
    private void handleRequest(Message message) {
        String senderId = message.getSenderId();
        long senderTimestamp = message.getTimestamp();

        System.out
                .println("[" + nodeId + "] Received REQUEST from " + senderId + " with timestamp: " + senderTimestamp);

        boolean shouldReplyImmediately = true;

        stateLock.lock();
        try {
            // Reply immediately if:
            // 1. Not requesting critical section, OR
            // 2. Requesting but sender has earlier timestamp, OR
            // 3. Same timestamp but sender has lexicographically smaller nodeId
            if (requestingCS.get()) {
                if (senderTimestamp < requestTimestamp ||
                        (senderTimestamp == requestTimestamp && senderId.compareTo(nodeId) < 0)) {
                    // Sender has priority, reply immediately
                    shouldReplyImmediately = true;
                } else {
                    // We have priority, defer reply
                    shouldReplyImmediately = false;
                    deferredReplies.add(senderId);
                    System.out.println("[" + nodeId + "] Deferring reply to " + senderId);
                }
            }
        } finally {
            stateLock.unlock();
        }

        if (shouldReplyImmediately) {
            sendReply(senderId);
        }
    }

    /**
     * Handle REPLY message from another node
     */
    private void handleReply(Message message) {
        String senderId = message.getSenderId();

        System.out.println("[" + nodeId + "] Received REPLY from " + senderId);

        stateLock.lock();
        try {
            if (requestingCS.get() && repliesReceived.containsKey(senderId)) {
                if (!repliesReceived.get(senderId)) {
                    repliesReceived.put(senderId, true);
                    if (replyLatch != null) {
                        replyLatch.countDown();
                    }
                    System.out.println("[" + nodeId + "] Reply count: " +
                            (allNodes.size() - replyLatch.getCount()) + "/" + allNodes.size());
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Broadcast REQUEST message to all other nodes
     */
    private void broadcastRequest() {
        Message requestMessage = new Message(
                MessageType.MUTEX_REQUEST,
                nodeId,
                "",
                "CRITICAL_SECTION_REQUEST",
                lamportClock.getTime());

        for (String node : allNodes) {
            try {
                networkManager.sendMessage(node, requestMessage);
                System.out.println("[" + nodeId + "] Sent REQUEST to " + node);
            } catch (Exception e) {
                System.err.println("[" + nodeId + "] Failed to send REQUEST to " + node + ": " + e.getMessage());
                // Mark as received to avoid deadlock
                stateLock.lock();
                try {
                    repliesReceived.put(node, true);
                    if (replyLatch != null) {
                        replyLatch.countDown();
                    }
                } finally {
                    stateLock.unlock();
                }
            }
        }
    }

    /**
     * Send REPLY message to a specific node
     */
    private void sendReply(String targetNode) {
        Message replyMessage = new Message(
                MessageType.MUTEX_REPLY,
                nodeId,
                targetNode,
                "CRITICAL_SECTION_REPLY",
                lamportClock.tick());

        try {
            networkManager.sendMessage(targetNode, replyMessage);
            System.out.println("[" + nodeId + "] Sent REPLY to " + targetNode);
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to send REPLY to " + targetNode + ": " + e.getMessage());
        }
    }

    /**
     * Send all deferred replies
     */
    private void sendDeferredReplies() {
        Set<String> toReply = new HashSet<>(deferredReplies);
        deferredReplies.clear();

        for (String node : toReply) {
            sendReply(node);
        }

        if (!toReply.isEmpty()) {
            System.out.println("[" + nodeId + "] Sent " + toReply.size() + " deferred replies");
        }
    }

    /**
     * Check if currently in critical section
     */
    public boolean isInCriticalSection() {
        return inCriticalSection.get();
    }

    /**
     * Check if currently requesting critical section
     */
    public boolean isRequestingCriticalSection() {
        return requestingCS.get();
    }

    /**
     * Get current request timestamp
     */
    public long getRequestTimestamp() {
        return requestTimestamp;
    }

    /**
     * Get mutex statistics
     */
    public String getStatistics() {
        return String.format("[%s] Mutex Stats - Requests: %d, Grants: %d, InCS: %s, Requesting: %s, Deferred: %d",
                nodeId, requestCount.get(), grantCount.get(),
                inCriticalSection.get(), requestingCS.get(), deferredReplies.size());
    }

    /**
     * Cleanup resources
     */
    public void shutdown() {
        stateLock.lock();
        try {
            if (inCriticalSection.get()) {
                releaseCriticalSection();
            }

            deferredReplies.clear();
            repliesReceived.clear();

            if (replyLatch != null) {
                // Release any waiting threads
                while (replyLatch.getCount() > 0) {
                    replyLatch.countDown();
                }
            }

            System.out.println("[" + nodeId + "] RicartAgrawalaMutex shutdown completed");
        } finally {
            stateLock.unlock();
        }
    }
}
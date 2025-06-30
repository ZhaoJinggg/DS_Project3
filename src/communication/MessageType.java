package communication;

/**
 * Enumeration of message types used in the distributed inventory system
 */
public enum MessageType {
    // Client-Server messages
    CLIENT_CONNECT,
    CLIENT_DISCONNECT,
    STOCK_QUERY,
    STOCK_RESPONSE,
    REPLENISHMENT_REQUEST,
    REPLENISHMENT_RESPONSE,
    STATUS_UPDATE,

    // Branch-to-Branch messages
    BRANCH_CONNECT,
    BRANCH_DISCONNECT,
    BRANCH_HEARTBEAT,
    STOCK_TRANSFER_REQUEST,
    STOCK_TRANSFER_RESPONSE,
    STOCK_TRANSFER_CONFIRM,

    // Distributed Mutex messages (Ricart-Agrawala)
    MUTEX_REQUEST,
    MUTEX_REPLY,

    // Synchronization messages
    SYNC_REQUEST,
    SYNC_RESPONSE,
    LOG_ENTRY,
    LOG_ACK,

    // Chatroom messages
    CHAT_MESSAGE,
    CHAT_USER_JOIN,
    CHAT_USER_LEAVE,
    CHAT_USER_LIST,

    // System messages
    ERROR,
    ACK,
    PING,
    PONG
}
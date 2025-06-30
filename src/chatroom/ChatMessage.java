package chatroom;

import java.io.Serializable;

/**
 * Represents a chat message in the chatroom system
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String message;
    private long timestamp;

    public ChatMessage(String username, String message, long timestamp) {
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("ChatMessage{user=%s, message=%s, timestamp=%d}",
                username, message, timestamp);
    }
}
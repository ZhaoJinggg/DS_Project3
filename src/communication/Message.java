package communication;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;

/**
 * Message class for communication between nodes in the distributed system
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private MessageType type;
    private String senderId;
    private String receiverId;
    private String resourceId;
    private long timestamp;
    private Map<String, Object> data;

    public Message(MessageType type, String senderId, String receiverId) {
        this(type, senderId, receiverId, null, System.currentTimeMillis());
    }

    public Message(MessageType type, String senderId, String receiverId, String resourceId, long timestamp) {
        this.type = type;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.resourceId = resourceId;
        this.timestamp = timestamp;
        this.data = new HashMap<>();
    }

    // Getters and setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    // Convenience methods for data
    public void putData(String key, Object value) {
        data.put(key, value);
    }

    public Object getData(String key) {
        return data.get(key);
    }

    public <T> T getData(String key, Class<T> type) {
        Object value = data.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    public Integer getIntData(String key) {
        return getData(key, Integer.class);
    }

    public String getStringData(String key) {
        return getData(key, String.class);
    }

    @Override
    public String toString() {
        return String.format("Message{type=%s, from=%s, to=%s, resource=%s, timestamp=%d, data=%s}",
                type, senderId, receiverId, resourceId, timestamp, data);
    }
}
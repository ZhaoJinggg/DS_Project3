package replication;

import java.io.Serializable;

/**
 * Represents a log entry in the replication system
 */
public class LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private String nodeId;
    private long timestamp;
    private String operation;
    private String resourceId;
    private Object data;

    public LogEntry(String nodeId, long timestamp, String operation, String resourceId, Object data) {
        this.nodeId = nodeId;
        this.timestamp = timestamp;
        this.operation = operation;
        this.resourceId = resourceId;
        this.data = data;
    }

    // Getters and setters
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return String.format("LogEntry{node=%s, timestamp=%d, operation=%s, resource=%s}",
                nodeId, timestamp, operation, resourceId);
    }
}
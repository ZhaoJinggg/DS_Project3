package distributed;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of Lamport logical clock for distributed event ordering
 */
public class LamportClock {
    private final AtomicLong clock;

    public LamportClock() {
        this.clock = new AtomicLong(0);
    }

    /**
     * Increment clock for local events
     * 
     * @return new timestamp
     */
    public long tick() {
        return clock.incrementAndGet();
    }

    /**
     * Update clock when receiving a message
     * 
     * @param receivedTimestamp timestamp from received message
     * @return updated local timestamp
     */
    public long update(long receivedTimestamp) {
        long currentTime = clock.get();
        long newTime = Math.max(currentTime, receivedTimestamp) + 1;
        clock.set(newTime);
        return newTime;
    }

    /**
     * Get current timestamp without incrementing
     * 
     * @return current timestamp
     */
    public long getTime() {
        return clock.get();
    }

    /**
     * Set clock to specific value (mainly for testing)
     * 
     * @param time new timestamp value
     */
    public void setTime(long time) {
        clock.set(time);
    }

    @Override
    public String toString() {
        return "LamportClock{time=" + clock.get() + "}";
    }
}
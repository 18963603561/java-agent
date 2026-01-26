package com.example.agent.history;

import java.time.Instant;

/**
 * 时间线摘要记录。
 */
public class TimelineRecord {

    private String eventId;
    private String type;
    private Instant timestamp;

    public TimelineRecord() {
    }

    public TimelineRecord(String eventId, String type, Instant timestamp) {
        this.eventId = eventId;
        this.type = type;
        this.timestamp = timestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

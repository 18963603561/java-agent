package com.example.agent.domain.event;

/**
 * 事件流游标，支持断线续传。
 */
public class StreamCursor {

    private String streamId;
    private long seq;
    private String eventId;

    public StreamCursor() {
    }

    public StreamCursor(String streamId, long seq, String eventId) {
        this.streamId = streamId;
        this.seq = seq;
        this.eventId = eventId;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}

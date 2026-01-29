package com.example.agent.domain.event;

/**
 * 事件流游标，支持断线续传。
 */
public class StreamCursor {

    /**
     * 事件流标识。
     */
    private String streamId;
    /**
     * 流内序号，用于续传定位。
     */
    private long seq;
    /**
     * 最近事件标识。
     */
    private String eventId;

    /**
     * 空构造方法，便于序列化。
     */
    public StreamCursor() {
    }

    /**
     * 构造事件流游标。
     *
     * @param streamId 事件流标识
     * @param seq 流内序号
     * @param eventId 最近事件标识
     */
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

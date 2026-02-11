package com.example.agent.orchestration.multiagent.dag.replay;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * DAG 回放帧。
 *
 * <p>用途：表达某一条回放事件及其解析后的关键信息。</p>
 */
public class DagReplayFrame {

    private String eventId;
    private String eventType;
    private long seq;
    private Instant timestamp;
    private String nodeId;
    private Integer attempt;
    private String reason;
    private Map<String, Object> payload = new HashMap<>();

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new HashMap<>() : new HashMap<>(payload);
    }
}


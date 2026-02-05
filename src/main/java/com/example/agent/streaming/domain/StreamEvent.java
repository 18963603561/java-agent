package com.example.agent.streaming.domain;

import java.time.Instant;
import java.util.Map;

/**
 * 事件流实体，用于 SSE 与持久化的统一载体。
 */
public class StreamEvent {

    /**
     * 事件唯一标识。
     */
    private String eventId;
    /**
     * 事件结构版本。
     */
    private String schemaVersion;
    /**
     * 工作流标识。
     */
    private String workflowId;
    /**
     * 事件类型。
     */
    private EventType type;
    /**
     * 智能体标识。
     */
    private String agentId;
    /**
     * 事件消息摘要。
     */
    private String message;
    /**
     * 事件时间。
     */
    private Instant timestamp;
    /**
     * 流内序号。
     */
    private long seq;
    /**
     * 事件流标识。
     */
    private String streamId;
    /**
     * 租户标识。
     */
    private String tenantId;
    /**
     * 事件负载数据。
     */
    private Map<String, Object> payload;

    /**
     * 空构造方法，便于序列化。
     */
    public StreamEvent() {
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}

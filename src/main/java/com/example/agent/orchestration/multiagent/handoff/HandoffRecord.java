package com.example.agent.orchestration.multiagent.handoff;

import java.time.Instant;
import java.util.Map;

/**
 * 交接记录。
 *
 * <p>用途：记录交接链路的主体、上下文快照、状态与失败原因，满足追溯要求。</p>
 */
public class HandoffRecord {

    private String handoffId;
    private String fromAgent;
    private String toAgent;
    /**
     * 当前交接状态。
     */
    private HandoffStatus status;
    /**
     * 版本号。
     */
    private long version;
    /**
     * 幂等键。
     */
    private String idempotencyKey;
    /**
     * 错误码。
     */
    private String errorCode;
    private String failureReason;
    private Map<String, Object> context;
    private Instant createdAt;
    private Instant updatedAt;

    public String getHandoffId() {
        return handoffId;
    }

    public void setHandoffId(String handoffId) {
        this.handoffId = handoffId;
    }

    public String getFromAgent() {
        return fromAgent;
    }

    public void setFromAgent(String fromAgent) {
        this.fromAgent = fromAgent;
    }

    public String getToAgent() {
        return toAgent;
    }

    public void setToAgent(String toAgent) {
        this.toAgent = toAgent;
    }

    public HandoffStatus getStatus() {
        return status;
    }

    public void setStatus(HandoffStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

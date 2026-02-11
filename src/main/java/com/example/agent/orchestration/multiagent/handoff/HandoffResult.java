package com.example.agent.orchestration.multiagent.handoff;

/**
 * 交接结果。
 */
public class HandoffResult {

    private String handoffId;
    /**
     * 交接状态。
     */
    private HandoffStatus status;
    private String reason;
    /**
     * 当前记录版本。
     */
    private long version;

    public String getHandoffId() {
        return handoffId;
    }

    public void setHandoffId(String handoffId) {
        this.handoffId = handoffId;
    }

    public HandoffStatus getStatus() {
        return status;
    }

    public void setStatus(HandoffStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}

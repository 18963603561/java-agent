package com.example.agent.multiagent;

import java.time.Instant;

/**
 * 智能体交接记录。
 */
public class HandoffRecord {

    private String handoffId;
    private String fromAgent;
    private String toAgent;
    private Instant createdAt;

    public HandoffRecord() {
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

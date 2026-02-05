package com.example.agent.capabilities.tools.hook;

import java.util.Map;

/**
 * Hook 决策结果。
 */
public class HookDecision {

    private boolean allowed;
    private String reason;
    private Map<String, Object> metadata;

    public HookDecision() {
    }

    public HookDecision(boolean allowed, String reason, Map<String, Object> metadata) {
        this.allowed = allowed;
        this.reason = reason;
        this.metadata = metadata;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}

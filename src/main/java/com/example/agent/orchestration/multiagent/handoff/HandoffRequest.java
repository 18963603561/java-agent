package com.example.agent.orchestration.multiagent.handoff;

import java.util.Map;

/**
 * 交接请求。
 *
 * <p>用途：描述从一个角色向另一个角色转交上下文时的输入数据。</p>
 */
public class HandoffRequest {

    private String fromAgent;
    private String toAgent;
    /**
     * 幂等键。
     *
     * <p>用途：标识同一交接意图的重复请求，避免重试导致重复写入。</p>
     */
    private String idempotencyKey;
    private Map<String, Object> context;
    private Map<String, Object> permissions;

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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Map<String, Object> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Object> permissions) {
        this.permissions = permissions;
    }
}

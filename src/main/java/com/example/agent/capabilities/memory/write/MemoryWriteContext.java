package com.example.agent.capabilities.memory.write;

import org.springframework.util.StringUtils;

/**
 * 记忆写入上下文快照，统一承载写入开关与请求关键字段。
 */
public class MemoryWriteContext {

    /**
     * 写入是否开启。
     */
    private final boolean enabled;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 会话标识。
     */
    private final String sessionId;

    public MemoryWriteContext(boolean enabled, String workflowId, String sessionId) {
        this.enabled = enabled;
        this.workflowId = workflowId;
        this.sessionId = sessionId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * 判断会话标识是否有效。
     */
    public boolean hasValidSession() {
        return StringUtils.hasText(sessionId);
    }
}


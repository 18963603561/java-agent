package com.example.agent.capabilities.context.compression.application.model;

import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.security.auth.TenantContext;

/**
 * LLM 压缩命令。
 *
 * <p>用途：承载 LLM 压缩编排所需的最小输入，隔离上游压缩域模型与下游模型调用细节。</p>
 */
public class LlmCompressionCommand {

    /**
     * 上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 租户上下文。
     */
    private TenantContext tenantContext;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 会话标识。
     */
    private String sessionId;

    /**
     * 触发原因。
     */
    private String triggerReason;

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }

    public void setTenantContext(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }
}


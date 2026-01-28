package com.example.agent.budget;

import com.example.agent.auth.TenantContext;
import com.example.agent.context.ContextSnapshot;

/**
 * 上下文压缩请求，用于携带触发所需的上下文与预算信息。
 */
public class ContextCompressionRequest {

    /**
     * 上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 预算分配结果。
     */
    private ContextBudgetAllocation allocation;

    /**
     * 裁剪报告，用于判断触发条件。
     */
    private ContextTrimReport trimReport;

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

    public ContextCompressionRequest() {
    }

    public ContextCompressionRequest(ContextSnapshot snapshot,
                                     ContextBudgetAllocation allocation,
                                     ContextTrimReport trimReport,
                                     TenantContext tenantContext,
                                     String workflowId,
                                     String sessionId) {
        this.snapshot = snapshot;
        this.allocation = allocation;
        this.trimReport = trimReport;
        this.tenantContext = tenantContext;
        this.workflowId = workflowId;
        this.sessionId = sessionId;
    }

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public ContextBudgetAllocation getAllocation() {
        return allocation;
    }

    public void setAllocation(ContextBudgetAllocation allocation) {
        this.allocation = allocation;
    }

    public ContextTrimReport getTrimReport() {
        return trimReport;
    }

    public void setTrimReport(ContextTrimReport trimReport) {
        this.trimReport = trimReport;
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
}

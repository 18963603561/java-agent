package com.example.agent.capabilities.context.assembly;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.budget.trim.model.ContextPruneResult;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.capabilities.context.model.ContextSnapshot;

/**
 * 上下文装配命令对象。
 *
 * <p>用途：统一承载提示词装配所需参数，避免接口长参数导致的语义漂移与调用错位。
 */
public class ContextAssemblyCommand {

    /**
     * 上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 预算分配结果。
     */
    private ContextBudgetAllocation allocation = ContextBudgetAllocation.EMPTY;

    /**
     * 裁剪报告。
     */
    private ContextTrimReport trimReport;

    /**
     * 剪枝结果。
     */
    private ContextPruneResult pruneResult;

    /**
     * 压缩结果。
     */
    private ContextCompressionResult compressionResult;

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 用户输入文本。
     */
    private String userText;

    /**
     * 创建命令构建器。
     *
     * @return 构建器
     */
    public static Builder builder() {
        return new Builder();
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
        this.allocation = allocation == null ? ContextBudgetAllocation.EMPTY : allocation;
    }

    public ContextTrimReport getTrimReport() {
        return trimReport;
    }

    public void setTrimReport(ContextTrimReport trimReport) {
        this.trimReport = trimReport;
    }

    public ContextPruneResult getPruneResult() {
        return pruneResult;
    }

    public void setPruneResult(ContextPruneResult pruneResult) {
        this.pruneResult = pruneResult;
    }

    public ContextCompressionResult getCompressionResult() {
        return compressionResult;
    }

    public void setCompressionResult(ContextCompressionResult compressionResult) {
        this.compressionResult = compressionResult;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getUserText() {
        return userText;
    }

    public void setUserText(String userText) {
        this.userText = userText;
    }

    /**
     * 上下文装配命令构建器。
     */
    public static class Builder {

        private final ContextAssemblyCommand command = new ContextAssemblyCommand();

        public Builder snapshot(ContextSnapshot snapshot) {
            command.setSnapshot(snapshot);
            return this;
        }

        public Builder allocation(ContextBudgetAllocation allocation) {
            command.setAllocation(allocation);
            return this;
        }

        public Builder trimReport(ContextTrimReport trimReport) {
            command.setTrimReport(trimReport);
            return this;
        }

        public Builder pruneResult(ContextPruneResult pruneResult) {
            command.setPruneResult(pruneResult);
            return this;
        }

        public Builder compressionResult(ContextCompressionResult compressionResult) {
            command.setCompressionResult(compressionResult);
            return this;
        }

        public Builder tenantId(String tenantId) {
            command.setTenantId(tenantId);
            return this;
        }

        public Builder workflowId(String workflowId) {
            command.setWorkflowId(workflowId);
            return this;
        }

        public Builder userText(String userText) {
            command.setUserText(userText);
            return this;
        }

        public ContextAssemblyCommand build() {
            return command;
        }
    }
}




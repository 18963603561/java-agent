package com.example.agent.capabilities.context.runtime;

/**
 * 上下文读取诊断上下文。
 *
 * <p>用途：在类型化读取失败时补充租户、工作流、任务与阶段信息，提升问题定位效率。
 */
public class ContextRuntimeReadContext {

    /**
     * 租户标识。
     */
    private final String tenantId;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 任务标识。
     */
    private final String taskId;

    /**
     * 读取阶段。
     */
    private final String stage;

    /**
     * 构造读取诊断上下文。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param stage 读取阶段
     */
    public ContextRuntimeReadContext(String tenantId,
                                     String workflowId,
                                     String taskId,
                                     String stage) {
        this.tenantId = tenantId;
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.stage = stage;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getStage() {
        return stage;
    }
}


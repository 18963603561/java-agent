package com.example.agent.governance.replay.domain;

/**
 * 回放任务快照。
 */
public class ReplayTaskSnapshot {

    /**
     * 任务标识。
     */
    private final String taskId;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 任务状态。
     */
    private final String status;

    public ReplayTaskSnapshot(String taskId, String workflowId, String status) {
        this.taskId = taskId;
        this.workflowId = workflowId;
        this.status = status;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getStatus() {
        return status;
    }
}


package com.example.agent.runtime;

/**
 * 执行控制状态响应。
 */
public class ExecutionControlStateResponse {

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 当前状态。
     */
    private ExecutionControlState state;

    /**
     * 决策内容，可选。
     */
    private String decision;

    public ExecutionControlStateResponse() {
    }

    public ExecutionControlStateResponse(String workflowId, ExecutionControlState state, String decision) {
        this.workflowId = workflowId;
        this.state = state;
        this.decision = decision;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public ExecutionControlState getState() {
        return state;
    }

    public void setState(ExecutionControlState state) {
        this.state = state;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}

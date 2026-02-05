package com.example.agent.governance.replay;

/**
 * 回放请求。
 */
public class ReplayRequest {

    private String taskId;
    private String fromStepId;
    private String toStepId;
    private String mode;

    public ReplayRequest() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getFromStepId() {
        return fromStepId;
    }

    public void setFromStepId(String fromStepId) {
        this.fromStepId = fromStepId;
    }

    public String getToStepId() {
        return toStepId;
    }

    public void setToStepId(String toStepId) {
        this.toStepId = toStepId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}

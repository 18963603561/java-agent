package com.example.agent.governance.replay.domain;

/**
 * 回放领域命令。
 */
public class ReplayCommand {

    private String taskId;
    private String fromStepId;
    private String toStepId;
    private String mode;

    public ReplayCommand() {
    }

    public ReplayCommand(String taskId, String fromStepId, String toStepId, String mode) {
        this.taskId = taskId;
        this.fromStepId = fromStepId;
        this.toStepId = toStepId;
        this.mode = mode;
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


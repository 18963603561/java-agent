package com.example.agent.orchestration.multiagent.dag.actor.control;

import java.time.Instant;

/**
 * DAG 控制结果。
 *
 * <p>用途：返回控制指令执行结果与摘要信息。</p>
 */
public class DagControlResult {

    private String workflowId;
    private String dagRunId;
    private String command;
    private String status;
    private String message;
    private boolean deduplicated;
    private Instant executedAt;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getDagRunId() {
        return dagRunId;
    }

    public void setDagRunId(String dagRunId) {
        this.dagRunId = dagRunId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isDeduplicated() {
        return deduplicated;
    }

    public void setDeduplicated(boolean deduplicated) {
        this.deduplicated = deduplicated;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}

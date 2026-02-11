package com.example.agent.orchestration.multiagent.dag.actor.control;

/**
 * DAG 控制命令。
 *
 * <p>用途：描述 pause/resume/rebalance/recover 等控制动作请求。</p>
 */
public class DagControlCommand {

    private String workflowId;
    private String dagRunId;
    private String command;
    private String idempotencyKey;

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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}


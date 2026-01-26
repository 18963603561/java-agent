package com.example.agent.runtime;

import java.time.Instant;
import java.util.Map;

/**
 * 运行时步骤记录，记录步骤输入输出与状态变化。
 */
public class StepRecord {

    /**
     * 步骤标识。
     */
    private String stepId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 步骤序列号。
     */
    private long stepSeq;

    /**
     * 步骤类型。
     */
    private String type;

    /**
     * 步骤状态。
     */
    private StepState status;

    /**
     * 尝试次数。
     */
    private int attempt;

    /**
     * 输入内容。
     */
    private Map<String, Object> input;

    /**
     * 输出内容。
     */
    private Map<String, Object> output;

    /**
     * 错误码。
     */
    private String errorCode;

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 开始时间。
     */
    private Instant startedAt;

    /**
     * 结束时间。
     */
    private Instant completedAt;

    public StepRecord() {
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public long getStepSeq() {
        return stepSeq;
    }

    public void setStepSeq(long stepSeq) {
        this.stepSeq = stepSeq;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public StepState getStatus() {
        return status;
    }

    public void setStatus(StepState status) {
        this.status = status;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

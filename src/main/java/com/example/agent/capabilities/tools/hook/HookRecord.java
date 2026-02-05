package com.example.agent.capabilities.tools.hook;

import java.time.Instant;

/**
 * Hook 执行记录，用于审计与排查。
 */
public class HookRecord {

    private String hookId;
    private HookType hookType;
    private String toolName;
    private String stepId;
    private String tenantId;
    private boolean allowed;
    private String reason;
    /**
     * 执行开始时间。
     */
    private Instant startedAt;
    /**
     * 执行结束时间。
     */
    private Instant endedAt;
    /**
     * 执行耗时（毫秒）。
     */
    private long durationMs;
    /**
     * 执行结果，ALLOW/BLOCK 等。
     */
    private String result;
    /**
     * 是否超时。
     */
    private boolean timeout;
    private Instant executedAt;

    public HookRecord() {
    }

    public String getHookId() {
        return hookId;
    }

    public void setHookId(String hookId) {
        this.hookId = hookId;
    }

    public HookType getHookType() {
        return hookType;
    }

    public void setHookType(HookType hookType) {
        this.hookType = hookType;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public void setTimeout(boolean timeout) {
        this.timeout = timeout;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}

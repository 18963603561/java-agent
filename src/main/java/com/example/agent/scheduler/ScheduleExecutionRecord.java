package com.example.agent.scheduler;

import java.time.Instant;

/**
 * 调度执行记录。
 */
public class ScheduleExecutionRecord {

    private String executionId;
    private String scheduleId;
    private String status;
    private Instant startedAt;
    private Instant endedAt;
    private Double costUsd;
    private Integer tokenUsage;
    private String tenantId;

    public ScheduleExecutionRecord() {
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Double getCostUsd() {
        return costUsd;
    }

    public void setCostUsd(Double costUsd) {
        this.costUsd = costUsd;
    }

    public Integer getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(Integer tokenUsage) {
        this.tokenUsage = tokenUsage;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}

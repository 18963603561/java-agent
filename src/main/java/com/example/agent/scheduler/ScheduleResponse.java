package com.example.agent.scheduler;

/**
 * 调度响应。
 */
public class ScheduleResponse {

    private String scheduleId;
    private String status;

    public ScheduleResponse() {
    }

    public ScheduleResponse(String scheduleId, String status) {
        this.scheduleId = scheduleId;
        this.status = status;
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
}

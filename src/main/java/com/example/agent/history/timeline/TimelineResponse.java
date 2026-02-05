package com.example.agent.history.timeline;

import java.util.List;
import java.util.Map;
import com.example.agent.history.eventlog.EventLogRecord;

/**
 * 时间线响应结构。
 */
public class TimelineResponse {

    private String workflowId;
    private String mode;
    private List<EventLogRecord> events;
    private Map<String, Object> stats;

    public TimelineResponse() {
    }

    public TimelineResponse(String workflowId, String mode, List<EventLogRecord> events,
                            Map<String, Object> stats) {
        this.workflowId = workflowId;
        this.mode = mode;
        this.events = events;
        this.stats = stats;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<EventLogRecord> getEvents() {
        return events;
    }

    public void setEvents(List<EventLogRecord> events) {
        this.events = events;
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    public void setStats(Map<String, Object> stats) {
        this.stats = stats;
    }
}

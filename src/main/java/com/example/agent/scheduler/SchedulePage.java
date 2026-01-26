package com.example.agent.scheduler;

import java.util.List;

/**
 * 调度分页结果。
 */
public class SchedulePage {

    private List<ScheduleResponse> schedules;
    private String nextCursor;
    private boolean hasMore;

    public SchedulePage() {
    }

    public SchedulePage(List<ScheduleResponse> schedules, String nextCursor, boolean hasMore) {
        this.schedules = schedules;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<ScheduleResponse> getSchedules() {
        return schedules;
    }

    public void setSchedules(List<ScheduleResponse> schedules) {
        this.schedules = schedules;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}

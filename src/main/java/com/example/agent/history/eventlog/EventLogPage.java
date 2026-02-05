package com.example.agent.history.eventlog;

import java.util.List;

/**
 * 事件日志分页结果。
 */
public class EventLogPage {

    private List<EventLogRecord> events;
    private String nextCursor;
    private boolean hasMore;

    public EventLogPage() {
    }

    public EventLogPage(List<EventLogRecord> events, String nextCursor, boolean hasMore) {
        this.events = events;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<EventLogRecord> getEvents() {
        return events;
    }

    public void setEvents(List<EventLogRecord> events) {
        this.events = events;
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

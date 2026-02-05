package com.example.agent.api.http.dto;

import java.util.List;

/**
 * 任务列表响应结构。
 */
public class TaskListResponse {

    private List<TaskStatusResponse> tasks;
    private String nextCursor;
    private boolean hasMore;
    private long total;

    public TaskListResponse() {
    }

    public TaskListResponse(List<TaskStatusResponse> tasks, String nextCursor, boolean hasMore, long total) {
        this.tasks = tasks;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.total = total;
    }

    public List<TaskStatusResponse> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskStatusResponse> tasks) {
        this.tasks = tasks;
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

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}

package com.example.agent.orchestration.task.contract;

import java.util.List;

/**
 * 任务列表视图。
 * <p>用途：表达任务列表查询的编排层输出结构。
 * <p>输入：任务列表、下一页游标、是否有更多、总数。
 * <p>输出：供适配层转换为接口返回对象。
 * <p>边界：tasks 允许为空列表，表示当前页无数据。
 */
public class TaskListView {

    /**
     * 当前页任务列表。
     */
    private List<TaskStatusView> tasks;

    /**
     * 下一页游标。
     */
    private String nextCursor;

    /**
     * 是否有更多数据。
     */
    private boolean hasMore;

    /**
     * 查询总数。
     */
    private long total;

    public TaskListView() {
    }

    public TaskListView(List<TaskStatusView> tasks, String nextCursor, boolean hasMore, long total) {
        this.tasks = tasks;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.total = total;
    }

    public List<TaskStatusView> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskStatusView> tasks) {
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


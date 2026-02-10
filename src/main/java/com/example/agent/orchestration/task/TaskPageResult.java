package com.example.agent.orchestration.task;

import java.util.List;

/**
 * 任务分页查询结果。
 * <p>用途：承载仓储层分页查询输出，提供统一的翻页与统计信息。
 * <p>输入：当前页任务、下一页游标、是否有更多、筛选总数。
 */
public class TaskPageResult {

    /**
     * 当前页任务记录。
     */
    private final List<TaskRecord> tasks;

    /**
     * 下一页游标。
     */
    private final String nextCursor;

    /**
     * 是否存在下一页。
     */
    private final boolean hasMore;

    /**
     * 筛选后的总数量。
     */
    private final long total;

    public TaskPageResult(List<TaskRecord> tasks, String nextCursor, boolean hasMore, long total) {
        this.tasks = tasks;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.total = total;
    }

    public List<TaskRecord> getTasks() {
        return tasks;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public long getTotal() {
        return total;
    }
}


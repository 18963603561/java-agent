package com.example.agent.runtime;

import java.util.List;

/**
 * 步骤时间线响应结构。
 */
public class StepTimelineResponse {

    private String workflowId;
    private List<StepRecord> steps;
    private String nextCursor;
    private boolean hasMore;

    public StepTimelineResponse() {
    }

    public StepTimelineResponse(String workflowId, List<StepRecord> steps, String nextCursor, boolean hasMore) {
        this.workflowId = workflowId;
        this.steps = steps;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public List<StepRecord> getSteps() {
        return steps;
    }

    public void setSteps(List<StepRecord> steps) {
        this.steps = steps;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    /**
     * 获取是否仍有更多步骤数据。
     *
     * @return 是否还有更多数据
     */
    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}

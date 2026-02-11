package com.example.agent.orchestration.multiagent.dag.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 回放响应。
 *
 * <p>用途：承载回放帧、游标和汇总统计，供接口直接返回。</p>
 */
public class DagReplayResponse {

    private String workflowId;
    private String dagRunId;
    private String fromCursor;
    private String nextCursor;
    private boolean hasMore;
    private List<DagReplayFrame> frames = new ArrayList<>();
    private Map<String, Object> summary = new HashMap<>();

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getDagRunId() {
        return dagRunId;
    }

    public void setDagRunId(String dagRunId) {
        this.dagRunId = dagRunId;
    }

    public String getFromCursor() {
        return fromCursor;
    }

    public void setFromCursor(String fromCursor) {
        this.fromCursor = fromCursor;
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

    public List<DagReplayFrame> getFrames() {
        return frames;
    }

    public void setFrames(List<DagReplayFrame> frames) {
        this.frames = frames == null ? new ArrayList<>() : new ArrayList<>(frames);
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary == null ? new HashMap<>() : new HashMap<>(summary);
    }
}


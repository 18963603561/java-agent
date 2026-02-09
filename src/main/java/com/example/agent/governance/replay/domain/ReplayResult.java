package com.example.agent.governance.replay.domain;

import java.time.Instant;

/**
 * 回放领域结果。
 */
public class ReplayResult {

    private String replayId;
    private String status;
    private Instant startedAt;
    private Instant completedAt;

    public ReplayResult() {
    }

    public ReplayResult(String replayId, String status, Instant startedAt, Instant completedAt) {
        this.replayId = replayId;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public String getReplayId() {
        return replayId;
    }

    public void setReplayId(String replayId) {
        this.replayId = replayId;
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

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}


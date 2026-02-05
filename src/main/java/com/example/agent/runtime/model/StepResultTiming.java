package com.example.agent.runtime.model;

import java.time.Instant;

/**
 * 步骤时间信息。
 */
public class StepResultTiming {

    private Instant startedAt;
    private Instant endedAt;
    private Long durationMs;

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}

package com.example.agent.governance.replay.domain;

import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.runtime.step.StepRecord;
import java.time.Instant;

/**
 * 回放项，统一表示事件记录与步骤记录。
 */
public class ReplayItem {

    private final Instant timestamp;
    private final long originalSeq;
    private final EventLogRecord eventRecord;
    private final StepRecord stepRecord;

    private ReplayItem(Instant timestamp, long originalSeq, EventLogRecord eventRecord, StepRecord stepRecord) {
        this.timestamp = timestamp;
        this.originalSeq = originalSeq;
        this.eventRecord = eventRecord;
        this.stepRecord = stepRecord;
    }

    public static ReplayItem fromEvent(EventLogRecord record) {
        Instant ts = record.getTimestamp() != null ? record.getTimestamp() : Instant.EPOCH;
        return new ReplayItem(ts, parseSeq(record.getEventId()), record, null);
    }

    public static ReplayItem fromStep(StepRecord step) {
        Instant ts = step.getCompletedAt() != null ? step.getCompletedAt()
                : (step.getStartedAt() != null ? step.getStartedAt() : Instant.EPOCH);
        return new ReplayItem(ts, step.getStepSeq(), null, step);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getOriginalSeq() {
        return originalSeq;
    }

    public EventLogRecord getEventRecord() {
        return eventRecord;
    }

    public StepRecord getStepRecord() {
        return stepRecord;
    }

    private static long parseSeq(String eventId) {
        if (eventId == null) {
            return 0;
        }
        int index = eventId.lastIndexOf(':');
        if (index < 0 || index == eventId.length() - 1) {
            return 0;
        }
        try {
            return Long.parseLong(eventId.substring(index + 1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}


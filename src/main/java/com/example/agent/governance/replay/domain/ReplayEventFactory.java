package com.example.agent.governance.replay.domain;

import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepState;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 回放事件构建器。
 */
@Component
public class ReplayEventFactory {

    /**
     * 构建回放生命周期事件。
     *
     * @param replayStreamId 回放流标识
     * @param tenantId 租户标识
     * @param seqCounter 序列计数器
     * @param replayId 回放标识
     * @param type 事件类型
     * @return 流事件
     */
    public StreamEvent buildLifecycleEvent(String replayStreamId,
                                           String tenantId,
                                           AtomicLong seqCounter,
                                           String replayId,
                                           EventType type) {
        long seq = seqCounter.incrementAndGet();
        StreamEvent event = new StreamEvent();
        event.setEventId(replayStreamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(replayStreamId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(replayStreamId);
        event.setTenantId(tenantId);
        event.setPayload(Map.of("replayId", replayId));
        return event;
    }

    /**
     * 构建回放项事件。
     *
     * @param replayStreamId 回放流标识
     * @param tenantId 租户标识
     * @param seqCounter 序列计数器
     * @param timestamp 事件时间
     * @param eventRecord 事件日志记录
     * @param stepRecord 步骤记录
     * @return 流事件
     */
    public StreamEvent buildReplayItemEvent(String replayStreamId,
                                            String tenantId,
                                            AtomicLong seqCounter,
                                            Instant timestamp,
                                            EventLogRecord eventRecord,
                                            StepRecord stepRecord) {
        long seq = seqCounter.incrementAndGet();
        StreamEvent event = new StreamEvent();
        event.setEventId(replayStreamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(replayStreamId);
        event.setTimestamp(timestamp);
        event.setSeq(seq);
        event.setStreamId(replayStreamId);
        event.setTenantId(tenantId);
        if (eventRecord != null) {
            event.setType(resolveEventType(eventRecord.getType()));
            Map<String, Object> payload = new HashMap<>();
            payload.put("originalEventId", eventRecord.getEventId());
            payload.put("payload", eventRecord.getPayload());
            event.setPayload(payload);
            return event;
        }
        if (stepRecord != null) {
            event.setType(resolveStepEvent(stepRecord));
            Map<String, Object> payload = new HashMap<>();
            payload.put("stepId", stepRecord.getStepId());
            payload.put("stepSeq", stepRecord.getStepSeq());
            payload.put("status", stepRecord.getStatus() != null ? stepRecord.getStatus().name() : null);
            payload.put("type", stepRecord.getType());
            payload.put("attempt", stepRecord.getAttempt());
            if (stepRecord.getErrorCode() != null) {
                payload.put("errorCode", stepRecord.getErrorCode());
            }
            if (stepRecord.getOutput() != null) {
                payload.put("output", stepRecord.getOutput());
            }
            event.setPayload(payload);
            return event;
        }
        event.setType(EventType.ERROR_OCCURRED);
        event.setPayload(Map.of("error", "replay_event_missing"));
        return event;
    }

    private EventType resolveEventType(String type) {
        if (type == null) {
            return EventType.ERROR_OCCURRED;
        }
        try {
            return EventType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            return EventType.ERROR_OCCURRED;
        }
    }

    private EventType resolveStepEvent(StepRecord step) {
        StepState state = step.getStatus();
        if (state == StepState.FAILED) {
            return EventType.STEP_FAILED;
        }
        if (state == StepState.COMPLETED) {
            return EventType.STEP_COMPLETED;
        }
        return EventType.STEP_STARTED;
    }
}


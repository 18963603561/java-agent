package com.example.agent.governance.replay;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.api.http.dto.TaskStatusResponse;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.history.eventlog.EventLogRepository;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.orchestration.task.TaskQueryService;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 回放服务，用于重放历史事件与步骤。
 */
@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private final TaskQueryService taskQueryService;
    private final EventLogRepository eventLogRepository;
    private final StepRuntimeService stepRuntimeService;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final MetricsPublisher metricsPublisher;

    private final ConcurrentHashMap<String, ReplaySession> sessions = new ConcurrentHashMap<>();

    public ReplayService(TaskQueryService taskQueryService,
                         EventLogRepository eventLogRepository,
                         StepRuntimeService stepRuntimeService,
                         ApplicationEventPublisher eventPublisher,
                         EventStreamService eventStreamService,
                         MetricsPublisher metricsPublisher) {
        this.taskQueryService = taskQueryService;
        this.eventLogRepository = eventLogRepository;
        this.stepRuntimeService = stepRuntimeService;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 执行回放。
     *
     * @param request 回放请求
     * @param tenantContext 租户上下文
     * @return 回放响应
     */
    public ReplayResponse replay(ReplayRequest request, TenantContext tenantContext) {
        TaskStatusResponse task = resolveTask(request, tenantContext);

        String replayId = UUID.randomUUID().toString();
        ReplaySession session = new ReplaySession();
        session.setReplayId(replayId);
        session.setTaskId(request.getTaskId());
        session.setStatus("RUNNING");
        session.setTenantId(tenantContext.getTenantId());
        session.setStartedAt(Instant.now());
        sessions.put(replayId, session);

        metricsPublisher.increment("replay.count");

        String replayStreamId = "replay-" + replayId;
        AtomicLong seqCounter = eventStreamService.sequenceCounter(tenantContext.getTenantId(), replayStreamId);
        publishReplayEvent(tenantContext, replayStreamId, seqCounter, replayId, EventType.REPLAY_STARTED);

        List<ReplayItem> items = buildReplayItems(tenantContext, task.getWorkflowId());
        log.info("回放开始, tenantId={}, replayId={}, workflowId={}, items={}",
                tenantContext.getTenantId(), replayId, task.getWorkflowId(), items.size());
        for (ReplayItem item : items) {
            StreamEvent event = item.toStreamEvent(replayStreamId, tenantContext.getTenantId(), seqCounter);
            eventPublisher.publishEvent(event);
        }

        session.setStatus("COMPLETED");
        session.setCompletedAt(Instant.now());
        publishReplayEvent(tenantContext, replayStreamId, seqCounter, replayId, EventType.REPLAY_COMPLETED);

        log.info("回放完成, tenantId={}, replayId={}, taskId={}",
                tenantContext.getTenantId(), replayId, request.getTaskId());
        return new ReplayResponse(replayId, "COMPLETED", session.getStartedAt(), session.getCompletedAt());
    }

    private TaskStatusResponse resolveTask(ReplayRequest request, TenantContext tenantContext) {
        try {
            TaskStatusResponse task = taskQueryService.getTask(request.getTaskId(), tenantContext);
            if (task == null) {
                throw new ErrorCodeException(HttpStatus.NOT_FOUND, "REPLAY_NOT_FOUND", "回放任务不存在");
            }
            return task;
        } catch (RuntimeException ex) {
            logReplayNotFound(tenantContext, request.getTaskId());
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "REPLAY_NOT_FOUND", "回放任务不存在");
        }
    }

    private List<ReplayItem> buildReplayItems(TenantContext tenantContext, String workflowId) {
        List<ReplayItem> items = new ArrayList<>();
        List<EventLogRecord> events = eventLogRepository.findByWorkflow(tenantContext.getTenantId(), workflowId);
        for (EventLogRecord record : events) {
            items.add(ReplayItem.fromEvent(record));
        }
        List<StepRecord> steps = stepRuntimeService.getSteps(workflowId, tenantContext);
        for (StepRecord step : steps) {
            items.add(ReplayItem.fromStep(step));
        }
        items.sort((left, right) -> {
            long leftSeq = left.getOriginalSeq();
            long rightSeq = right.getOriginalSeq();
            if (leftSeq > 0 && rightSeq > 0 && leftSeq != rightSeq) {
                return Long.compare(leftSeq, rightSeq);
            }
            return left.getTimestamp().compareTo(right.getTimestamp());
        });
        return items;
    }

    private void publishReplayEvent(TenantContext tenantContext,
                                    String replayStreamId,
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
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(Map.of("replayId", replayId));
        eventPublisher.publishEvent(event);
    }

    private void logReplayNotFound(TenantContext tenantContext, String taskId) {
        log.warn("REPLAY_NOT_FOUND, tenantId={}, userId={}, traceId={}, requestId={}, taskId={}",
                tenantContext.getTenantId(),
                tenantContext.getUserId(),
                tenantContext.getTraceId(),
                tenantContext.getRequestId(),
                taskId);
    }

    private static class ReplayItem {
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

        private static ReplayItem fromEvent(EventLogRecord record) {
            Instant ts = record.getTimestamp() != null ? record.getTimestamp() : Instant.EPOCH;
            return new ReplayItem(ts, parseSeq(record.getEventId()), record, null);
        }

        private static ReplayItem fromStep(StepRecord step) {
            Instant ts = step.getCompletedAt() != null ? step.getCompletedAt()
                    : (step.getStartedAt() != null ? step.getStartedAt() : Instant.EPOCH);
            long seq = step.getStepSeq();
            return new ReplayItem(ts, seq, null, step);
        }

        private Instant getTimestamp() {
            return timestamp;
        }

        private long getOriginalSeq() {
            return originalSeq;
        }

        private StreamEvent toStreamEvent(String replayStreamId, String tenantId, AtomicLong seqCounter) {
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

        private static EventType resolveEventType(String type) {
            if (type == null) {
                return EventType.ERROR_OCCURRED;
            }
            try {
                return EventType.valueOf(type);
            } catch (IllegalArgumentException ex) {
                return EventType.ERROR_OCCURRED;
            }
        }

        private static EventType resolveStepEvent(StepRecord step) {
            StepState state = step.getStatus();
            if (state == StepState.FAILED) {
                return EventType.STEP_FAILED;
            }
            if (state == StepState.COMPLETED) {
                return EventType.STEP_COMPLETED;
            }
            return EventType.STEP_STARTED;
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
}

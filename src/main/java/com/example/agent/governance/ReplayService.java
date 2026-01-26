package com.example.agent.governance;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.common.TaskStatusResponse;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.history.EventLogRecord;
import com.example.agent.history.EventLogRepository;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.runtime.StepRecord;
import com.example.agent.runtime.StepRuntimeService;
import com.example.agent.streaming.EventStreamService;
import com.example.agent.orchestrator.TaskQueryService;
import java.time.Instant;
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
        TaskStatusResponse task;
        try {
            task = taskQueryService.getTask(request.getTaskId(), tenantContext);
        } catch (RuntimeException ex) {
            logReplayNotFound(tenantContext, request.getTaskId());
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "REPLAY_NOT_FOUND", "回放任务不存在");
        }
        if (task == null) {
            logReplayNotFound(tenantContext, request.getTaskId());
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "REPLAY_NOT_FOUND", "回放任务不存在");
        }

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
        List<EventLogRecord> events = eventLogRepository.findByWorkflow(
                tenantContext.getTenantId(), task.getWorkflowId());
        for (EventLogRecord record : events) {
            publishReplayedEvent(tenantContext, replayStreamId, seqCounter, record);
        }
        List<StepRecord> steps = stepRuntimeService.getSteps(task.getWorkflowId(), tenantContext);
        for (StepRecord step : steps) {
            publishReplayedStep(tenantContext, replayStreamId, seqCounter, step);
        }

        session.setStatus("COMPLETED");
        session.setCompletedAt(Instant.now());
        publishReplayEvent(tenantContext, replayStreamId, seqCounter, replayId, EventType.REPLAY_COMPLETED);

        log.info("回放完成, tenantId={}, replayId={}, taskId={}",
                tenantContext.getTenantId(), replayId, request.getTaskId());
        return new ReplayResponse(replayId, "COMPLETED", session.getStartedAt(), session.getCompletedAt());
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

    private void publishReplayedEvent(TenantContext tenantContext,
                                      String replayStreamId,
                                      AtomicLong seqCounter,
                                      EventLogRecord record) {
        long seq = seqCounter.incrementAndGet();
        StreamEvent event = new StreamEvent();
        event.setEventId(replayStreamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(replayStreamId);
        event.setType(EventType.valueOf(record.getType()));
        event.setTimestamp(record.getTimestamp());
        event.setSeq(seq);
        event.setStreamId(replayStreamId);
        event.setTenantId(tenantContext.getTenantId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("originalEventId", record.getEventId());
        payload.put("payload", record.getPayload());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }

    private void publishReplayedStep(TenantContext tenantContext,
                                     String replayStreamId,
                                     AtomicLong seqCounter,
                                     StepRecord step) {
        long seq = seqCounter.incrementAndGet();
        StreamEvent event = new StreamEvent();
        event.setEventId(replayStreamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(replayStreamId);
        event.setType(EventType.STEP_COMPLETED);
        event.setTimestamp(step.getCompletedAt() != null ? step.getCompletedAt() : Instant.now());
        event.setSeq(seq);
        event.setStreamId(replayStreamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(Map.of("stepId", step.getStepId(), "stepSeq", step.getStepSeq()));
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
}

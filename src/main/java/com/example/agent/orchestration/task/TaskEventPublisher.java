package com.example.agent.orchestration.task;

import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 任务事件发布器。
 * <p>用途：统一构建与发布任务事件，收敛 trace 注入、序列号与 payload 结构。
 */
@Service
public class TaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TaskEventPublisher.class);

    public static final String TASK_REPOSITORY_FAILURE_REASON = "task_repository_failure";

    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final MetricsPublisher metricsPublisher;
    private final TracingPublisher tracingPublisher;

    public TaskEventPublisher(ApplicationEventPublisher eventPublisher,
                              EventStreamService eventStreamService,
                              MetricsPublisher metricsPublisher,
                              TracingPublisher tracingPublisher) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.metricsPublisher = metricsPublisher;
        this.tracingPublisher = tracingPublisher;
    }

    /**
     * 发布任务受理事件。
     */
    public void publishTaskAccepted(TenantContext tenantContext, TaskRecord record) {
        if (record == null) {
            return;
        }
        AtomicLong seqCounter = eventStreamService.sequenceCounter(record.getTenantId(), record.getWorkflowId());
        long seq = seqCounter.incrementAndGet();
        publishEvent(buildEvent(tenantContext, record.getWorkflowId(), EventType.TASK_ACCEPTED, seq,
                Map.of("message", "task accepted", "taskId", record.getTaskId())));
        metricsPublisher.increment("task.submit.count", resolveTraceId(tenantContext));
    }

    /**
     * 发布工作流开始事件。
     */
    public void publishWorkflowStarted(TenantContext tenantContext,
                                       String workflowId,
                                       AtomicLong seqCounter) {
        long seq = seqCounter.incrementAndGet();
        publishEvent(buildEvent(tenantContext, workflowId, EventType.WORKFLOW_STARTED, seq,
                Map.of("message", "workflow started")));
    }

    /**
     * 发布工作流完成事件。
     */
    public void publishWorkflowCompleted(TenantContext tenantContext,
                                         String workflowId,
                                         AtomicLong seqCounter,
                                         String finalStatus,
                                         long durationMs) {
        long seq = seqCounter.incrementAndGet();
        publishEvent(buildEvent(tenantContext, workflowId, EventType.WORKFLOW_COMPLETED, seq,
                Map.of("status", finalStatus)));
        metricsPublisher.recordTime("task.duration.ms", durationMs, resolveTraceId(tenantContext));
    }

    /**
     * 发布执行异常事件。
     */
    public Map<String, Object> publishExecutionError(TenantContext tenantContext,
                                                     String workflowId,
                                                     AtomicLong seqCounter,
                                                     Throwable ex) {
        String errorMessage = ex != null && ex.getMessage() != null ? ex.getMessage() : "route_failed";
        long errorSeq = seqCounter.incrementAndGet();
        Map<String, Object> payload = Map.of("error", errorMessage);
        publishEvent(buildEvent(tenantContext, workflowId, EventType.ERROR_OCCURRED, errorSeq, payload));
        return payload;
    }

    /**
     * 发布仓储失败事件。
     */
    public Map<String, Object> publishRepositoryError(TenantContext tenantContext,
                                                       String workflowId,
                                                       String taskId,
                                                       String operation,
                                                       AtomicLong seqCounter) {
        if (tenantContext == null || !StringUtils.hasText(workflowId)) {
            return Map.of(
                    "error", TASK_REPOSITORY_FAILURE_REASON,
                    "errorCode", TASK_REPOSITORY_FAILURE_REASON,
                    "operation", operation
            );
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("error", TASK_REPOSITORY_FAILURE_REASON);
        payload.put("errorCode", TASK_REPOSITORY_FAILURE_REASON);
        payload.put("operation", operation);
        if (StringUtils.hasText(taskId)) {
            payload.put("taskId", taskId);
        }
        long seq = seqCounter != null
                ? seqCounter.incrementAndGet()
                : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        publishEvent(buildEvent(tenantContext, workflowId, EventType.ERROR_OCCURRED, seq, payload));
        return payload;
    }

    /**
     * 构建任务受理结果。
     */
    public TaskSubmissionResult buildAcceptedResult(TaskRecord record, boolean forceRunning) {
        TaskSubmissionResult result = new TaskSubmissionResult(record.getTaskId(), record.getWorkflowId(),
                toStatusValue(record.getStatus()));
        if (forceRunning) {
            result.setStatus(TaskStatus.RUNNING.value());
        }
        result.setStreamUrl(resolveStreamUrl(record.getWorkflowId()));
        return result;
    }

    /**
     * 构建任务完成结果。
     */
    public TaskSubmissionResult buildCompletedResult(TaskRecord record) {
        TaskSubmissionResult result = new TaskSubmissionResult(record.getTaskId(), record.getWorkflowId(),
                toStatusValue(record.getStatus()));
        result.setStreamUrl(resolveStreamUrl(record.getWorkflowId()));
        result.setResult(record.getResult());
        return result;
    }

    /**
     * 获取工作流序列计数器。
     */
    public AtomicLong sequenceCounter(String tenantId, String workflowId) {
        return eventStreamService.sequenceCounter(tenantId, workflowId);
    }

    private String resolveStreamUrl(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return null;
        }
        return "/api/v1/stream/sse?workflow_id=" + workflowId;
    }

    private void publishEvent(StreamEvent event) {
        eventPublisher.publishEvent(event);
    }

    private StreamEvent buildEvent(TenantContext tenantContext,
                                   String workflowId,
                                   EventType type,
                                   long seq,
                                   Map<String, Object> payload) {
        String streamId = workflowId;
        Map<String, Object> mutable = payload == null ? new HashMap<>() : new HashMap<>(payload);
        attachTraceContext(mutable, tenantContext);
        StreamEvent event = new StreamEvent();
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(mutable);
        return event;
    }

    private void attachTraceContext(Map<String, Object> payload, TenantContext tenantContext) {
        if (payload == null || tenantContext == null) {
            return;
        }
        payload.putIfAbsent("traceId", resolveTraceId(tenantContext));
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
    }

    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTraceId())) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }

    private String toStatusValue(TaskStatus status) {
        if (status == null) {
            return null;
        }
        return status.value();
    }
}

package com.example.agent.orchestrator;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskListResponse;
import com.example.agent.common.TaskQuery;
import com.example.agent.common.TaskRequest;
import com.example.agent.common.TaskResponse;
import com.example.agent.common.TaskStatusResponse;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.runtime.RuntimeResult;
import com.example.agent.streaming.EventStreamService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 任务编排器，负责任务提交、事件发布与查询。
 */
@Service
public class TaskOrchestrator implements TaskSubmissionService, TaskQueryService {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestrator.class);

    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowRouter workflowRouter;
    private final MetricsPublisher metricsPublisher;
    private final TracingPublisher tracingPublisher;
    private final EventStreamService eventStreamService;
    private final TaskRepository taskRepository;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, Object> idempotencyLocks = new ConcurrentHashMap<>();

    @Value("${agent.idempotency.redis-enabled:false}")
    private boolean redisIdempotencyEnabled;

    @Value("${agent.idempotency.ttl-seconds:86400}")
    private long idempotencyTtlSeconds;

    public TaskOrchestrator(ApplicationEventPublisher eventPublisher,
                            WorkflowRouter workflowRouter,
                            MetricsPublisher metricsPublisher,
                            TracingPublisher tracingPublisher,
                            EventStreamService eventStreamService,
                            TaskRepository taskRepository,
                            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.eventPublisher = eventPublisher;
        this.workflowRouter = workflowRouter;
        this.metricsPublisher = metricsPublisher;
        this.tracingPublisher = tracingPublisher;
        this.eventStreamService = eventStreamService;
        this.taskRepository = taskRepository;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 提交任务并发布工作流启动事件。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 任务响应
     */
    @Override
    public TaskResponse submitTask(TaskRequest request, TenantContext tenantContext) {
        String tenantId = tenantContext.getTenantId();
        String idempotencyKey = request.getIdempotencyKey();
        if (StringUtils.hasText(idempotencyKey)) {
            String lockKey = buildIdempotencyKey(tenantId, idempotencyKey);
            Object lock = idempotencyLocks.computeIfAbsent(lockKey, key -> new Object());
            synchronized (lock) {
                try {
                    TaskRecord idempotent = findIdempotent(tenantId, idempotencyKey);
                    if (idempotent != null) {
                        log.info("幂等命中, tenantId={}, taskId={}", tenantId, idempotent.getTaskId());
                        return new TaskResponse(idempotent.getTaskId(), idempotent.getWorkflowId(),
                                idempotent.getStatus());
                    }

                    TaskRecord record = createTask(request, tenantContext);
                    TaskResponse response = new TaskResponse(record.getTaskId(), record.getWorkflowId(),
                            record.getStatus());
                    storeIdempotency(tenantId, idempotencyKey, record.getTaskId());
                    handleWorkflowRoute(request, tenantContext, response);
                    return response;
                } finally {
                    idempotencyLocks.remove(lockKey, lock);
                }
            }
        }

        TaskRecord record = createTask(request, tenantContext);
        TaskResponse response = new TaskResponse(record.getTaskId(), record.getWorkflowId(), record.getStatus());
        handleWorkflowRoute(request, tenantContext, response);
        return response;
    }

    /**
     * 查询任务状态。
     *
     * @param taskId 任务标识
     * @param tenantContext 租户上下文
     * @return 任务状态
     */
    @Override
    public TaskStatusResponse getTask(String taskId, TenantContext tenantContext) {
        TaskRecord record = taskRepository.findById(tenantContext.getTenantId(), taskId);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return toStatusResponse(record);
    }

    /**
     * 查询任务列表。
     *
     * @param query 查询条件
     * @param tenantContext 租户上下文
     * @return 任务列表
     */
    @Override
    public TaskListResponse listTasks(TaskQuery query, TenantContext tenantContext) {
        String tenantId = tenantContext.getTenantId();
        String statusFilter = query != null ? query.getStatus() : null;
        String cursor = query != null ? query.getCursor() : null;
        Integer size = query != null ? query.getSize() : null;

        ArrayList<TaskStatusResponse> filtered = new ArrayList<>();
        for (TaskRecord record : taskRepository.listByTenant(tenantId, statusFilter)) {
            filtered.add(toStatusResponse(record));
        }

        filtered.sort(Comparator.comparing(TaskStatusResponse::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        int startIndex = 0;
        if (StringUtils.hasText(cursor)) {
            for (int i = 0; i < filtered.size(); i++) {
                TaskStatusResponse item = filtered.get(i);
                if (item != null && cursor.equals(item.getTaskId())) {
                    startIndex = i + 1;
                    break;
                }
            }
        }

        int pageSize = (size != null && size > 0) ? size : filtered.size();
        int endIndex = Math.min(startIndex + pageSize, filtered.size());
        ArrayList<TaskStatusResponse> page = new ArrayList<>();
        if (startIndex < endIndex) {
            page.addAll(filtered.subList(startIndex, endIndex));
        }

        String nextCursor = null;
        boolean hasMore = false;
        if (endIndex < filtered.size() && endIndex > 0) {
            nextCursor = filtered.get(endIndex - 1).getTaskId();
            hasMore = true;
        }

        return new TaskListResponse(page, nextCursor, hasMore, filtered.size());
    }

    private TaskRecord createTask(TaskRequest request, TenantContext tenantContext) {
        String tenantId = tenantContext.getTenantId();
        String taskId = UUID.randomUUID().toString();
        String workflowId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord();
        record.setTaskId(taskId);
        record.setWorkflowId(workflowId);
        record.setStatus("SUBMITTED");
        record.setTenantId(tenantId);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(record.getCreatedAt());
        record.setIdempotencyKey(request.getIdempotencyKey());
        record.setRequest(buildRequestPayload(request));
        taskRepository.save(record);

        AtomicLong seqCounter = eventStreamService.sequenceCounter(tenantId, workflowId);
        long startedSeq = seqCounter.incrementAndGet();
        publishEvent(buildEvent(tenantContext, workflowId, EventType.WORKFLOW_STARTED, startedSeq,
                Map.of("message", "workflow started")));
        metricsPublisher.increment("task.submit.count", resolveTraceId(tenantContext));

        log.info("任务提交, tenantId={}, taskId={}, workflowId={}, traceId={}",
                tenantId, taskId, workflowId, resolveTraceId(tenantContext));
        return record;
    }

    private void handleWorkflowRoute(TaskRequest request, TenantContext tenantContext, TaskResponse response) {
        String workflowId = response.getWorkflowId();
        AtomicLong seqCounter = eventStreamService.sequenceCounter(tenantContext.getTenantId(), workflowId);
        long startNs = System.nanoTime();
        TaskRecord record = taskRepository.findById(tenantContext.getTenantId(), response.getTaskId());
        updateTaskStatus(record, "RUNNING", null);
        try {
            RuntimeResult runtimeResult = workflowRouter.route(request, tenantContext, workflowId,
                    response.getTaskId(), seqCounter);
            updateTaskStatus(record, "COMPLETED", buildResultPayload(runtimeResult));
        } catch (RuntimeException ex) {
            log.error("任务路由失败, tenantId={}, taskId={}, workflowId={}, traceId={}",
                    tenantContext.getTenantId(), response.getTaskId(), workflowId,
                    resolveTraceId(tenantContext), ex);
            long errorSeq = seqCounter.incrementAndGet();
            publishEvent(buildEvent(tenantContext, workflowId, EventType.ERROR_OCCURRED, errorSeq,
                    Map.of("error", ex.getMessage() == null ? "route_failed" : ex.getMessage())));
            String errorMessage = ex.getMessage() == null ? "route_failed" : ex.getMessage();
            updateTaskStatus(record, "FAILED", Map.of("error", errorMessage));
            throw ex;
        } finally {
            long endSeq = seqCounter.incrementAndGet();
            TaskRecord latest = taskRepository.findById(tenantContext.getTenantId(), response.getTaskId());
            String finalStatus = latest != null && latest.getStatus() != null
                    ? latest.getStatus()
                    : response.getStatus();
            publishEvent(buildEvent(tenantContext, workflowId, EventType.WORKFLOW_COMPLETED, endSeq,
                    Map.of("status", finalStatus)));
            long costMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
            metricsPublisher.recordTime("task.duration.ms", costMs, resolveTraceId(tenantContext));
        }
    }

    private TaskRecord findIdempotent(String tenantId, String idempotencyKey) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisIdempotencyEnabled && redisTemplate != null) {
            String redisKey = buildIdempotencyKey(tenantId, idempotencyKey);
            String taskId = redisTemplate.opsForValue().get(redisKey);
            if (StringUtils.hasText(taskId)) {
                TaskRecord record = taskRepository.findById(tenantId, taskId);
                if (record != null) {
                    return record;
                }
            }
        }
        return taskRepository.findByIdempotencyKey(tenantId, idempotencyKey);
    }

    private void storeIdempotency(String tenantId, String idempotencyKey, String taskId) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisIdempotencyEnabled && redisTemplate != null) {
            String redisKey = buildIdempotencyKey(tenantId, idempotencyKey);
            redisTemplate.opsForValue().set(redisKey, taskId, Duration.ofSeconds(idempotencyTtlSeconds));
        }
    }

    private String buildIdempotencyKey(String tenantId, String idempotencyKey) {
        return "idempotency:task:" + tenantId + ":" + idempotencyKey;
    }

    private TaskStatusResponse toStatusResponse(TaskRecord record) {
        return new TaskStatusResponse(record.getTaskId(), record.getWorkflowId(), record.getStatus(),
                record.getUpdatedAt(), record.getResult());
    }

    private void updateTaskStatus(TaskRecord record, String status, Map<String, Object> result) {
        if (record == null) {
            return;
        }
        record.setStatus(status);
        record.setUpdatedAt(Instant.now());
        if (result != null) {
            record.setResult(result);
        }
        taskRepository.save(record);
    }

    private Map<String, Object> buildResultPayload(RuntimeResult runtimeResult) {
        if (runtimeResult == null) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", runtimeResult.getPlanId());
        payload.put("planSummary", runtimeResult.getPlanSummary());
        payload.put("steps", runtimeResult.getSteps());
        payload.put("finalOutput", runtimeResult.getFinalOutput());
        return payload;
    }

    private Map<String, Object> buildRequestPayload(TaskRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", request.getQuery());
        payload.put("sessionId", request.getSessionId());
        payload.put("skillName", request.getSkillName());
        payload.put("context", request.getContext());
        payload.put("idempotencyKey", request.getIdempotencyKey());
        payload.put("toolChoice", request.getToolChoice());
        return payload;
    }

    private void publishEvent(StreamEvent event) {
        eventPublisher.publishEvent(event);
    }

    private StreamEvent buildEvent(TenantContext tenantContext, String workflowId, EventType type, long seq,
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
}

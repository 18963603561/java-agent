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
import com.example.agent.streaming.EventStreamService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private final EventStreamService eventStreamService;

    private final Map<String, TaskResponse> idempotencyCache = new ConcurrentHashMap<>();
    private final Map<String, TaskStatusResponse> taskStatusCache = new ConcurrentHashMap<>();
    private final Map<String, String> taskTenantIndex = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupAt = new AtomicLong(0);

    @Value("${orchestrator.task-ttl:24h}")
    private Duration taskTtl = Duration.ofHours(24);

    @Value("${orchestrator.cleanup-interval:5m}")
    private Duration cleanupInterval = Duration.ofMinutes(5);

    public TaskOrchestrator(ApplicationEventPublisher eventPublisher,
                            WorkflowRouter workflowRouter,
                            MetricsPublisher metricsPublisher,
                            EventStreamService eventStreamService) {
        this.eventPublisher = eventPublisher;
        this.workflowRouter = workflowRouter;
        this.metricsPublisher = metricsPublisher;
        this.eventStreamService = eventStreamService;
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
        cleanupIfNeeded();
        String tenantId = tenantContext.getTenantId();
        String idempotencyKey = request.getIdempotencyKey();
        if (StringUtils.hasText(idempotencyKey)) {
            String cacheKey = tenantId + ":" + idempotencyKey;
            AtomicBoolean created = new AtomicBoolean(false);
            TaskResponse response = idempotencyCache.computeIfAbsent(cacheKey, key -> {
                created.set(true);
                return createTask(tenantContext);
            });
            if (!created.get()) {
                log.info("幂等命中, tenantId={}, taskId={}", tenantId, response.getTaskId());
                return response;
            }
            handleWorkflowRoute(request, tenantContext, response);
            return response;
        }

        TaskResponse response = createTask(tenantContext);
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
        TaskStatusResponse status = taskStatusCache.get(taskId);
        String ownerTenant = taskTenantIndex.get(taskId);
        if (status == null || ownerTenant == null || !ownerTenant.equals(tenantContext.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return status;
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
        cleanupIfNeeded();
        String tenantId = tenantContext.getTenantId();
        String statusFilter = query != null ? query.getStatus() : null;
        String cursor = query != null ? query.getCursor() : null;
        Integer size = query != null ? query.getSize() : null;

        ArrayList<TaskStatusResponse> filtered = new ArrayList<>();
        for (Map.Entry<String, TaskStatusResponse> entry : taskStatusCache.entrySet()) {
            String taskId = entry.getKey();
            String ownerTenant = taskTenantIndex.get(taskId);
            if (!tenantId.equals(ownerTenant)) {
                continue;
            }
            TaskStatusResponse status = entry.getValue();
            if (StringUtils.hasText(statusFilter) && status != null
                    && !statusFilter.equalsIgnoreCase(status.getStatus())) {
                continue;
            }
            filtered.add(status);
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

    private TaskResponse createTask(TenantContext tenantContext) {
        String tenantId = tenantContext.getTenantId();
        String taskId = UUID.randomUUID().toString();
        String workflowId = UUID.randomUUID().toString();
        TaskResponse response = new TaskResponse(taskId, workflowId, "SUBMITTED");

        TaskStatusResponse status = new TaskStatusResponse(taskId, workflowId, "SUBMITTED", Instant.now(), null);
        taskStatusCache.put(taskId, status);
        taskTenantIndex.put(taskId, tenantId);

        AtomicLong seqCounter = eventStreamService.sequenceCounter(tenantId, workflowId);
        long startedSeq = seqCounter.incrementAndGet();
        publishEvent(buildEvent(tenantContext, workflowId, EventType.WORKFLOW_STARTED, startedSeq,
                Map.of("message", "workflow started")));
        metricsPublisher.increment("task.submit.count");

        log.info("任务提交, tenantId={}, taskId={}, workflowId={}", tenantId, taskId, workflowId);
        return response;
    }

    private void handleWorkflowRoute(TaskRequest request, TenantContext tenantContext, TaskResponse response) {
        String workflowId = response.getWorkflowId();
        AtomicLong seqCounter = eventStreamService.sequenceCounter(tenantContext.getTenantId(), workflowId);
        long startNs = System.nanoTime();
        TaskStatusResponse status = taskStatusCache.get(response.getTaskId());
        if (status != null) {
            status.setStatus("RUNNING");
            status.setUpdatedAt(Instant.now());
        }
        try {
            workflowRouter.route(request, tenantContext, workflowId, response.getTaskId(), seqCounter);
            if (status != null) {
                status.setStatus("COMPLETED");
                status.setUpdatedAt(Instant.now());
            }
        } catch (RuntimeException ex) {
            log.error("任务路由失败, tenantId={}, taskId={}, workflowId={}",
                    tenantContext.getTenantId(), response.getTaskId(), workflowId, ex);
            long errorSeq = seqCounter.incrementAndGet();
            publishEvent(buildEvent(tenantContext, workflowId, EventType.ERROR_OCCURRED, errorSeq,
                    Map.of("error", ex.getMessage() == null ? "route_failed" : ex.getMessage())));
            if (status != null) {
                status.setStatus("FAILED");
                status.setUpdatedAt(Instant.now());
                String errorMessage = ex.getMessage() == null ? "route_failed" : ex.getMessage();
                status.setResult(Map.of("error", errorMessage));
            }
            throw ex;
        } finally {
            long endSeq = seqCounter.incrementAndGet();
            String finalStatus = status != null && status.getStatus() != null
                    ? status.getStatus()
                    : "UNKNOWN";
            publishEvent(buildEvent(tenantContext, workflowId, EventType.WORKFLOW_COMPLETED, endSeq,
                    Map.of("status", finalStatus)));
            long costMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
            metricsPublisher.recordTime("task.duration.ms", costMs);
        }
    }

    private void cleanupIfNeeded() {
        long now = Instant.now().toEpochMilli();
        long last = lastCleanupAt.get();
        if (now - last < cleanupInterval.toMillis()) {
            return;
        }
        if (!lastCleanupAt.compareAndSet(last, now)) {
            return;
        }
        int evicted = 0;
        for (Map.Entry<String, TaskStatusResponse> entry : taskStatusCache.entrySet()) {
            String taskId = entry.getKey();
            TaskStatusResponse status = entry.getValue();
            if (isExpired(status, now)) {
                evictTask(taskId, status);
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("任务缓存清理, evicted={}, tasks={}, idempotency={}",
                    evicted, taskStatusCache.size(), idempotencyCache.size());
        }
    }

    private boolean isExpired(TaskStatusResponse status, long nowEpochMs) {
        if (status == null || status.getUpdatedAt() == null) {
            return true;
        }
        long updatedAt = status.getUpdatedAt().toEpochMilli();
        return nowEpochMs - updatedAt > taskTtl.toMillis();
    }

    private void evictTask(String taskId, TaskStatusResponse status) {
        String tenantId = taskTenantIndex.remove(taskId);
        taskStatusCache.remove(taskId);
        if (tenantId != null && status != null && status.getWorkflowId() != null) {
            eventStreamService.evictSequence(tenantId, status.getWorkflowId());
        }
        idempotencyCache.entrySet().removeIf(entry -> entry.getValue() != null
                && taskId.equals(entry.getValue().getTaskId()));
    }

    private void publishEvent(StreamEvent event) {
        eventPublisher.publishEvent(event);
    }

    private StreamEvent buildEvent(TenantContext tenantContext, String workflowId, EventType type, long seq,
                                   Map<String, Object> payload) {
        String streamId = workflowId;
        StreamEvent event = new StreamEvent();
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload);
        return event;
    }
}

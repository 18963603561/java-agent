package com.example.agent.orchestration.task;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.error.SyncWaitTimeoutException;
import com.example.agent.api.http.dto.TaskListResponse;
import com.example.agent.api.http.dto.TaskQuery;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.api.http.dto.TaskResponse;
import com.example.agent.api.http.dto.TaskStatusResponse;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.runtime.engine.RuntimeResult;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import jakarta.annotation.PostConstruct;
import com.example.agent.orchestration.workflow.WorkflowRouter;

/**
 * 任务编排器，负责任务提交、事件发布与查询。
 * <p>用途：串联任务持久化、事件流与运行时路由，保障任务全链路可追踪。
 * <p>输入：任务请求与租户上下文。
 * <p>输出：任务响应或任务状态列表。
 * <p>边界：当任务不存在时返回 {@code 404}；幂等键冲突时返回已存在任务。
 * <p>示例：
 * <pre>{@code
 * TaskResponse response = taskOrchestrator.submitTask(request, tenantContext);
 * }</pre>
 */
@Service
public class TaskOrchestrator implements TaskSubmissionService, TaskQueryService {

    /**
     * 日志记录器，用于记录任务编排关键节点。
     * <p>示例：记录任务标识与工作流标识。
     */
    private static final Logger log = LoggerFactory.getLogger(TaskOrchestrator.class);

    /**
     * 应用事件发布器，用于发布工作流事件。
     * <p>示例：发布 {@code WORKFLOW_STARTED} 事件。
     */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 工作流路由器，用于进入运行时。
     * <p>示例：路由到 {@code AgentRuntime.run}。
     */
    private final WorkflowRouter workflowRouter;
    /**
     * 指标发布器，用于输出耗时与数量指标。
     * <p>示例：记录任务耗时与提交次数。
     */
    private final MetricsPublisher metricsPublisher;
    /**
     * 链路跟踪发布器。
     * <p>示例：补充当前链路的跟踪标识。
     */
    private final TracingPublisher tracingPublisher;
    /**
     * 事件流服务，用于生成序列号。
     * <p>示例：为工作流事件生成递增序号。
     */
    private final EventStreamService eventStreamService;
    /**
     * 任务持久化仓库。
     * <p>示例：创建、查询、更新任务记录。
     */
    private final TaskRepository taskRepository;
    /**
     * 任务异步执行器，用于后台运行工作流。
     */
    private final TaskExecutionService taskExecutionService;
    /**
     * 缓存模板提供器，用于幂等键缓存。
     * <p>示例：读取或写入幂等键到 {@code Redis}。
     */
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    /**
     * 本地幂等锁容器，用于控制同一幂等键的并发提交。
     * <p>示例：同一 {@code idempotencyKey} 仅允许单次提交进入创建逻辑。
     */
    private final Map<String, Object> idempotencyLocks = new ConcurrentHashMap<>();

    /**
     * 是否启用 {@code Redis} 幂等存储。
     * <p>示例：配置 {@code agent.idempotency.redis-enabled=true}。
     */
    @Value("${agent.idempotency.redis-enabled:false}")
    private boolean redisIdempotencyEnabled;

    /**
     * 幂等键在 {@code Redis} 中的有效期秒数。
     * <p>示例：配置 {@code agent.idempotency.ttl-seconds=86400}。
     */
    @Value("${agent.idempotency.ttl-seconds:86400}")
    private long idempotencyTtlSeconds;

    /**
     * 同步模式默认等待时长（毫秒）。
     */
    @Value("${agent.task.sync.wait-timeout-ms:30000}")
    private long syncWaitTimeoutMs;

    /**
     * 同步模式等待最大上限（毫秒）。
     */
    @Value("${agent.task.sync.max-wait-timeout-ms:60000}")
    private long syncMaxWaitTimeoutMs;

    /**
     * 同步等待并发上限。
     */
    @Value("${agent.task.sync.max-concurrency:20}")
    private int syncMaxConcurrency;

    /**
     * 同步等待并发控制器。
     */
    private Semaphore syncSemaphore;

    /**
     * 构造任务编排器。
     *
     * @param eventPublisher 事件发布器
     * @param workflowRouter 工作流路由器
     * @param metricsPublisher 指标发布器
     * @param tracingPublisher 链路跟踪发布器
     * @param eventStreamService 事件流服务
     * @param taskRepository 任务仓库
     * @param redisTemplateProvider 缓存模板提供器
     */
    public TaskOrchestrator(ApplicationEventPublisher eventPublisher,
                            WorkflowRouter workflowRouter,
                            MetricsPublisher metricsPublisher,
                            TracingPublisher tracingPublisher,
                            EventStreamService eventStreamService,
                            TaskRepository taskRepository,
                            TaskExecutionService taskExecutionService,
                            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.eventPublisher = eventPublisher;
        this.workflowRouter = workflowRouter;
        this.metricsPublisher = metricsPublisher;
        this.tracingPublisher = tracingPublisher;
        this.eventStreamService = eventStreamService;
        this.taskRepository = taskRepository;
        this.taskExecutionService = taskExecutionService;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @PostConstruct
    public void initSyncSemaphore() {
        int permits = Math.max(1, syncMaxConcurrency);
        this.syncSemaphore = new Semaphore(permits);
        log.info("同步等待并发初始化, permits={}", permits);
    }

    /**
     * 提交任务并发布工作流启动事件。
     *
     * <p>输入：任务请求与租户上下文。
     * <p>输出：包含任务标识、工作流标识与初始状态的响应。
     * <p>边界：存在幂等键时返回已存在任务；无幂等键则每次生成新任务。
     * <p>示例：
     * <pre>{@code
     * TaskResponse response = submitTask(request, tenantContext);
     * }</pre>
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 任务响应
     */
    @Override
    public TaskResponse submitTask(TaskRequest request, TenantContext tenantContext) {
        String tenantId = tenantContext.getTenantId();
        String idempotencyKey = normalizeIdempotencyKey(request);
        TaskRequest.ExecutionMode executionMode = resolveExecutionMode(request);
        if (StringUtils.hasText(idempotencyKey)) {
            String lockKey = buildIdempotencyKey(tenantId, idempotencyKey);
            Object lock = idempotencyLocks.computeIfAbsent(lockKey, key -> new Object());
            synchronized (lock) {
                try {
                    // 先尝试命中幂等记录，避免重复创建任务。
                    TaskRecord idempotent = findIdempotent(tenantId, idempotencyKey);
                    if (idempotent != null) {
                        log.info("幂等命中, tenantId={}, taskId={}", tenantId, idempotent.getTaskId());
                        return handleExistingTask(request, tenantContext, idempotent, executionMode);
                    }

                    // 幂等未命中时创建任务并写入存储。
                    TaskRecord record = createTask(request, tenantContext);
                    // 提交后台执行，失败则返回 503。
                    submitAsyncOrThrow(request, tenantContext, record, executionMode);
                    // 记录幂等键，避免重复处理。
                    storeIdempotency(tenantId, idempotencyKey, record.getTaskId());
                    publishTaskAccepted(tenantContext, record);
                    return waitIfSync(request, tenantContext, record, executionMode);
                } finally {
                    // 清理本地锁对象，避免内存占用。
                    idempotencyLocks.remove(lockKey, lock);
                }
            }
        }

        // 无幂等键时直接创建任务并进入路由。
        TaskRecord record = createTask(request, tenantContext);
        submitAsyncOrThrow(request, tenantContext, record, executionMode);
        publishTaskAccepted(tenantContext, record);
        return waitIfSync(request, tenantContext, record, executionMode);
    }

    /**
     * 归一化幂等键：空白视为未提供，非空则去除首尾空格并回写请求对象。
     *
     * <p>输入：任务请求对象（可能为空）。</p>
     * <p>输出：归一化后的幂等键，空白时返回 {@code null}。</p>
     * <p>注意：该方法会在必要时更新请求对象中的字段，避免持久化空白键。</p>
     *
     * @param request 任务请求
     * @return 归一化后的幂等键
     */
    private String normalizeIdempotencyKey(TaskRequest request) {
        if (request == null) {
            return null;
        }
        String rawKey = request.getIdempotencyKey();
        if (!StringUtils.hasText(rawKey)) {
            request.setIdempotencyKey(null);
            return null;
        }
        String trimmed = rawKey.trim();
        if (!StringUtils.hasText(trimmed)) {
            request.setIdempotencyKey(null);
            return null;
        }
        if (!trimmed.equals(rawKey)) {
            request.setIdempotencyKey(trimmed);
        }
        return trimmed;
    }

    /**
     * 查询任务状态。
     *
     * <p>输入：任务标识与租户上下文。
     * <p>输出：任务状态响应对象。
     * <p>边界：任务不存在时抛出 {@code 404}。
     * <p>示例：
     * <pre>{@code
     * TaskStatusResponse status = getTask(taskId, tenantContext);
     * }</pre>
     *
     * @param taskId 任务标识
     * @param tenantContext 租户上下文
     * @return 任务状态
     */
    @Override
    public TaskStatusResponse getTask(String taskId, TenantContext tenantContext) {
        // 从持久化层读取任务记录。
        TaskRecord record = taskRepository.findById(tenantContext.getTenantId(), taskId);
        if (record == null) {
            // 任务不存在时直接返回错误，避免误判。
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return toStatusResponse(record);
    }

    /**
     * 查询任务列表。
     *
     * <p>输入：查询条件与租户上下文。
     * <p>输出：分页后的任务列表。
     * <p>边界：无分页参数时返回全量列表。
     * <p>示例：
     * <pre>{@code
     * TaskListResponse list = listTasks(query, tenantContext);
     * }</pre>
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

        // 先按租户过滤并转换为响应对象。
        ArrayList<TaskStatusResponse> filtered = new ArrayList<>();
        for (TaskRecord record : taskRepository.listByTenant(tenantId, statusFilter)) {
            filtered.add(toStatusResponse(record));
        }

        // 按更新时间倒序排序，优先展示最新任务。
        filtered.sort(Comparator.comparing(TaskStatusResponse::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        // 根据游标定位分页起点。
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

        // 计算分页范围。
        int pageSize = (size != null && size > 0) ? size : filtered.size();
        int endIndex = Math.min(startIndex + pageSize, filtered.size());
        ArrayList<TaskStatusResponse> page = new ArrayList<>();
        if (startIndex < endIndex) {
            page.addAll(filtered.subList(startIndex, endIndex));
        }

        // 生成下一页游标与是否还有更多记录标志。
        String nextCursor = null;
        boolean hasMore = false;
        if (endIndex < filtered.size() && endIndex > 0) {
            nextCursor = filtered.get(endIndex - 1).getTaskId();
            hasMore = true;
        }

        return new TaskListResponse(page, nextCursor, hasMore, filtered.size());
    }

    /**
     * 创建任务记录并发布启动事件。
     *
     * <p>输入：任务请求与租户上下文。
     * <p>输出：持久化后的任务记录。
     * <p>边界：请求为空时仍会创建基础记录。
     * <p>示例：
     * <pre>{@code
     * TaskRecord record = createTask(request, tenantContext);
     * }</pre>
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 任务记录
     */
    private TaskRecord createTask(TaskRequest request, TenantContext tenantContext) {
        String tenantId = tenantContext.getTenantId();
        String taskId = UUID.randomUUID().toString();
        String workflowId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord();
        // 填充任务基础字段。
        record.setTaskId(taskId);
        record.setWorkflowId(workflowId);
        record.setStatus("SUBMITTED");
        record.setTenantId(tenantId);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(record.getCreatedAt());
        record.setIdempotencyKey(request.getIdempotencyKey());
        record.setRequest(buildRequestPayload(request));
        // 持久化任务记录。
        taskRepository.save(record);

        log.info("任务提交, tenantId={}, taskId={}, workflowId={}, traceId={}",
                tenantId, taskId, workflowId, resolveTraceId(tenantContext));
        return record;
    }

    /**
     * 路由任务进入运行时执行并更新状态。
     *
     * <p>输入：任务请求、租户上下文与任务响应。
     * <p>输出：无，状态更新写入存储。
     * <p>边界：运行时异常会被捕获并标记任务失败。
     * <p>示例：
     * <pre>{@code
     * handleWorkflowRoute(request, tenantContext, response);
     * }</pre>
     */
    private void handleWorkflowRoute(TaskRequest request, TenantContext tenantContext, TaskRecord record) {
        String workflowId = record.getWorkflowId();
        AtomicLong seqCounter = eventStreamService.sequenceCounter(tenantContext.getTenantId(), workflowId);
        long startNs = System.nanoTime();
        TaskRecord latest = taskRepository.findById(tenantContext.getTenantId(), record.getTaskId());
        // 进入运行时前将任务状态置为运行中。
        updateTaskStatus(latest, "RUNNING", null);
        long startedSeq = seqCounter.incrementAndGet();
        publishEvent(buildEvent(tenantContext, workflowId, EventType.WORKFLOW_STARTED, startedSeq,
                Map.of("message", "workflow started")));
        try {
            // 路由进入运行时执行。
            RuntimeResult runtimeResult = workflowRouter.route(request, tenantContext, workflowId,
                    record.getTaskId(), seqCounter);
            // 执行成功后写入最终结果。
            updateTaskStatus(latest, "COMPLETED", buildResultPayload(runtimeResult));
        } catch (RuntimeException ex) {
            // 捕获异常并发布错误事件，标记任务失败。
            log.error("任务路由失败, tenantId={}, taskId={}, workflowId={}, traceId={}",
                    tenantContext.getTenantId(), record.getTaskId(), workflowId,
                    resolveTraceId(tenantContext), ex);
            long errorSeq = seqCounter.incrementAndGet();
            publishEvent(buildEvent(tenantContext, workflowId, EventType.ERROR_OCCURRED, errorSeq,
                    Map.of("error", ex.getMessage() == null ? "route_failed" : ex.getMessage())));
            String errorMessage = ex.getMessage() == null ? "route_failed" : ex.getMessage();
            updateTaskStatus(latest, "FAILED", Map.of("error", errorMessage));
            throw ex;
        } finally {
            // 无论成功或失败都发布完成事件，并记录耗时指标。
            long endSeq = seqCounter.incrementAndGet();
            TaskRecord statusRecord = taskRepository.findById(tenantContext.getTenantId(), record.getTaskId());
            String finalStatus = statusRecord != null && statusRecord.getStatus() != null
                    ? statusRecord.getStatus()
                    : record.getStatus();
            publishEvent(buildEvent(tenantContext, workflowId, EventType.WORKFLOW_COMPLETED, endSeq,
                    Map.of("status", finalStatus)));
            long costMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
            metricsPublisher.recordTime("task.duration.ms", costMs, resolveTraceId(tenantContext));
        }
    }

    /**
     * 查询幂等任务记录。
     *
     * <p>输入：租户标识与幂等键。
     * <p>输出：已存在的任务记录或 {@code null}。
     * <p>边界：{@code Redis} 不可用时回退到持久化查询。
     * <p>示例：
     * <pre>{@code
     * TaskRecord record = findIdempotent(tenantId, idempotencyKey);
     * }</pre>
     */
    private TaskRecord findIdempotent(String tenantId, String idempotencyKey) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisIdempotencyEnabled && redisTemplate != null) {
            // 优先从 {@code Redis} 获取幂等映射。
            String redisKey = buildIdempotencyKey(tenantId, idempotencyKey);
            String taskId = redisTemplate.opsForValue().get(redisKey);
            if (StringUtils.hasText(taskId)) {
                TaskRecord record = taskRepository.findById(tenantId, taskId);
                if (record != null) {
                    return record;
                }
            }
        }
        // {@code Redis} 未命中时回退持久化层查询。
        return taskRepository.findByIdempotencyKey(tenantId, idempotencyKey);
    }

    /**
     * 保存幂等键映射。
     *
     * <p>输入：租户标识、幂等键与任务标识。
     * <p>输出：无。
     * <p>边界：未启用 {@code Redis} 时不写入。
     * <p>示例：
     * <pre>{@code
     * storeIdempotency(tenantId, idempotencyKey, taskId);
     * }</pre>
     */
    private void storeIdempotency(String tenantId, String idempotencyKey, String taskId) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisIdempotencyEnabled && redisTemplate != null) {
            // 写入 {@code Redis} 并设置过期时间。
            String redisKey = buildIdempotencyKey(tenantId, idempotencyKey);
            redisTemplate.opsForValue().set(redisKey, taskId, Duration.ofSeconds(idempotencyTtlSeconds));
        }
    }

    /**
     * 生成幂等键的缓存键。
     *
     * <p>输入：租户标识与幂等键。
     * <p>输出：缓存键字符串。
     * <p>示例：
     * <pre>{@code
     * String key = buildIdempotencyKey("tenantA", "req-001");
     * }</pre>
     */
    private String buildIdempotencyKey(String tenantId, String idempotencyKey) {
        return "idempotency:task:" + tenantId + ":" + idempotencyKey;
    }

    /**
     * 将任务记录转换为状态响应。
     *
     * <p>输入：任务记录。
     * <p>输出：状态响应对象。
     * <p>示例：
     * <pre>{@code
     * TaskStatusResponse response = toStatusResponse(record);
     * }</pre>
     */
    private TaskStatusResponse toStatusResponse(TaskRecord record) {
        return new TaskStatusResponse(record.getTaskId(), record.getWorkflowId(), record.getStatus(),
                record.getUpdatedAt(), record.getResult());
    }

    /**
     * 更新任务状态并持久化。
     *
     * <p>输入：任务记录、状态与结果。
     * <p>输出：无。
     * <p>边界：记录为空时直接返回。
     * <p>示例：
     * <pre>{@code
     * updateTaskStatus(record, "COMPLETED", result);
     * }</pre>
     */
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

    /**
     * 组装运行时结果载荷。
     *
     * <p>输入：运行时结果。
     * <p>输出：可持久化的结果映射。
     * <p>边界：运行时结果为空时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> payload = buildResultPayload(runtimeResult);
     * }</pre>
     */
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

    /**
     * 构建任务请求的持久化副本。
     *
     * <p>输入：任务请求。
     * <p>输出：可序列化的请求映射。
     * <p>边界：请求为空时返回空映射。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> payload = buildRequestPayload(request);
     * }</pre>
     */
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
        payload.put("executionMode", request.getExecutionMode());
        payload.put("waitTimeoutMs", request.getWaitTimeoutMs());
        return payload;
    }

    private TaskRequest.ExecutionMode resolveExecutionMode(TaskRequest request) {
        if (request == null) {
            return TaskRequest.ExecutionMode.ASYNC;
        }
        TaskRequest.ExecutionMode mode = request.getExecutionMode();
        if (mode == null) {
            request.setExecutionMode(TaskRequest.ExecutionMode.ASYNC);
            return TaskRequest.ExecutionMode.ASYNC;
        }
        return mode;
    }

    private TaskResponse handleExistingTask(TaskRequest request,
                                            TenantContext tenantContext,
                                            TaskRecord record,
                                            TaskRequest.ExecutionMode executionMode) {
        if (record == null) {
            return new TaskResponse();
        }
        if (executionMode != TaskRequest.ExecutionMode.SYNC) {
            return buildAcceptedResponse(record, false);
        }
        if (isTerminalStatus(record.getStatus())) {
            return buildCompletedResponse(record);
        }
        java.util.concurrent.CompletableFuture<Void> future = taskExecutionService.getFuture(record.getTaskId());
        if (future == null) {
            log.info("同步等待未命中执行器, taskId={}, status={}", record.getTaskId(), record.getStatus());
            return buildAcceptedResponse(record, true);
        }
        return waitIfSync(request, tenantContext, record, executionMode, future);
    }

    private TaskResponse waitIfSync(TaskRequest request,
                                    TenantContext tenantContext,
                                    TaskRecord record,
                                    TaskRequest.ExecutionMode executionMode) {
        java.util.concurrent.CompletableFuture<Void> future = taskExecutionService.getFuture(record.getTaskId());
        if (future == null || executionMode != TaskRequest.ExecutionMode.SYNC) {
            return buildAcceptedResponse(record, false);
        }
        return waitIfSync(request, tenantContext, record, executionMode, future);
    }

    private TaskResponse waitIfSync(TaskRequest request,
                                    TenantContext tenantContext,
                                    TaskRecord record,
                                    TaskRequest.ExecutionMode executionMode,
                                    java.util.concurrent.CompletableFuture<Void> future) {
        if (executionMode != TaskRequest.ExecutionMode.SYNC) {
            return buildAcceptedResponse(record, false);
        }
        if (syncSemaphore == null || !syncSemaphore.tryAcquire()) {
            log.warn("同步并发已达上限, taskId={}, workflowId={}", record.getTaskId(), record.getWorkflowId());
            return buildAcceptedResponse(record, true);
        }
        try {
            long waitTimeout = resolveWaitTimeoutMs(request);
            try {
                future.get(waitTimeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                TaskResponse timeoutResponse = buildAcceptedResponse(record, true);
                throw new SyncWaitTimeoutException(timeoutResponse, "sync_wait_timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                TaskResponse timeoutResponse = buildAcceptedResponse(record, true);
                throw new SyncWaitTimeoutException(timeoutResponse, "sync_wait_interrupted");
            } catch (ExecutionException ex) {
                log.warn("同步等待任务异常完成, taskId={}, workflowId={}", record.getTaskId(),
                        record.getWorkflowId(), ex.getCause());
            }
            TaskRecord latest = taskRepository.findById(tenantContext.getTenantId(), record.getTaskId());
            return buildCompletedResponse(latest != null ? latest : record);
        } finally {
            syncSemaphore.release();
        }
    }

    private long resolveWaitTimeoutMs(TaskRequest request) {
        long defaultTimeout = Math.max(1000, syncWaitTimeoutMs);
        long maxTimeout = Math.max(defaultTimeout, syncMaxWaitTimeoutMs);
        if (request == null || request.getWaitTimeoutMs() == null) {
            return defaultTimeout;
        }
        long configured = request.getWaitTimeoutMs();
        if (configured <= 0) {
            return defaultTimeout;
        }
        return Math.min(configured, maxTimeout);
    }

    private boolean isTerminalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String upper = status.toUpperCase();
        return "COMPLETED".equals(upper) || "FAILED".equals(upper);
    }

    private TaskResponse buildAcceptedResponse(TaskRecord record, boolean forceRunning) {
        TaskResponse response = new TaskResponse(record.getTaskId(), record.getWorkflowId(), record.getStatus());
        if (forceRunning) {
            response.setStatus("RUNNING");
        }
        response.setStreamUrl(resolveStreamUrl(record.getWorkflowId()));
        return response;
    }

    private TaskResponse buildCompletedResponse(TaskRecord record) {
        TaskResponse response = new TaskResponse(record.getTaskId(), record.getWorkflowId(), record.getStatus());
        response.setStreamUrl(resolveStreamUrl(record.getWorkflowId()));
        response.setResult(record.getResult());
        return response;
    }

    private String resolveStreamUrl(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return null;
        }
        return "/api/v1/stream/sse?workflow_id=" + workflowId;
    }

    private java.util.concurrent.CompletableFuture<Void> submitAsyncOrThrow(TaskRequest request,
                                                                           TenantContext tenantContext,
                                                                           TaskRecord record,
                                                                           TaskRequest.ExecutionMode executionMode) {
        try {
            return taskExecutionService.submit(record.getTaskId(),
                    () -> handleWorkflowRoute(request, tenantContext, record));
        } catch (RejectedExecutionException ex) {
            updateTaskStatus(record, "FAILED", Map.of("error", "executor_rejected"));
            log.warn("任务提交被拒绝, tenantId={}, taskId={}, workflowId={}, mode={}",
                    tenantContext.getTenantId(), record.getTaskId(), record.getWorkflowId(), executionMode);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "EXECUTOR_REJECTED",
                    "executor_rejected");
        }
    }

    private void publishTaskAccepted(TenantContext tenantContext, TaskRecord record) {
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
     * 发布事件。
     *
     * <p>输入：事件对象。
     * <p>输出：无。
     * <p>示例：
     * <pre>{@code
     * publishEvent(streamEvent);
     * }</pre>
     */
    private void publishEvent(StreamEvent event) {
        eventPublisher.publishEvent(event);
    }

    /**
     * 构建事件对象。
     *
     * <p>输入：租户上下文、工作流标识、事件类型与载荷。
     * <p>输出：事件对象。
     * <p>边界：载荷为空时会创建空映射。
     * <p>示例：
     * <pre>{@code
     * StreamEvent event = buildEvent(context, workflowId, type, seq, payload);
     * }</pre>
     */
    private StreamEvent buildEvent(TenantContext tenantContext, String workflowId, EventType type, long seq,
                                   Map<String, Object> payload) {
        String streamId = workflowId;
        Map<String, Object> mutable = payload == null ? new HashMap<>() : new HashMap<>(payload);
        // 补充链路跟踪信息，便于审计与排障。
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

    /**
     * 将链路追踪信息写入事件载荷。
     *
     * <p>输入：载荷映射与租户上下文。
     * <p>输出：无。
     * <p>边界：任一参数为空时不做处理。
     * <p>示例：
     * <pre>{@code
     * attachTraceContext(payload, tenantContext);
     * }</pre>
     */
    private void attachTraceContext(Map<String, Object> payload, TenantContext tenantContext) {
        if (payload == null || tenantContext == null) {
            return;
        }
        payload.putIfAbsent("traceId", resolveTraceId(tenantContext));
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
    }

    /**
     * 解析链路跟踪标识。
     *
     * <p>输入：租户上下文。
     * <p>输出：跟踪标识字符串。
     * <p>边界：上下文缺失时使用当前链路标识。
     * <p>示例：
     * <pre>{@code
     * String traceId = resolveTraceId(context);
     * }</pre>
     */
    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTraceId())) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }
}

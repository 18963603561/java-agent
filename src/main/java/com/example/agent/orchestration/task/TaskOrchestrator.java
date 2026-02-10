package com.example.agent.orchestration.task;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.orchestration.task.contract.TaskExecutionMode;
import com.example.agent.orchestration.task.contract.TaskListView;
import com.example.agent.orchestration.task.contract.TaskQueryCommand;
import com.example.agent.orchestration.task.contract.TaskStatusView;
import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.example.agent.orchestration.workflow.WorkflowRouter;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.security.auth.TenantContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 任务编排门面。
 * <p>用途：负责任务提交流程编排、查询编排与统一错误边界转换。
 * <p>输入：任务提交命令、任务查询命令、租户上下文。
 * <p>输出：提交结果、任务状态视图、任务列表视图。
 * <p>边界：仓储异常统一转换为 503，任务不存在返回 404。
 */
@Service
public class TaskOrchestrator implements TaskSubmissionService, TaskQueryService {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestrator.class);

    /**
     * 工作流路由器。
     */
    private final WorkflowRouter workflowRouter;

    /**
     * 任务执行服务。
     */
    private final TaskExecutionService taskExecutionService;

    /**
     * 任务仓储。
     */
    private final TaskRepository taskRepository;

    /**
     * 生命周期服务。
     */
    private final TaskLifecycleService taskLifecycleService;

    /**
     * 幂等服务。
     */
    private final TaskIdempotencyService taskIdempotencyService;

    /**
     * 同步等待服务。
     */
    private final TaskSyncWaitService taskSyncWaitService;

    /**
     * 任务事件发布器。
     */
    private final TaskEventPublisher taskEventPublisher;

    /**
     * 仓储异常翻译器。
     */
    private final TaskRepositoryErrorTranslator taskRepositoryErrorTranslator;

    public TaskOrchestrator(WorkflowRouter workflowRouter,
                            TaskExecutionService taskExecutionService,
                            TaskRepository taskRepository,
                            TaskLifecycleService taskLifecycleService,
                            TaskIdempotencyService taskIdempotencyService,
                            TaskSyncWaitService taskSyncWaitService,
                            TaskEventPublisher taskEventPublisher,
                            TaskRepositoryErrorTranslator taskRepositoryErrorTranslator) {
        this.workflowRouter = workflowRouter;
        this.taskExecutionService = taskExecutionService;
        this.taskRepository = taskRepository;
        this.taskLifecycleService = taskLifecycleService;
        this.taskIdempotencyService = taskIdempotencyService;
        this.taskSyncWaitService = taskSyncWaitService;
        this.taskEventPublisher = taskEventPublisher;
        this.taskRepositoryErrorTranslator = taskRepositoryErrorTranslator;
    }

    /**
     * 提交任务。
     * <p>流程：参数归一化 -> 幂等判定 -> 创建任务 -> 异步执行 -> 同步等待分支（可选）。
     */
    @Override
    public TaskSubmissionResult submitTask(TaskSubmitCommand command, TenantContext tenantContext) {
        try {
            String tenantId = tenantContext.getTenantId();
            TaskSubmitCommand effectiveCommand = command != null ? command : new TaskSubmitCommand();
            String idempotencyKey = taskIdempotencyService.normalizeIdempotencyKey(effectiveCommand.getIdempotencyKey());
            effectiveCommand.setIdempotencyKey(idempotencyKey);
            TaskExecutionMode executionMode = resolveExecutionMode(effectiveCommand);

            if (StringUtils.hasText(idempotencyKey)) {
                return taskIdempotencyService.executeWithLocalLock(tenantId, idempotencyKey,
                        () -> submitWithIdempotency(effectiveCommand, tenantContext, executionMode, idempotencyKey));
            }
            return createAndDispatchTask(effectiveCommand, tenantContext, executionMode, null);
        } catch (TaskRepositoryException ex) {
            throw taskRepositoryErrorTranslator.translate(ex, "submit_task", tenantContext, ex.getWorkflowId(),
                    ex.getTaskId(), true);
        }
    }

    /**
     * 查询任务状态。
     */
    @Override
    public TaskStatusView getTask(String taskId, TenantContext tenantContext) {
        try {
            TaskRecord record = taskRepository.findById(tenantContext.getTenantId(), taskId);
            if (record == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
            }
            return taskLifecycleService.toStatusView(record);
        } catch (TaskRepositoryException ex) {
            throw taskRepositoryErrorTranslator.translate(ex, "get_task", tenantContext, null, taskId, false);
        }
    }

    /**
     * 查询任务列表。
     */
    @Override
    public TaskListView listTasks(TaskQueryCommand command, TenantContext tenantContext) {
        try {
            String tenantId = tenantContext.getTenantId();
            TaskStatus statusFilter = command != null ? command.getStatus() : null;
            String cursor = command != null ? command.getCursor() : null;
            Integer size = command != null ? command.getSize() : null;
            TaskPageQuery query = new TaskPageQuery(tenantId,
                    statusFilter != null ? statusFilter.value() : null,
                    cursor,
                    size);
            TaskPageResult pageResult = taskRepository.listPage(query);
            List<TaskStatusView> views = pageResult.getTasks().stream()
                    .map(taskLifecycleService::toStatusView)
                    .toList();
            return new TaskListView(views,
                    pageResult.getNextCursor(),
                    pageResult.isHasMore(),
                    pageResult.getTotal());
        } catch (TaskRepositoryException ex) {
            throw taskRepositoryErrorTranslator.translate(ex, "list_tasks", tenantContext, null, null, false);
        }
    }

    /**
     * 在幂等上下文中提交任务。
     */
    private TaskSubmissionResult submitWithIdempotency(TaskSubmitCommand command,
                                                       TenantContext tenantContext,
                                                       TaskExecutionMode executionMode,
                                                       String idempotencyKey) {
        String tenantId = tenantContext.getTenantId();
        TaskRecord idempotent = taskIdempotencyService.findIdempotent(tenantId, idempotencyKey);
        if (idempotent != null) {
            log.info("幂等命中, tenantId={}, taskId={}", tenantId, idempotent.getTaskId());
            return handleExistingTask(command, tenantContext, idempotent, executionMode);
        }
        return createAndDispatchTask(command, tenantContext, executionMode, idempotencyKey);
    }

    /**
     * 创建任务并调度执行。
     */
    private TaskSubmissionResult createAndDispatchTask(TaskSubmitCommand command,
                                                       TenantContext tenantContext,
                                                       TaskExecutionMode executionMode,
                                                       String idempotencyKey) {
        TaskRecord record = taskLifecycleService.createTask(command, tenantContext);
        CompletableFuture<Void> future = submitAsyncOrThrow(command, tenantContext, record, executionMode);
        if (StringUtils.hasText(idempotencyKey)) {
            taskIdempotencyService.storeIdempotency(tenantContext.getTenantId(), idempotencyKey, record.getTaskId());
        }
        taskEventPublisher.publishTaskAccepted(tenantContext, record);

        TaskSubmissionResult accepted = taskEventPublisher.buildAcceptedResult(record, false);
        TaskSubmissionResult completed = taskEventPublisher.buildCompletedResult(record);
        return taskSyncWaitService.waitForResult(executionMode,
                command != null ? command.getWaitTimeoutMs() : null,
                tenantContext,
                record,
                future,
                accepted,
                completed);
    }

    /**
     * 处理已存在任务。
     */
    private TaskSubmissionResult handleExistingTask(TaskSubmitCommand command,
                                                    TenantContext tenantContext,
                                                    TaskRecord record,
                                                    TaskExecutionMode executionMode) {
        if (record == null) {
            return new TaskSubmissionResult();
        }
        if (executionMode != TaskExecutionMode.SYNC) {
            return taskEventPublisher.buildAcceptedResult(record, false);
        }
        if (taskLifecycleService.isTerminalStatus(record.getStatus())) {
            return taskEventPublisher.buildCompletedResult(record);
        }
        CompletableFuture<Void> future = taskExecutionService.getFuture(record.getTaskId());
        return taskSyncWaitService.waitForResult(executionMode,
                command != null ? command.getWaitTimeoutMs() : null,
                tenantContext,
                record,
                future,
                taskEventPublisher.buildAcceptedResult(record, false),
                taskEventPublisher.buildCompletedResult(record));
    }

    /**
     * 提交异步执行并处理线程池拒绝。
     */
    private CompletableFuture<Void> submitAsyncOrThrow(TaskSubmitCommand command,
                                                       TenantContext tenantContext,
                                                       TaskRecord record,
                                                       TaskExecutionMode executionMode) {
        try {
            return taskExecutionService.submit(record.getTaskId(),
                    () -> handleWorkflowRoute(command, tenantContext, record));
        } catch (RejectedExecutionException ex) {
            taskLifecycleService.updateTaskStatus(record, TaskStatus.FAILED, Map.of("error", "executor_rejected"));
            log.warn("任务提交被拒绝, tenantId={}, taskId={}, workflowId={}, mode={}",
                    tenantContext.getTenantId(), record.getTaskId(), record.getWorkflowId(), executionMode);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "EXECUTOR_REJECTED", "executor_rejected");
        }
    }

    /**
     * 处理工作流执行。
     */
    private void handleWorkflowRoute(TaskSubmitCommand command,
                                     TenantContext tenantContext,
                                     TaskRecord record) {
        String workflowId = record.getWorkflowId();
        AtomicLong seqCounter = taskEventPublisher.sequenceCounter(tenantContext.getTenantId(), workflowId);
        long startNs = System.nanoTime();
        TaskRecord latest = record;
        try {
            TaskRecord persisted = taskRepository.findById(tenantContext.getTenantId(), record.getTaskId());
            if (persisted != null) {
                latest = persisted;
            }
            taskLifecycleService.updateTaskStatus(latest, TaskStatus.RUNNING, null);
            taskLifecycleService.updateInMemoryRecord(record, TaskStatus.RUNNING, null);
            taskEventPublisher.publishWorkflowStarted(tenantContext, workflowId, seqCounter);

            RuntimeResult runtimeResult = workflowRouter.route(command, tenantContext, workflowId,
                    record.getTaskId(), seqCounter);
            if (runtimeResult == null) {
                log.warn("任务路由返回空结果, tenantId={}, taskId={}, workflowId={}",
                        tenantContext.getTenantId(), record.getTaskId(), workflowId);
            }
            Map<String, Object> payload = taskLifecycleService.buildResultPayload(runtimeResult);
            taskLifecycleService.updateTaskStatus(latest, TaskStatus.COMPLETED, payload);
            taskLifecycleService.updateInMemoryRecord(record, TaskStatus.COMPLETED, payload);
        } catch (TaskRepositoryException ex) {
            taskRepositoryErrorTranslator.handleDuringWorkflow(ex, tenantContext, record, workflowId, seqCounter,
                    taskLifecycleService);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("任务路由失败, tenantId={}, taskId={}, workflowId={}",
                    tenantContext.getTenantId(), record.getTaskId(), workflowId, ex);
            Map<String, Object> payload = taskEventPublisher.publishExecutionError(tenantContext, workflowId,
                    seqCounter, ex);
            try {
                taskLifecycleService.updateTaskStatus(latest, TaskStatus.FAILED, payload);
            } catch (TaskRepositoryException repoEx) {
                taskRepositoryErrorTranslator.handleDuringWorkflow(repoEx, tenantContext, record, workflowId,
                        seqCounter, taskLifecycleService);
                throw repoEx;
            }
            taskLifecycleService.updateInMemoryRecord(record, TaskStatus.FAILED, payload);
            throw ex;
        } finally {
            TaskRecord statusRecord = taskRepositoryErrorTranslator.safeFindTask(taskRepository,
                    tenantContext.getTenantId(), record.getTaskId(), workflowId);
            TaskStatus finalStatus = statusRecord != null && statusRecord.getStatus() != null
                    ? statusRecord.getStatus()
                    : record.getStatus();
            long durationMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
            taskEventPublisher.publishWorkflowCompleted(tenantContext,
                    workflowId,
                    seqCounter,
                    finalStatus != null ? finalStatus.value() : null,
                    durationMs);
        }
    }

    /**
     * 解析执行模式，默认异步。
     */
    private TaskExecutionMode resolveExecutionMode(TaskSubmitCommand command) {
        if (command == null) {
            return TaskExecutionMode.ASYNC;
        }
        TaskExecutionMode mode = command.getExecutionMode();
        if (mode == null) {
            command.setExecutionMode(TaskExecutionMode.ASYNC);
            return TaskExecutionMode.ASYNC;
        }
        return mode;
    }
}

package com.example.agent.orchestration.task;

import com.example.agent.common.error.SyncWaitTimeoutException;
import com.example.agent.orchestration.task.contract.TaskExecutionMode;
import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.example.agent.security.auth.TenantContext;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 任务同步等待服务。
 * <p>用途：集中处理同步等待并发控制、超时策略和短轮询收敛逻辑。
 */
@Service
public class TaskSyncWaitService {

    private static final Logger log = LoggerFactory.getLogger(TaskSyncWaitService.class);

    /**
     * 默认短轮询窗口毫秒。
     */
    private static final long DEFAULT_SYNC_POLL_WINDOW_MS = 120L;

    /**
     * 默认短轮询间隔毫秒。
     */
    private static final long DEFAULT_SYNC_POLL_INTERVAL_MS = 10L;

    private final TaskRepository taskRepository;
    private final TaskLifecycleService taskLifecycleService;

    @Value("${agent.task.sync.wait-timeout-ms:30000}")
    private long syncWaitTimeoutMs;

    @Value("${agent.task.sync.max-wait-timeout-ms:60000}")
    private long syncMaxWaitTimeoutMs;

    @Value("${agent.task.sync.max-concurrency:20}")
    private int syncMaxConcurrency;

    @Value("${agent.task.sync.poll-window-ms:120}")
    private long syncPollWindowMs;

    @Value("${agent.task.sync.poll-interval-ms:10}")
    private long syncPollIntervalMs;

    private Semaphore syncSemaphore;

    public TaskSyncWaitService(TaskRepository taskRepository,
                               TaskLifecycleService taskLifecycleService) {
        this.taskRepository = taskRepository;
        this.taskLifecycleService = taskLifecycleService;
    }

    @PostConstruct
    public void initSyncSemaphore() {
        int permits = Math.max(1, syncMaxConcurrency);
        this.syncSemaphore = new Semaphore(permits);
        log.info("同步等待并发初始化, permits={}", permits);
    }

    /**
     * 在同步模式下等待结果，异步模式直接返回受理响应。
     */
    public TaskSubmissionResult waitForResult(TaskExecutionMode executionMode,
                                              Long requestTimeoutMs,
                                              TenantContext tenantContext,
                                              TaskRecord record,
                                              CompletableFuture<Void> future,
                                              TaskSubmissionResult acceptedResult,
                                              TaskSubmissionResult completedResult) {
        if (executionMode != TaskExecutionMode.SYNC) {
            return acceptedResult;
        }
        if (syncSemaphore == null || !syncSemaphore.tryAcquire()) {
            log.warn("同步并发已达上限, taskId={}, workflowId={}", record.getTaskId(), record.getWorkflowId());
            TaskSubmissionResult degraded = cloneResult(acceptedResult);
            degraded.setStatus(TaskStatus.RUNNING.value());
            return degraded;
        }
        try {
            if (future == null) {
                return buildSyncResultWithoutFuture(tenantContext, record, acceptedResult, completedResult);
            }
            long waitTimeout = resolveWaitTimeoutMs(requestTimeoutMs);
            try {
                future.get(waitTimeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                TaskSubmissionResult timeoutResult = cloneResult(acceptedResult);
                timeoutResult.setStatus(TaskStatus.RUNNING.value());
                throw new SyncWaitTimeoutException(timeoutResult, "sync_wait_timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                TaskSubmissionResult interruptedResult = cloneResult(acceptedResult);
                interruptedResult.setStatus(TaskStatus.RUNNING.value());
                throw new SyncWaitTimeoutException(interruptedResult, "sync_wait_interrupted");
            } catch (ExecutionException ex) {
                log.warn("同步等待任务异常完成, taskId={}, workflowId={}",
                        record.getTaskId(), record.getWorkflowId(), ex.getCause());
            }
            TaskRecord candidate = waitForSyncResult(tenantContext, record);
            if (candidate != null
                    && (candidate.getResult() != null || taskLifecycleService.isTerminalStatus(candidate.getStatus()))) {
                TaskSubmissionResult done = cloneResult(completedResult);
                done.setTaskId(candidate.getTaskId());
                done.setWorkflowId(candidate.getWorkflowId());
                done.setStatus(candidate.getStatus() != null ? candidate.getStatus().value() : null);
                done.setResult(candidate.getResult());
                return done;
            }
            log.info("同步等待结束但结果未收敛, taskId={}, workflowId={}, status={}",
                    record.getTaskId(), record.getWorkflowId(),
                    candidate == null || candidate.getStatus() == null ? null : candidate.getStatus().value());
            TaskSubmissionResult degraded = cloneResult(acceptedResult);
            degraded.setStatus(TaskStatus.RUNNING.value());
            if (candidate != null) {
                degraded.setResult(candidate.getResult());
            }
            return degraded;
        } finally {
            syncSemaphore.release();
        }
    }

    /**
     * 解析等待超时。
     */
    public long resolveWaitTimeoutMs(Long requestTimeoutMs) {
        long defaultTimeout = Math.max(1000, syncWaitTimeoutMs);
        long maxTimeout = Math.max(defaultTimeout, syncMaxWaitTimeoutMs);
        if (requestTimeoutMs == null) {
            return defaultTimeout;
        }
        long configured = requestTimeoutMs;
        if (configured <= 0) {
            return defaultTimeout;
        }
        return Math.min(configured, maxTimeout);
    }

    /**
     * 短轮询等待结果收敛。
     */
    public TaskRecord waitForSyncResult(TenantContext tenantContext, TaskRecord inMemoryRecord) {
        if (tenantContext == null || inMemoryRecord == null) {
            return inMemoryRecord;
        }
        long pollWindow = Math.max(1L, syncPollWindowMs > 0 ? syncPollWindowMs : DEFAULT_SYNC_POLL_WINDOW_MS);
        long pollInterval = Math.max(1L, syncPollIntervalMs > 0 ? syncPollIntervalMs : DEFAULT_SYNC_POLL_INTERVAL_MS);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(pollWindow);
        TaskRecord candidate = null;
        while (System.nanoTime() <= deadline) {
            TaskRecord persisted = taskRepository.findById(tenantContext.getTenantId(), inMemoryRecord.getTaskId());
            candidate = preferCompletedRecord(inMemoryRecord, persisted);
            if (candidate == null) {
                break;
            }
            if (candidate.getResult() != null || taskLifecycleService.isTerminalStatus(candidate.getStatus())) {
                return candidate;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(pollInterval);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return candidate;
            }
        }
        return candidate != null ? candidate : inMemoryRecord;
    }

    private TaskSubmissionResult buildSyncResultWithoutFuture(TenantContext tenantContext,
                                                               TaskRecord record,
                                                               TaskSubmissionResult acceptedResult,
                                                               TaskSubmissionResult completedResult) {
        TaskRecord candidate = waitForSyncResult(tenantContext, record);
        if (candidate != null
                && (candidate.getResult() != null || taskLifecycleService.isTerminalStatus(candidate.getStatus()))) {
            TaskSubmissionResult done = cloneResult(completedResult);
            done.setTaskId(candidate.getTaskId());
            done.setWorkflowId(candidate.getWorkflowId());
            done.setStatus(candidate.getStatus() != null ? candidate.getStatus().value() : null);
            done.setResult(candidate.getResult());
            return done;
        }
        TaskSubmissionResult degraded = cloneResult(acceptedResult);
        degraded.setStatus(TaskStatus.RUNNING.value());
        if (candidate != null) {
            degraded.setResult(candidate.getResult());
        }
        return degraded;
    }

    private TaskRecord preferCompletedRecord(TaskRecord inMemoryRecord, TaskRecord persistedRecord) {
        if (persistedRecord == null) {
            return inMemoryRecord;
        }
        if (persistedRecord.getResult() != null) {
            return persistedRecord;
        }
        if (inMemoryRecord != null && inMemoryRecord.getResult() != null) {
            return inMemoryRecord;
        }
        return persistedRecord;
    }

    private TaskSubmissionResult cloneResult(TaskSubmissionResult source) {
        TaskSubmissionResult target = new TaskSubmissionResult();
        if (source == null) {
            return target;
        }
        target.setTaskId(source.getTaskId());
        target.setWorkflowId(source.getWorkflowId());
        target.setStatus(source.getStatus());
        target.setStreamUrl(source.getStreamUrl());
        target.setResult(source.getResult());
        return target;
    }
}

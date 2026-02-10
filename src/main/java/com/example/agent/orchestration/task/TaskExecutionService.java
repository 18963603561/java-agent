package com.example.agent.orchestration.task;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 任务异步执行器，负责后台线程池调度与幂等去重。
 */
@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    /**
     * 优雅关闭等待超时时间。
     */
    private static final long SHUTDOWN_WAIT_SECONDS = 10L;

    /**
     * 任务执行异步结果映射，防止重复调度。
     */
    private final Map<String, TaskExecution> futures = new ConcurrentHashMap<>();

    /**
     * 线程池拒绝次数计数。
     */
    private final AtomicLong rejectedCount = new AtomicLong(0);

    @Value("${agent.task.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${agent.task.executor.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${agent.task.executor.queue-capacity:200}")
    private int queueCapacity;

    @Value("${agent.task.executor.keep-alive-seconds:180}")
    private long keepAliveSeconds;

    @Value("${agent.task.executor.thread-name-prefix:task-exec-}")
    private String threadNamePrefix;

    private ThreadPoolExecutor executor;

    @PostConstruct
    public void init() {
        int core = Math.max(1, corePoolSize);
        int max = Math.max(core, maxPoolSize);
        int capacity = Math.max(1, queueCapacity);
        long keepAlive = Math.max(1, keepAliveSeconds);
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity);
        this.executor = new ThreadPoolExecutor(core, max, keepAlive, TimeUnit.SECONDS,
                queue, buildThreadFactory(), new TaskRejectedHandler());
        log.info("任务执行器初始化, core={}, max={}, queueCapacity={}, keepAliveSeconds={}",
                core, max, capacity, keepAlive);
    }

    /**
     * 容器销毁时关闭线程池。
     * <p>流程：先停止接收新任务，再等待在途任务完成，超时后强制中断。
     */
    @PreDestroy
    public void shutdown() {
        if (executor == null) {
            return;
        }
        int activeCount = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        log.info("任务执行器开始关闭, activeCount={}, queueSize={}, futuresSize={}",
                activeCount, queueSize, futures.size());
        executor.shutdown();
        try {
            boolean terminated = executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            if (terminated) {
                log.info("任务执行器已优雅关闭, futuresSize={}", futures.size());
                return;
            }
            log.warn("任务执行器等待超时，执行强制关闭, activeCount={}, queueSize={}",
                    executor.getActiveCount(), executor.getQueue().size());
            var droppedTasks = executor.shutdownNow();
            log.warn("任务执行器已强制关闭, droppedTaskCount={}", droppedTasks.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("任务执行器关闭被中断, activeCount={}, queueSize={}",
                    executor.getActiveCount(), executor.getQueue().size(), exception);
            executor.shutdownNow();
        }
    }

    /**
     * 提交任务执行，按任务标识去重。
     *
     * @param taskId 任务标识
     * @param task 执行逻辑
     * @return 对应的异步结果
     */
    public CompletableFuture<Void> submit(String taskId, Runnable task) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("taskId_required");
        }
        Objects.requireNonNull(task, "task_required");
        TaskExecution existing = futures.get(taskId);
        if (existing != null) {
            log.info("任务已在执行队列中, taskId={}", taskId);
            return existing.future;
        }
        TaskExecution created = new TaskExecution();
        TaskExecution previous = futures.putIfAbsent(taskId, created);
        if (previous != null) {
            log.info("任务已在执行队列中, taskId={}", taskId);
            return previous.future;
        }
        created.future.whenComplete((result, error) -> futures.remove(taskId, created));
        try {
            executor.execute(wrap(taskId, task, created.future));
            logPoolStatus("任务已提交", taskId);
            return created.future;
        } catch (RejectedExecutionException ex) {
            futures.remove(taskId, created);
            created.future.completeExceptionally(ex);
            log.warn("任务被线程池拒绝, taskId={}, activeCount={}, queueSize={}, rejectedCount={}",
                    taskId, getActiveCount(), getQueueSize(), getRejectedCount());
            throw ex;
        }
    }

    /**
     * 获取已存在的异步结果，不触发执行。
     *
     * @param taskId 任务标识
     * @return 已存在的异步结果，若不存在则返回 {@code null}
     */
    public CompletableFuture<Void> getFuture(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        TaskExecution execution = futures.get(taskId);
        return execution != null ? execution.future : null;
    }

    /**
     * 清理长期未完成的异步结果。
     *
     * <p>说明：该方法需要外部定时调用，否则长时间运行的任务仍会占用映射。
     *
     * @param maxAge 超过该时长视为异常任务
     * @return 清理数量
     */
    public int purge(Duration maxAge) {
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            return 0;
        }
        Instant now = Instant.now();
        int removed = 0;
        for (Map.Entry<String, TaskExecution> entry : futures.entrySet()) {
            TaskExecution execution = entry.getValue();
            if (execution == null) {
                continue;
            }
            if (execution.future.isDone()) {
                if (futures.remove(entry.getKey(), execution)) {
                    removed++;
                }
                continue;
            }
            if (Duration.between(execution.startAt, now).compareTo(maxAge) > 0) {
                log.warn("任务执行超时未完成, taskId={}, startAt={}", entry.getKey(), execution.startAt);
            }
        }
        return removed;
    }

    /**
     * 获取当前活跃线程数。
     */
    public int getActiveCount() {
        return executor == null ? 0 : executor.getActiveCount();
    }

    /**
     * 获取当前队列长度。
     */
    public int getQueueSize() {
        return executor == null ? 0 : executor.getQueue().size();
    }

    /**
     * 获取拒绝次数。
     */
    public long getRejectedCount() {
        return rejectedCount.get();
    }

    private ThreadFactory buildThreadFactory() {
        return new ThreadFactory() {
            private final AtomicLong index = new AtomicLong(0);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName(threadNamePrefix + index.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    private Runnable wrap(String taskId, Runnable task, CompletableFuture<Void> future) {
        return () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
                log.error("任务执行异常, taskId={}", taskId, ex);
                throw ex;
            } finally {
                logPoolStatus("任务执行结束", taskId);
            }
        };
    }

    private void logPoolStatus(String message, String taskId) {
        log.info("{}, taskId={}, activeCount={}, queueSize={}, rejectedCount={}",
                message, taskId, getActiveCount(), getQueueSize(), getRejectedCount());
    }

    private class TaskRejectedHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            rejectedCount.incrementAndGet();
            throw new RejectedExecutionException("task_executor_rejected");
        }
    }

    private static class TaskExecution {
        private final CompletableFuture<Void> future = new CompletableFuture<>();
        private final Instant startAt = Instant.now();

        private TaskExecution() {
        }
    }
}

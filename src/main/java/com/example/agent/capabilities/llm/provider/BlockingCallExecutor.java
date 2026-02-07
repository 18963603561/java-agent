package com.example.agent.capabilities.llm.provider;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * 阻塞调用执行器。
 *
 * <p>用途：在 Reactor 非阻塞线程中安全执行阻塞逻辑，避免直接阻塞事件线程。</p>
 */
@Component
public class BlockingCallExecutor {

    /**
     * 执行阻塞任务。
     *
     * @param action 待执行任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     * @throws Exception 执行异常
     */
    public <T> T execute(Callable<T> action) throws Exception {
        if (action == null) {
            return null;
        }
        if (!Schedulers.isInNonBlockingThread()) {
            return action.call();
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return action.call();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }
}


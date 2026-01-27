package com.example.agent.runtime;

import com.example.agent.common.ErrorCodeException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionControlServiceTest {

    @Test
    void pauseBlocksUntilResume() throws Exception {
        ExecutionControlService service = new ExecutionControlService();
        service.pause("wf-1");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ExecutionControlState> future = executor.submit(() -> service.awaitIfBlocked("wf-1"));
            Thread.sleep(100);
            assertFalse(future.isDone());
            service.resume("wf-1");
            ExecutionControlState state = future.get(1, TimeUnit.SECONDS);
            assertEquals(ExecutionControlState.RUNNING, state);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancelStopsExecution() throws Exception {
        ExecutionControlService service = new ExecutionControlService();
        service.pause("wf-2");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ExecutionControlState> future = executor.submit(() -> service.awaitIfBlocked("wf-2"));
            Thread.sleep(100);
            service.cancel("wf-2");
            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
            ErrorCodeException cause = assertInstanceOf(ErrorCodeException.class, ex.getCause());
            assertEquals("CANCELLED", cause.getErrorCode());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void approvalWaitsUntilApproved() throws Exception {
        ExecutionControlService service = new ExecutionControlService();
        service.requestApproval("wf-3", Map.of("tool", "demo"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ExecutionControlState> future = executor.submit(() -> service.awaitIfBlocked("wf-3"));
            Thread.sleep(100);
            assertFalse(future.isDone());
            service.decideApproval("wf-3", "approve");
            ExecutionControlState state = future.get(1, TimeUnit.SECONDS);
            assertEquals(ExecutionControlState.RUNNING, state);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void approvalRejectCancels() throws Exception {
        ExecutionControlService service = new ExecutionControlService();
        service.requestApproval("wf-4", Map.of("tool", "demo"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ExecutionControlState> future = executor.submit(() -> service.awaitIfBlocked("wf-4"));
            Thread.sleep(100);
            service.decideApproval("wf-4", "reject");
            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
            ErrorCodeException cause = assertInstanceOf(ErrorCodeException.class, ex.getCause());
            assertEquals("CANCELLED", cause.getErrorCode());
        } finally {
            executor.shutdownNow();
        }
    }
}

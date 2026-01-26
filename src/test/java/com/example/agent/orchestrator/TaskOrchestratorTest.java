package com.example.agent.orchestrator;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskListResponse;
import com.example.agent.common.TaskQuery;
import com.example.agent.common.TaskRequest;
import com.example.agent.common.TaskResponse;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.streaming.EventStreamService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaskOrchestratorTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WorkflowRouter workflowRouter;

    @Mock
    private MetricsPublisher metricsPublisher;

    @Mock
    private EventStreamService eventStreamService;

    private TaskOrchestrator orchestrator;
    private AtomicLong seqCounter;

    @BeforeEach
    void setUp() {
        seqCounter = new AtomicLong(0);
        when(eventStreamService.sequenceCounter(anyString(), anyString())).thenReturn(seqCounter);
        orchestrator = new TaskOrchestrator(eventPublisher, workflowRouter, metricsPublisher, eventStreamService);
    }

    @Test
    void concurrentIdempotencyUsesSingleTask() throws Exception {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idempotency-1");
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        CopyOnWriteArrayList<TaskResponse> responses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                    responses.add(orchestrator.submitTask(request, tenantContext));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        executor.shutdownNow();

        Set<String> taskIds = responses.stream()
                .map(TaskResponse::getTaskId)
                .collect(Collectors.toSet());
        assertEquals(1, taskIds.size());
        verify(workflowRouter, times(1))
                .route(eq(request), eq(tenantContext), anyString(), anyString(), any(AtomicLong.class));
        verify(eventPublisher, times(1))
                .publishEvent(argThat((Object event) -> event instanceof StreamEvent
                        && ((StreamEvent) event).getType() == EventType.WORKFLOW_STARTED));
    }

    @Test
    void listTasksFiltersByTenant() {
        TaskRequest requestA = new TaskRequest();
        requestA.setIdempotencyKey("idem-a");
        TaskRequest requestB = new TaskRequest();
        requestB.setIdempotencyKey("idem-b");

        TenantContext tenantA = new TenantContext("tenant-a", "user-a", List.of(), "req-a", "trace-a");
        TenantContext tenantB = new TenantContext("tenant-b", "user-b", List.of(), "req-b", "trace-b");

        TaskResponse responseA = orchestrator.submitTask(requestA, tenantA);
        orchestrator.submitTask(requestB, tenantB);

        TaskListResponse list = orchestrator.listTasks(new TaskQuery(), tenantA);
        assertEquals(1, list.getTasks().size());
        assertEquals(responseA.getTaskId(), list.getTasks().get(0).getTaskId());
    }
}

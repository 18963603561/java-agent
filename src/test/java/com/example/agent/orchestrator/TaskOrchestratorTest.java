package com.example.agent.orchestrator;

import com.example.agent.orchestration.task.InMemoryTaskRepository;
import com.example.agent.orchestration.task.TaskEventPublisher;
import com.example.agent.orchestration.task.TaskExecutionService;
import com.example.agent.orchestration.task.TaskIdempotencyService;
import com.example.agent.orchestration.task.TaskLifecycleService;
import com.example.agent.orchestration.task.TaskOrchestrator;
import com.example.agent.orchestration.task.TaskRepository;
import com.example.agent.orchestration.task.TaskRepositoryErrorTranslator;
import com.example.agent.orchestration.task.TaskRepositoryException;
import com.example.agent.orchestration.task.TaskStatus;
import com.example.agent.orchestration.task.TaskStatusMapper;
import com.example.agent.orchestration.task.TaskSyncWaitService;
import com.example.agent.orchestration.task.contract.TaskListView;
import com.example.agent.orchestration.task.contract.TaskQueryCommand;
import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.example.agent.orchestration.workflow.WorkflowRouter;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskOrchestratorTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WorkflowRouter workflowRouter;

    @Mock
    private MetricsPublisher metricsPublisher;

    @Mock
    private TracingPublisher tracingPublisher;

    @Mock
    private EventStreamService eventStreamService;

    @Mock
    private TaskExecutionService taskExecutionService;

    private TaskOrchestrator orchestrator;
    private AtomicLong seqCounter;
    private TaskRepository taskRepository;
    private ObjectProvider<StringRedisTemplate> redisProvider;

    @BeforeEach
    void setUp() {
        seqCounter = new AtomicLong(0);
        lenient().when(eventStreamService.sequenceCounter(anyString(), anyString())).thenReturn(seqCounter);
        lenient().when(eventStreamService.nextSequence(anyString(), anyString())).thenReturn(1L);
        lenient().when(tracingPublisher.currentTraceId()).thenReturn("trace-fallback");

        TaskStatusMapper taskStatusMapper = new TaskStatusMapper();
        taskRepository = new InMemoryTaskRepository(taskStatusMapper);
        redisProvider = Mockito.mock(ObjectProvider.class);
        lenient().when(redisProvider.getIfAvailable()).thenReturn(null);

        lenient().when(taskExecutionService.submit(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return CompletableFuture.completedFuture(null);
                });

        lenient().when(workflowRouter.route(any(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    RuntimeResult result = new RuntimeResult();
                    result.setFinalOutput(java.util.Map.of("answer", "ok"));
                    return result;
                });

        TaskLifecycleService lifecycleService = new TaskLifecycleService(taskRepository);
        TaskIdempotencyService idempotencyService = new TaskIdempotencyService(taskRepository, redisProvider);
        TaskSyncWaitService syncWaitService = new TaskSyncWaitService(taskRepository, lifecycleService);
        syncWaitService.initSyncSemaphore();
        TaskEventPublisher taskEventPublisher = new TaskEventPublisher(eventPublisher, eventStreamService,
                metricsPublisher, tracingPublisher);
        TaskRepositoryErrorTranslator taskRepositoryErrorTranslator =
                new TaskRepositoryErrorTranslator(taskEventPublisher);

        orchestrator = new TaskOrchestrator(workflowRouter,
                taskExecutionService,
                taskRepository,
                lifecycleService,
                idempotencyService,
                syncWaitService,
                taskEventPublisher,
                taskRepositoryErrorTranslator);
    }

    @Test
    void concurrentIdempotencyUsesSingleTask() throws Exception {
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setQuery("ping");
        command.setIdempotencyKey("idempotency-1");
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        CopyOnWriteArrayList<TaskSubmissionResult> responses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                    responses.add(orchestrator.submitTask(command, tenantContext));
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
                .map(TaskSubmissionResult::getTaskId)
                .collect(Collectors.toSet());
        assertEquals(1, taskIds.size());
        verify(taskExecutionService, times(1)).submit(anyString(), any(Runnable.class));
        verify(workflowRouter, times(1)).route(any(), eq(tenantContext), anyString(), anyString(), any(AtomicLong.class));
        verify(eventPublisher, times(1))
                .publishEvent(argThat((Object event) -> event instanceof StreamEvent
                        && ((StreamEvent) event).getType() == EventType.TASK_ACCEPTED
                        && "trace-1".equals(((StreamEvent) event).getPayload().get("traceId"))));
        verify(metricsPublisher, times(1)).increment(eq("task.submit.count"), eq("trace-1"));
    }

    @Test
    void listTasksFiltersByTenant() {
        TaskSubmitCommand commandA = new TaskSubmitCommand();
        commandA.setIdempotencyKey("idem-a");
        TaskSubmitCommand commandB = new TaskSubmitCommand();
        commandB.setIdempotencyKey("idem-b");

        TenantContext tenantA = new TenantContext("tenant-a", "user-a", List.of(), "req-a", "trace-a");
        TenantContext tenantB = new TenantContext("tenant-b", "user-b", List.of(), "req-b", "trace-b");

        TaskSubmissionResult responseA = orchestrator.submitTask(commandA, tenantA);
        orchestrator.submitTask(commandB, tenantB);

        TaskListView list = orchestrator.listTasks(new TaskQueryCommand(), tenantA);
        assertEquals(1, list.getTasks().size());
        assertEquals(responseA.getTaskId(), list.getTasks().get(0).getTaskId());
    }

    @Test
    void listTasksUsesStableCursorPagination() {
        TenantContext tenantA = new TenantContext("tenant-a", "user-a", List.of(), "req-a", "trace-a");
        TaskSubmitCommand first = new TaskSubmitCommand();
        first.setIdempotencyKey("idem-1");
        TaskSubmitCommand second = new TaskSubmitCommand();
        second.setIdempotencyKey("idem-2");

        TaskSubmissionResult firstResult = orchestrator.submitTask(first, tenantA);
        TaskSubmissionResult secondResult = orchestrator.submitTask(second, tenantA);

        TaskQueryCommand pageOne = new TaskQueryCommand();
        pageOne.setSize(1);
        TaskListView firstPage = orchestrator.listTasks(pageOne, tenantA);
        assertEquals(1, firstPage.getTasks().size());
        assertTrue(firstPage.isHasMore());
        assertTrue(firstPage.getNextCursor().contains("|"));

        TaskQueryCommand pageTwo = new TaskQueryCommand();
        pageTwo.setSize(1);
        pageTwo.setCursor(firstPage.getNextCursor());
        TaskListView secondPage = orchestrator.listTasks(pageTwo, tenantA);
        assertEquals(1, secondPage.getTasks().size());
        assertFalse(secondPage.isHasMore());

        Set<String> ids = Set.of(firstPage.getTasks().get(0).getTaskId(), secondPage.getTasks().get(0).getTaskId());
        assertEquals(Set.of(firstResult.getTaskId(), secondResult.getTaskId()), ids);
    }

    @Test
    void listTasksRejectsInvalidCursor() {
        TenantContext tenantA = new TenantContext("tenant-a", "user-a", List.of(), "req-a", "trace-a");
        TaskQueryCommand command = new TaskQueryCommand();
        command.setCursor("invalid-cursor");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orchestrator.listTasks(command, tenantA));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void listTasksFiltersByControlledStatus() {
        TenantContext tenantA = new TenantContext("tenant-a", "user-a", List.of(), "req-a", "trace-a");
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setIdempotencyKey("idem-status");
        orchestrator.submitTask(command, tenantA);

        TaskQueryCommand query = new TaskQueryCommand();
        query.setStatus(TaskStatus.COMPLETED);
        query.setSize(10);
        TaskListView list = orchestrator.listTasks(query, tenantA);
        assertEquals(1, list.getTasks().size());
        assertEquals(TaskStatus.COMPLETED, list.getTasks().get(0).getStatus());
    }

    @Test
    void submitTaskReturns503WhenRepositoryFails() {
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setQuery("ping");
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req-a", "trace-a");

        TaskRepository brokenRepository = Mockito.mock(TaskRepository.class);
        when(brokenRepository.save(any())).thenThrow(
                new TaskRepositoryException("save", "tenant-a", "task-x", "wf-x", new RuntimeException("db_down")));

        TaskLifecycleService lifecycleService = new TaskLifecycleService(brokenRepository);
        TaskIdempotencyService idempotencyService = new TaskIdempotencyService(brokenRepository, redisProvider);
        TaskSyncWaitService syncWaitService = new TaskSyncWaitService(brokenRepository, lifecycleService);
        syncWaitService.initSyncSemaphore();
        TaskEventPublisher taskEventPublisher = new TaskEventPublisher(eventPublisher, eventStreamService,
                metricsPublisher, tracingPublisher);
        TaskRepositoryErrorTranslator taskRepositoryErrorTranslator =
                new TaskRepositoryErrorTranslator(taskEventPublisher);

        TaskOrchestrator brokenOrchestrator = new TaskOrchestrator(workflowRouter,
                taskExecutionService,
                brokenRepository,
                lifecycleService,
                idempotencyService,
                syncWaitService,
                taskEventPublisher,
                taskRepositoryErrorTranslator);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> brokenOrchestrator.submitTask(command, tenantContext));
        assertEquals(503, ex.getStatusCode().value());
    }
}

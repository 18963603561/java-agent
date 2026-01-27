package com.example.agent.runtime;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.memory.MemoryWriteService;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.streaming.EventStreamService;
import com.example.agent.tools.hook.HookManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactLoopServiceTest {

    @Test
    void maxIterationsStopsAndEmitsEvent() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("planner", "{\"action\":\"none\",\"shouldStop\":false}", 1, 1));

        ReactRuntimeProperties props = new ReactRuntimeProperties();
        props.setMaxIterations(2);
        props.setMinIterations(1);
        props.setObservationWindow(3);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        ReactLoopService service = buildService(modelInvocationService, props, eventPublisher);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of());
        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");

        ReactLoopResult result = service.run(request, tenantContext, "wf-1", "task-1", new AtomicLong(0));

        assertEquals(2, result.getIterations());
        assertEquals("max_iterations", result.getStopReason());
        assertFalse(result.isCompleted());

        assertTrue(eventPublisher.events.stream().anyMatch(event -> event.getType() == EventType.REACT_STOPPED));
    }

    @Test
    void observationWindowIsApplied() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("planner", "{\"action\":\"none\",\"shouldStop\":false}", 1, 1));

        ReactRuntimeProperties props = new ReactRuntimeProperties();
        props.setMaxIterations(3);
        props.setMinIterations(1);
        props.setObservationWindow(2);

        ReactLoopService service = buildService(modelInvocationService, props, new TestEventPublisher());
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of());
        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");

        ReactLoopResult result = service.run(request, tenantContext, "wf-1", "task-1", new AtomicLong(0));
        assertEquals(2, result.getObservations().size());
    }

    @Test
    void minIterationsPreventsEarlyStop() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        new ModelResponse("planner", "{\"action\":\"stop\",\"shouldStop\":true,\"finalAnswer\":\"ok\"}", 1, 1),
                        new ModelResponse("planner", "{\"action\":\"stop\",\"shouldStop\":true,\"finalAnswer\":\"ok\"}", 1, 1)
                );

        ReactRuntimeProperties props = new ReactRuntimeProperties();
        props.setMaxIterations(3);
        props.setMinIterations(2);
        props.setObservationWindow(2);

        ReactLoopService service = buildService(modelInvocationService, props, new TestEventPublisher());
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of());
        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");

        ReactLoopResult result = service.run(request, tenantContext, "wf-1", "task-1", new AtomicLong(0));
        assertEquals(2, result.getIterations());
        assertEquals("completed", result.getStopReason());
        assertTrue(result.isCompleted());
    }

    @Test
    void approvalIsRequestedDuringAct() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("planner", "{\"action\":\"tool\",\"tool\":\"demo_tool\",\"shouldStop\":false}", 1, 1));

        ReactRuntimeProperties props = new ReactRuntimeProperties();
        props.setMaxIterations(1);
        props.setMinIterations(1);
        props.setObservationWindow(1);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        ExecutionControlService executionControlService = Mockito.mock(ExecutionControlService.class);
        when(executionControlService.getState(any())).thenReturn(ExecutionControlState.RUNNING);
        when(executionControlService.awaitIfBlocked(any())).thenReturn(ExecutionControlState.RUNNING);

        ReactLoopService service = buildService(modelInvocationService, props, eventPublisher, executionControlService,
                Mockito.mock(HookManager.class));

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("requiresApproval", true));
        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");

        service.run(request, tenantContext, "wf-1", "task-1", new AtomicLong(0));

        verify(executionControlService).requestApproval(eq("wf-1"), any());
        assertTrue(eventPublisher.events.stream().anyMatch(event -> event.getType() == EventType.APPROVAL_REQUESTED));
    }

    @Test
    void hookManagerInvokedForAct() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("planner", "{\"action\":\"tool\",\"tool\":\"demo_tool\",\"shouldStop\":false}", 1, 1));

        ReactRuntimeProperties props = new ReactRuntimeProperties();
        props.setMaxIterations(1);
        props.setMinIterations(1);
        props.setObservationWindow(1);

        HookManager hookManager = Mockito.mock(HookManager.class);
        ReactLoopService service = buildService(modelInvocationService, props, new TestEventPublisher(),
                Mockito.mock(ExecutionControlService.class), hookManager);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of());
        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");

        service.run(request, tenantContext, "wf-1", "task-1", new AtomicLong(0));

        verify(hookManager).preTool(any(), any(), eq("demo_tool"));
        verify(hookManager).postTool(any(), any(), eq("demo_tool"), any());
    }

    @Test
    void reactIterationEventsAreEmitted() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("planner", "{\"action\":\"none\",\"shouldStop\":false}", 1, 1));

        ReactRuntimeProperties props = new ReactRuntimeProperties();
        props.setMaxIterations(1);
        props.setMinIterations(1);
        props.setObservationWindow(1);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        ReactLoopService service = buildService(modelInvocationService, props, eventPublisher);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of());
        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");

        service.run(request, tenantContext, "wf-1", "task-1", new AtomicLong(0));

        long startedCount = eventPublisher.events.stream()
                .filter(event -> event.getType() == EventType.REACT_ITERATION_STARTED)
                .count();
        long completedCount = eventPublisher.events.stream()
                .filter(event -> event.getType() == EventType.REACT_ITERATION_COMPLETED)
                .count();
        assertEquals(1, startedCount);
        assertEquals(1, completedCount);
    }

    private ReactLoopService buildService(ModelInvocationService modelInvocationService,
                                          ReactRuntimeProperties properties,
                                          TestEventPublisher eventPublisher) {
        return buildService(modelInvocationService, properties, eventPublisher,
                Mockito.mock(ExecutionControlService.class), Mockito.mock(HookManager.class));
    }

    private ReactLoopService buildService(ModelInvocationService modelInvocationService,
                                          ReactRuntimeProperties properties,
                                          TestEventPublisher eventPublisher,
                                          ExecutionControlService executionControlService,
                                          HookManager hookManager) {
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        EnforcementGateway enforcementGateway = Mockito.mock(EnforcementGateway.class);
        when(enforcementGateway.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("result", "ok"));
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        when(executionControlService.getState(any())).thenReturn(ExecutionControlState.RUNNING);
        when(executionControlService.awaitIfBlocked(any())).thenReturn(ExecutionControlState.RUNNING);

        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        when(tracingPublisher.currentTraceId()).thenReturn("trace");

        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);

        return new ReactLoopService(modelInvocationService,
                modelToolResolver,
                enforcementGateway,
                memoryWriteService,
                executionControlService,
                hookManager,
                eventPublisher,
                tracingPublisher,
                eventStreamService,
                properties,
                new ObjectMapper());
    }

    static class TestEventPublisher implements ApplicationEventPublisher {
        private final List<StreamEvent> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof StreamEvent streamEvent) {
                events.add(streamEvent);
            }
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            // 不处理 ApplicationEvent 分支
        }
    }
}

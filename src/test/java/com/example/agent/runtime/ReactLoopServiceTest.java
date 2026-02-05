package com.example.agent.runtime;

import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.capabilities.memory.MemoryWriteService;
import com.example.agent.capabilities.llm.ModelInvocationService;
import com.example.agent.capabilities.llm.ModelResponse;
import com.example.agent.capabilities.llm.ModelToolResolver;
import com.example.agent.capabilities.llm.PromptAssembler;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.runtime.control.ExecutionControlService;
import com.example.agent.runtime.engine.ReactLoopService;
import com.example.agent.runtime.engine.ReactRuntimeProperties;
import com.example.agent.runtime.control.ExecutionControlState;
import com.example.agent.runtime.engine.ReactLoopResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void reactRepairsOutputWithExtraText() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        String badContent = "说明 {\"action\":\"stop\",\"tool\":\"\",\"arguments\":{},\"shouldStop\":true,"
                + "\"stopReason\":\"completed\",\"finalAnswer\":\"ok\"} 后缀";
        String repaired = "{\"action\":\"stop\",\"tool\":\"\",\"arguments\":{},\"shouldStop\":true,"
                + "\"stopReason\":\"completed\",\"finalAnswer\":\"ok\"}";
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("react", badContent, 10, 10),
                        new ModelResponse("repair", repaired, 10, 10));

        ReactRuntimeProperties properties = new ReactRuntimeProperties();
        properties.setMaxIterations(1);
        properties.setMinIterations(1);
        properties.setObservationWindow(1);

        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        EnforcementGateway enforcementGateway = Mockito.mock(EnforcementGateway.class);
        when(enforcementGateway.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("result", "ok"));
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        ExecutionControlService executionControlService = Mockito.mock(ExecutionControlService.class);
        when(executionControlService.getState(any())).thenReturn(ExecutionControlState.RUNNING);
        when(executionControlService.awaitIfBlocked(any())).thenReturn(ExecutionControlState.RUNNING);

        HookManager hookManager = Mockito.mock(HookManager.class);
        TestEventPublisher eventPublisher = new TestEventPublisher();
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        when(tracingPublisher.currentTraceId()).thenReturn("trace");
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);

        ReactLoopService service = new ReactLoopService(modelInvocationService,
                modelToolResolver,
                promptAssembler,
                enforcementGateway,
                memoryWriteService,
                executionControlService,
                hookManager,
                eventPublisher,
                tracingPublisher,
                eventStreamService,
                properties,
                new ObjectMapper(),
                repairService);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        ReactLoopResult result = service.run(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", "task-1", new AtomicLong(0));

        assertTrue(result.isCompleted());
        assertEquals("ok", result.getFinalAnswer());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_success_total", "scene", "react");
    }

    @Test
    void reactFallsBackWhenRepairFails() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        String badContent = "无法解析";
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("react", badContent, 10, 10),
                        new ModelResponse("repair", "", 10, 10));

        ReactRuntimeProperties properties = new ReactRuntimeProperties();
        properties.setMaxIterations(1);
        properties.setMinIterations(1);
        properties.setObservationWindow(1);

        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        EnforcementGateway enforcementGateway = Mockito.mock(EnforcementGateway.class);
        when(enforcementGateway.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("result", "ok"));
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        ExecutionControlService executionControlService = Mockito.mock(ExecutionControlService.class);
        when(executionControlService.getState(any())).thenReturn(ExecutionControlState.RUNNING);
        when(executionControlService.awaitIfBlocked(any())).thenReturn(ExecutionControlState.RUNNING);

        HookManager hookManager = Mockito.mock(HookManager.class);
        TestEventPublisher eventPublisher = new TestEventPublisher();
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        when(tracingPublisher.currentTraceId()).thenReturn("trace");
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);

        ReactLoopService service = new ReactLoopService(modelInvocationService,
                modelToolResolver,
                promptAssembler,
                enforcementGateway,
                memoryWriteService,
                executionControlService,
                hookManager,
                eventPublisher,
                tracingPublisher,
                eventStreamService,
                properties,
                new ObjectMapper(),
                repairService);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        ReactLoopResult result = service.run(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", "task-1", new AtomicLong(0));

        assertFalse(result.isCompleted());
        assertEquals("max_iterations", result.getStopReason());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "react");
    }

    @Test
    void reactPromptUsesObservationSummary() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        String decision1 = "{\"action\":\"tool\",\"tool\":\"demo_tool\",\"arguments\":{},\"shouldStop\":false,"
                + "\"stopReason\":\"\",\"finalAnswer\":\"\"}";
        String decision2 = "{\"action\":\"none\",\"tool\":\"\",\"arguments\":{},\"shouldStop\":false,"
                + "\"stopReason\":\"\",\"finalAnswer\":\"\"}";
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("planner", decision1, 1, 1),
                        new ModelResponse("planner", decision2, 1, 1));

        ReactRuntimeProperties properties = new ReactRuntimeProperties();
        properties.setMaxIterations(2);
        properties.setMinIterations(1);
        properties.setObservationWindow(2);

        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        EnforcementGateway enforcementGateway = Mockito.mock(EnforcementGateway.class);
        when(enforcementGateway.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("contextSnapshot", "big",
                        "contextBudget", "big",
                        "evidencePack", "big",
                        "tokenUsage", "big"));
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        ExecutionControlService executionControlService = Mockito.mock(ExecutionControlService.class);
        when(executionControlService.getState(any())).thenReturn(ExecutionControlState.RUNNING);
        when(executionControlService.awaitIfBlocked(any())).thenReturn(ExecutionControlState.RUNNING);
        HookManager hookManager = Mockito.mock(HookManager.class);
        TestEventPublisher eventPublisher = new TestEventPublisher();
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        when(tracingPublisher.currentTraceId()).thenReturn("trace");
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);

        ReactLoopService service = new ReactLoopService(modelInvocationService,
                modelToolResolver,
                promptAssembler,
                enforcementGateway,
                memoryWriteService,
                executionControlService,
                hookManager,
                eventPublisher,
                tracingPublisher,
                eventStreamService,
                properties,
                new ObjectMapper(),
                repairService);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        service.run(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", "task-1", new AtomicLong(0));

        ArgumentCaptor<com.example.agent.capabilities.llm.ModelRequest> captor = ArgumentCaptor.forClass(
                com.example.agent.capabilities.llm.ModelRequest.class);
        Mockito.verify(modelInvocationService, Mockito.atLeast(2)).invoke(
                captor.capture(), eq(com.example.agent.capabilities.llm.ModelScene.PLANNER),
                any(), any(), any(), any(), any());
        List<com.example.agent.capabilities.llm.ModelRequest> captured = captor.getAllValues();
        String prompt = captured.get(captured.size() - 1).getPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.contains("contextSnapshot"));
        assertFalse(prompt.contains("contextBudget"));
        assertFalse(prompt.contains("evidencePack"));
        assertFalse(prompt.contains("tokenUsage"));
        assertFalse(prompt.contains("\"content\""));
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
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);

        return new ReactLoopService(modelInvocationService,
                modelToolResolver,
                promptAssembler,
                enforcementGateway,
                memoryWriteService,
                executionControlService,
                hookManager,
                eventPublisher,
                tracingPublisher,
                eventStreamService,
                properties,
                new ObjectMapper(),
                repairService);
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
            // 涓嶅鐞?ApplicationEvent 鍒嗘敮
        }
    }
}

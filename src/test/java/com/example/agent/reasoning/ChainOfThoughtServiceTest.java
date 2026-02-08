package com.example.agent.reasoning;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.provider.ModelResponse;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.streaming.sse.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.reasoning.cot.ChainOfThoughtResult;
import com.example.agent.reasoning.cot.ChainOfThoughtService;
import com.example.agent.reasoning.cot.CotProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ChainOfThoughtServiceTest {

    @Test
    void maxStepsStopsWhenModelKeepsContinuing() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new ModelResponse("cot-model",
                        "{\"stepSummary\":\"summary\",\"shouldContinue\":true,\"confidence\":0.5}",
                        1, 1));

        CotProperties props = new CotProperties();
        props.setMaxSteps(2);
        props.setEmitStepEvents(true);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                promptAssembler,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props, Mockito.mock(JsonOutputRepairService.class));

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("test", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertFalse(result.isCompleted());
        assertEquals("max_steps", result.getStopReason());
        assertEquals(2, result.getStepsCount());
        assertTrue(eventPublisher.events.stream().anyMatch(event -> event.getType() == EventType.COT_STOPPED));
    }

    @Test
    void happyPathEmitsEventSequenceAndOutput() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        new ModelResponse("cot-model",
                                "{\"stepSummary\":\"summary-1\",\"shouldContinue\":true,\"confidence\":0.6}",
                                1, 1),
                        new ModelResponse("cot-model",
                                "{\"stepSummary\":\"summary-2\",\"shouldContinue\":false,\"finalAnswer\":\"done\",\"confidence\":0.8}",
                                1, 1)
                );

        CotProperties props = new CotProperties();
        props.setMaxSteps(3);
        props.setEmitStepEvents(true);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                promptAssembler,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props, Mockito.mock(JsonOutputRepairService.class));

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("test", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertTrue(result.isCompleted());
        assertEquals("completed", result.getStopReason());
        assertEquals("done", result.getFinalAnswer());
        assertEquals(2, result.getStepsCount());

        List<EventType> types = eventPublisher.events.stream().map(StreamEvent::getType).toList();
        assertEquals(EventType.COT_STARTED, types.get(0));
        assertEquals(EventType.COT_COMPLETED, types.get(types.size() - 1));
        long stepCount = types.stream().filter(type -> type == EventType.COT_STEP).count();
        assertEquals(2, stepCount);
    }

    @Test
    void invalidResponseEmitsStoppedEvent() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("cot-model", "not-json", 1, 1));

        CotProperties props = new CotProperties();
        props.setMaxSteps(1);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                promptAssembler,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props, Mockito.mock(JsonOutputRepairService.class));

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("test", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertFalse(result.isCompleted());
        assertEquals("invalid_response", result.getStopReason());
        List<EventType> types = eventPublisher.events.stream().map(StreamEvent::getType).toList();
        assertTrue(types.contains(EventType.COT_STOPPED));
        assertFalse(types.contains(EventType.COT_COMPLETED));
    }

    @Test
    void sanitizeFinalAnswerRemovesReasoningMarkers() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("cot-model",
                        "{\"stepSummary\":\"summary\",\"shouldContinue\":false,\"finalAnswer\":\"Let's think step by step. Step 1... Final Answer: 42\",\"confidence\":0.6}",
                        1, 1));

        CotProperties props = new CotProperties();
        props.setMaxSteps(1);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                promptAssembler,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props, Mockito.mock(JsonOutputRepairService.class));

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("test", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertTrue(result.isCompleted());
        assertEquals("42", result.getFinalAnswer());
        assertFalse(result.getFinalAnswer().toLowerCase().contains("think step by step"));
    }

    @Test
    void parseDecisionAcceptsCodeFenceJson() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("cot-model",
                        "```json\n{\"stepSummary\":\"summary\",\"shouldContinue\":false,\"finalAnswer\":\"answer\",\"confidence\":0.9}\n```",
                        1, 1));

        CotProperties props = new CotProperties();
        props.setMaxSteps(1);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                promptAssembler,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props, Mockito.mock(JsonOutputRepairService.class));

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("test", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertTrue(result.isCompleted());
        assertEquals("answer", result.getFinalAnswer());
        assertEquals("completed", result.getStopReason());
    }

    @Test
    void chainOfThoughtRepairsOutputWithExtraText() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        String badContent = "prefix {\"shouldContinue\":false,\"stepSummary\":\"ok\",\"finalAnswer\":\"done\",\"confidence\":0.8,\"stopReason\":\"completed\"} suffix";
        String repaired = "{\"shouldContinue\":false,\"stepSummary\":\"ok\",\"finalAnswer\":\"done\",\"confidence\":0.8,\"stopReason\":\"completed\"}";
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("cot", badContent, 10, 10),
                        new ModelResponse("repair", repaired, 10, 10));

        CotProperties props = new CotProperties();
        props.setMaxSteps(1);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        ChainOfThoughtService service = new ChainOfThoughtService(modelInvocationService, promptAssembler,
                new ObjectMapper(), new TestEventPublisher(), Mockito.mock(EventStreamService.class), props,
                repairService);

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("test", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertTrue(result.isCompleted());
        assertEquals("done", result.getFinalAnswer());
    }

    @Test
    void chainOfThoughtFallsBackWhenRepairFails() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        String badContent = "invalid";
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("cot", badContent, 10, 10),
                        new ModelResponse("repair", "", 10, 10));

        CotProperties props = new CotProperties();
        props.setMaxSteps(1);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        ChainOfThoughtService service = new ChainOfThoughtService(modelInvocationService, promptAssembler,
                new ObjectMapper(), new TestEventPublisher(), Mockito.mock(EventStreamService.class), props,
                repairService);

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("test", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertFalse(result.isCompleted());
        assertEquals("invalid_response", result.getStopReason());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "cot");
    }

    @Test
    void chainOfThoughtPromptUsesRecentSummariesAndNoRawFields() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        AtomicInteger callIndex = new AtomicInteger(0);
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    int index = callIndex.incrementAndGet();
                    ModelResponse response;
                    if (index < 3) {
                        response = new ModelResponse("cot-model",
                                "{\"stepSummary\":\"s" + index + "\",\"shouldContinue\":true,\"confidence\":0.5}",
                                1, 1);
                    } else {
                        response = new ModelResponse("cot-model",
                                "{\"stepSummary\":\"s3\",\"shouldContinue\":false,\"finalAnswer\":\"done\",\"confidence\":0.5}",
                                1, 1);
                    }
                    if (index == 3) {
                        Object request = invocation.getArgument(0);
                        if (request instanceof com.example.agent.capabilities.llm.provider.ModelRequest modelRequest) {
                            capturedPrompt.set(modelRequest.getPrompt());
                        }
                    }
                    return response;
                });

        CotProperties props = new CotProperties();
        props.setMaxSteps(3);
        props.setMaxStepSummaries(1);

        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                promptAssembler,
                new ObjectMapper(),
                new TestEventPublisher(),
                Mockito.mock(EventStreamService.class),
                props, Mockito.mock(JsonOutputRepairService.class));

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("lastStepSummary", Map.of("summary", "recent"));
        input.put("contextSnapshot", Map.of("large", "snapshot"));
        input.put("evidencePack", Map.of("items", List.of("a")));
        input.put("tokenUsage", Map.of("total", 100));

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("test", input, tenantContext, "wf-1", new AtomicLong(0));

        assertTrue(result.isCompleted());
        String prompt = capturedPrompt.get();
        assertTrue(prompt.contains("s2"));
        assertFalse(prompt.contains("s1"));
        assertFalse(prompt.contains("contextSnapshot"));
        assertFalse(prompt.contains("evidencePack"));
        assertFalse(prompt.contains("tokenUsage"));
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
        }
    }
}

package com.example.agent.reasoning;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelResponse;
import com.example.agent.streaming.EventStreamService;
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
import static org.mockito.Mockito.when;

class ChainOfThoughtServiceTest {

    @Test
    void maxStepsStopsWhenModelKeepsContinuing() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new ModelResponse("cot-model",
                        "{\"stepSummary\":\"继续\",\"shouldContinue\":true,\"confidence\":0.5}",
                        1, 1));

        CotProperties props = new CotProperties();
        props.setMaxSteps(2);
        props.setEmitStepEvents(true);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props
        );

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("测试问题", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

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
                                "{\"stepSummary\":\"拆解问题\",\"shouldContinue\":true,\"confidence\":0.6}",
                                1, 1),
                        new ModelResponse("cot-model",
                                "{\"stepSummary\":\"收敛结论\",\"shouldContinue\":false,\"finalAnswer\":\"答案\",\"confidence\":0.8}",
                                1, 1)
                );

        CotProperties props = new CotProperties();
        props.setMaxSteps(3);
        props.setEmitStepEvents(true);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props
        );

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("测试问题", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertTrue(result.isCompleted());
        assertEquals("completed", result.getStopReason());
        assertEquals("答案", result.getFinalAnswer());
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
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props
        );

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("测试问题", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

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
                        "{\"stepSummary\":\"推理\",\"shouldContinue\":false,"
                                + "\"finalAnswer\":\"Let's think step by step. Step 1... Final Answer: 42\","
                                + "\"confidence\":0.6}",
                        1, 1));

        CotProperties props = new CotProperties();
        props.setMaxSteps(1);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        ChainOfThoughtService service = new ChainOfThoughtService(
                modelInvocationService,
                new ObjectMapper(),
                eventPublisher,
                Mockito.mock(EventStreamService.class),
                props
        );

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        ChainOfThoughtResult result = service.run("测试问题", Map.of(), tenantContext, "wf-1", new AtomicLong(0));

        assertTrue(result.isCompleted());
        assertEquals("42", result.getFinalAnswer());
        assertFalse(result.getFinalAnswer().toLowerCase().contains("think step by step"));
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

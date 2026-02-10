package com.example.agent.reasoning;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import com.example.agent.reasoning.common.config.ReasoningConfigValidator;
import com.example.agent.reasoning.common.config.ReasoningExecutionProperties;
import com.example.agent.reasoning.common.JsonPayloadNormalizer;
import com.example.agent.reasoning.common.ReasoningParseSupport;
import com.example.agent.reasoning.common.telemetry.ReasoningEventPublisher;
import com.example.agent.reasoning.common.telemetry.ReasoningMetricsPublisher;
import com.example.agent.reasoning.common.telemetry.ReasoningTraceRecorder;
import com.example.agent.streaming.sse.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.reasoning.debate.DebateCoordinator;
import com.example.agent.reasoning.debate.DebateRound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DebateCoordinatorTest {

    private DebateCoordinator newCoordinator(ModelInvocationService modelInvocationService,
                                             PromptAssembler promptAssembler,
                                             ApplicationEventPublisher eventPublisher,
                                             EventStreamService eventStreamService,
                                             JsonOutputRepairService repairService) {
        ReasoningConfigResolver reasoningConfigResolver = new ReasoningConfigResolver(
                new ReasoningExecutionProperties(),
                new ReasoningConfigValidator()
        );
        return new DebateCoordinator(
                modelInvocationService,
                promptAssembler,
                new ObjectMapper(),
                repairService,
                new JsonPayloadNormalizer(),
                new ReasoningParseSupport(),
                new ReasoningEventPublisher(eventPublisher, eventStreamService),
                new ReasoningTraceRecorder(modelInvocationService),
                new ReasoningMetricsPublisher(Mockito.mock(MetricsPublisher.class)),
                reasoningConfigResolver
        );
    }

    @Test
    void debateRepairsOutputWithExtraText() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        DebateCoordinator coordinator = newCoordinator(
                modelInvocationService,
                promptAssembler,
                eventPublisher,
                eventStreamService,
                repairService
        );

        String badContent = "not-json";
        String repaired = "{\"conclusion\":\"ok\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("debate"), any()))
                .thenReturn(new ModelResponse("debate", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", repaired, 10, 10));

        DebateRound round = coordinator.debate("topic", new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));
        assertEquals("ok", round.getConclusion());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_success_total", "scene", "debate");
    }

    @Test
    void debateFallsBackWhenRepairFails() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        DebateCoordinator coordinator = newCoordinator(
                modelInvocationService,
                promptAssembler,
                eventPublisher,
                eventStreamService,
                repairService
        );

        String badContent = "无法解析";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("debate"), any()))
                .thenReturn(new ModelResponse("debate", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "", 10, 10));

        DebateRound round = coordinator.debate("topic", new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));
        assertEquals("结论不足", round.getConclusion());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "debate");
    }

    @Test
    void debateReturnsFallbackWhenOutputEmpty() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        DebateCoordinator coordinator = newCoordinator(
                modelInvocationService,
                promptAssembler,
                eventPublisher,
                eventStreamService,
                repairService
        );

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("debate"), any()))
                .thenReturn(new ModelResponse("debate", "", 10, 10));

        DebateRound round = coordinator.debate("topic", new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));
        assertEquals("结论不足", round.getConclusion());
    }

    @Test
    void debateTruncatesTooLongConclusion() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        DebateCoordinator coordinator = newCoordinator(
                modelInvocationService,
                promptAssembler,
                eventPublisher,
                eventStreamService,
                repairService
        );

        String longConclusion = "a".repeat(700);
        String content = "{\"conclusion\":\"" + longConclusion + "\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("debate"), any()))
                .thenReturn(new ModelResponse("debate", content, 10, 10));

        DebateRound round = coordinator.debate("topic", new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));
        assertEquals(600, round.getConclusion().length());
    }
}

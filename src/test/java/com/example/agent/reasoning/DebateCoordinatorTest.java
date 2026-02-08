package com.example.agent.reasoning;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.provider.ModelRequest;
import com.example.agent.capabilities.llm.provider.ModelResponse;
import com.example.agent.capabilities.llm.provider.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
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

    @Test
    void debateRepairsOutputWithExtraText() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        DebateCoordinator coordinator = new DebateCoordinator(modelInvocationService, promptAssembler,
                new ObjectMapper(), eventPublisher, eventStreamService, repairService);

        String badContent = "说明:{\"conclusion\":\"ok\"}后缀";
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
        DebateCoordinator coordinator = new DebateCoordinator(modelInvocationService, promptAssembler,
                new ObjectMapper(), eventPublisher, eventStreamService, repairService);

        String badContent = "无法解析";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("debate"), any()))
                .thenReturn(new ModelResponse("debate", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "", 10, 10));

        DebateRound round = coordinator.debate("topic", new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));
        assertEquals(badContent, round.getConclusion());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "debate");
    }
}
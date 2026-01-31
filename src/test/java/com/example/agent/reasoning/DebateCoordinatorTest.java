package com.example.agent.reasoning;

import com.example.agent.auth.TenantContext;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.PromptAssembler;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.repair.JsonOutputRepairService;
import com.example.agent.streaming.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

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
package com.example.agent.multiagent;

import com.example.agent.auth.TenantContext;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.repair.JsonOutputRepairService;
import com.example.agent.runtime.StepRequest;
import com.example.agent.streaming.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MultiAgentCoordinatorTest {

    @Test
    void multiAgentRepairsOutputWithExtraText() {
        AgentProfileProperties profileProperties = new AgentProfileProperties();
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        MultiAgentCoordinator coordinator = new MultiAgentCoordinator(profileProperties, modelInvocationService,
                modelToolResolver, promptAssembler, new ObjectMapper(), eventPublisher, eventStreamService,
                repairService);

        String badContent = "说明:{\"team\":[{\"role\":\"r\",\"responsibility\":\"x\"}]}后缀";
        String repaired = "{\"team\":[{\"role\":\"r\",\"responsibility\":\"x\"}]}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.PLANNER),
                any(), any(), any(), eq("multi_agent"), any()))
                .thenReturn(new ModelResponse("multi", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", repaired, 10, 10));

        StepRequest step = new StepRequest("TOOL", Map.of());
        Map<String, Object> result = coordinator.coordinate(step,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));
        @SuppressWarnings("unchecked")
        List<AgentRole> roles = (List<AgentRole>) result.get("team");
        assertFalse(roles.isEmpty());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_success_total", "scene", "multiagent");
    }

    @Test
    void multiAgentFallsBackWhenRepairFails() {
        AgentProfileProperties profileProperties = new AgentProfileProperties();
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        MultiAgentCoordinator coordinator = new MultiAgentCoordinator(profileProperties, modelInvocationService,
                modelToolResolver, promptAssembler, new ObjectMapper(), eventPublisher, eventStreamService,
                repairService);

        String badContent = "无法解析";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.PLANNER),
                any(), any(), any(), eq("multi_agent"), any()))
                .thenReturn(new ModelResponse("multi", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "", 10, 10));

        StepRequest step = new StepRequest("TOOL", Map.of());
        Map<String, Object> result = coordinator.coordinate(step,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));
        @SuppressWarnings("unchecked")
        List<AgentRole> roles = (List<AgentRole>) result.get("team");
        assertFalse(roles.isEmpty());
        assertEquals("default", roles.get(0).getRoleId());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "multiagent");
    }
}
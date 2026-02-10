package com.example.agent.multiagent;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.orchestration.multiagent.AgentProfileProperties;
import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentCoordinator;
import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.MultiAgentInputSummaryBuilder;
import com.example.agent.orchestration.multiagent.MultiAgentPromptBuilder;
import com.example.agent.orchestration.multiagent.MultiAgentRoleResolver;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MultiAgentCoordinatorTest {

    private MultiAgentCoordinator buildCoordinator(AgentProfileProperties profileProperties,
                                                   ModelInvocationService modelInvocationService,
                                                   ModelToolResolver modelToolResolver,
                                                   PromptAssembler promptAssembler,
                                                   ApplicationEventPublisher eventPublisher,
                                                   EventStreamService eventStreamService,
                                                   JsonOutputRepairService repairService) {
        ObjectMapper objectMapper = new ObjectMapper();
        MultiAgentInputSummaryBuilder inputSummaryBuilder = new MultiAgentInputSummaryBuilder();
        MultiAgentPromptBuilder promptBuilder = new MultiAgentPromptBuilder(objectMapper, promptAssembler);
        MultiAgentRoleResolver roleResolver = new MultiAgentRoleResolver(objectMapper, repairService, profileProperties);
        MultiAgentEventPublisher eventHandler = new MultiAgentEventPublisher(eventPublisher, eventStreamService);
        return new MultiAgentCoordinator(modelInvocationService,
                modelToolResolver,
                inputSummaryBuilder,
                promptBuilder,
                roleResolver,
                eventHandler);
    }

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
        MultiAgentCoordinator coordinator = buildCoordinator(profileProperties,
                modelInvocationService,
                modelToolResolver,
                promptAssembler,
                eventPublisher,
                eventStreamService,
                repairService);

        String badContent = "说明:{\"team\":[{\"roleId\":\"r\",\"name\":\"Planner\",\"description\":\"x\"}]}后缀";
        String repaired = "{\"team\":[{\"roleId\":\"r\",\"name\":\"Planner\",\"description\":\"x\"}]}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.PLANNER),
                any(), any(), any(), eq("multi_agent"), any()))
                .thenReturn(new ModelResponse("multi", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", repaired, 10, 10));

        StepSpec step = new StepSpec("TOOL", Map.of());
        Map<String, Object> result = coordinator.coordinate(step,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));
        @SuppressWarnings("unchecked")
        List<AgentRole> roles = (List<AgentRole>) result.get("team");
        assertFalse(roles.isEmpty());
        assertEquals("r", roles.get(0).getRoleId());
        assertEquals("Planner", roles.get(0).getName());
        assertEquals("x", roles.get(0).getDescription());
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
        MultiAgentCoordinator coordinator = buildCoordinator(profileProperties,
                modelInvocationService,
                modelToolResolver,
                promptAssembler,
                eventPublisher,
                eventStreamService,
                repairService);

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.PLANNER),
                any(), any(), any(), eq("multi_agent"), any()))
                .thenReturn(new ModelResponse("multi", "invalid_json", 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "", 10, 10));

        StepSpec step = new StepSpec("TOOL", Map.of());
        Map<String, Object> result = coordinator.coordinate(step,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));
        @SuppressWarnings("unchecked")
        List<AgentRole> roles = (List<AgentRole>) result.get("team");
        assertFalse(roles.isEmpty());
        assertEquals("default", roles.get(0).getRoleId());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "multiagent");
    }

    @Test
    void multiAgentPromptUsesInputSummary() {
        AgentProfileProperties profileProperties = new AgentProfileProperties();
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        MultiAgentCoordinator coordinator = buildCoordinator(profileProperties,
                modelInvocationService,
                modelToolResolver,
                promptAssembler,
                eventPublisher,
                eventStreamService,
                repairService);

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.PLANNER),
                any(), any(), any(), eq("multi_agent"), any()))
                .thenReturn(new ModelResponse("multi", "{\"team\":[{\"roleId\":\"r1\",\"name\":\"Planner\",\"modelId\":null,\"description\":\"负责规划\"}]}", 10, 10));

        Map<String, Object> input = Map.of(
                "query", "ping",
                "goal", "assist",
                "constraints", "fast",
                "tools", List.of("search"),
                "contextSnapshot", Map.of("large", "snapshot"),
                "evidencePack", Map.of("items", List.of("a")),
                "tokenUsage", Map.of("total", 100)
        );
        StepSpec step = new StepSpec("TOOL", input);

        coordinator.coordinate(step,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));

        ArgumentCaptor<ModelRequest> requestCaptor = ArgumentCaptor.forClass(ModelRequest.class);
        Mockito.verify(modelInvocationService).invoke(requestCaptor.capture(), eq(ModelScene.PLANNER),
                any(), eq("wf-1"), any(), eq("multi_agent"), any());
        String prompt = requestCaptor.getValue().getPrompt();
        assertTrue(prompt.contains("MULTI_AGENT_CONTEXT_JSON"));
        assertTrue(prompt.contains("roleId"));
        assertTrue(prompt.contains("description"));
        assertTrue(prompt.contains("query"));
        assertTrue(prompt.contains("goal"));
        assertTrue(prompt.contains("constraints"));
        assertTrue(prompt.contains("tools"));
        assertFalse(prompt.contains("contextSnapshot"));
        assertFalse(prompt.contains("evidencePack"));
        assertFalse(prompt.contains("tokenUsage"));
    }

    @Test
    void multiAgentParsesNewContractFields() {
        AgentProfileProperties profileProperties = new AgentProfileProperties();
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        MultiAgentCoordinator coordinator = buildCoordinator(profileProperties,
                modelInvocationService,
                modelToolResolver,
                promptAssembler,
                eventPublisher,
                eventStreamService,
                repairService);

        String content = "{\"team\":[{\"roleId\":\"planner\",\"name\":\"Planner\",\"modelId\":\"gpt-4o\",\"description\":\"负责规划\"}]}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.PLANNER),
                any(), any(), any(), eq("multi_agent"), any()))
                .thenReturn(new ModelResponse("multi", content, 10, 10));

        StepSpec step = new StepSpec("TOOL", Map.of("query", "q"));
        Map<String, Object> result = coordinator.coordinate(step,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));
        @SuppressWarnings("unchecked")
        List<AgentRole> roles = (List<AgentRole>) result.get("team");
        assertEquals(1, roles.size());
        assertEquals("planner", roles.get(0).getRoleId());
        assertEquals("Planner", roles.get(0).getName());
        assertEquals("gpt-4o", roles.get(0).getModelId());
        assertEquals("负责规划", roles.get(0).getDescription());
    }
}

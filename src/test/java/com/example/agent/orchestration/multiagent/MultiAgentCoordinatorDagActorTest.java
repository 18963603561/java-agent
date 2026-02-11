package com.example.agent.orchestration.multiagent;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.orchestration.multiagent.support.StepArgumentReader;
import com.example.agent.orchestration.multiagent.dag.DagPlanner;
import com.example.agent.orchestration.multiagent.dag.actor.DagActorRuntime;
import com.example.agent.orchestration.multiagent.usecase.ExecutionResultAssembler;
import com.example.agent.orchestration.multiagent.usecase.ExecutionRouteDecider;
import com.example.agent.orchestration.multiagent.usecase.MultiAgentExecutionUseCase;
import com.example.agent.orchestration.multiagent.handoff.HandoffService;
import com.example.agent.orchestration.multiagent.handoff.HandoffStateMachine;
import com.example.agent.orchestration.multiagent.handoff.InMemoryHandoffRepository;
import com.example.agent.orchestration.multiagent.handoff.TopicDependencyCoordinator;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.orchestration.multiagent.supervisor.SupervisorCoordinator;
import com.example.agent.orchestration.multiagent.supervisor.SupervisorPolicy;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.sse.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MultiAgentCoordinatorDagActorTest {

    @Test
    void shouldExecuteDagWithActorRuntimeWhenDependenciesPresent() {
        AgentProfileProperties profileProperties = new AgentProfileProperties();
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);

        ObjectMapper objectMapper = new ObjectMapper();
        MultiAgentInputSummaryBuilder inputSummaryBuilder = new MultiAgentInputSummaryBuilder();
        MultiAgentPromptBuilder promptBuilder = new MultiAgentPromptBuilder(objectMapper, promptAssembler);
        MultiAgentRoleResolver roleResolver = new MultiAgentRoleResolver(objectMapper, repairService, profileProperties);
        MultiAgentEventPublisher multiAgentEventPublisher = new MultiAgentEventPublisher(eventPublisher, eventStreamService);
        MultiAgentRoutingPolicy routingPolicy = new MultiAgentRoutingPolicy();
        StepArgumentReader stepArgumentReader = new StepArgumentReader();
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        DagPlanner dagPlanner = new DagPlanner(stepArgumentReader);
        DagActorRuntime dagActorRuntime = new DagActorRuntime(workspaceSyncService, multiAgentEventPublisher);

        TopicDependencyCoordinator dependencyCoordinator = new TopicDependencyCoordinator(workspaceSyncService,
                500L,
                10L);
        SupervisorPolicy supervisorPolicy = new SupervisorPolicy(2, "PARTIAL_SUCCESS");
        HandoffService handoffService = new HandoffService(
                workspaceSyncService,
                multiAgentEventPublisher,
                new InMemoryHandoffRepository(),
                new HandoffStateMachine());
        SupervisorCoordinator supervisorCoordinator = new SupervisorCoordinator(supervisorPolicy,
                handoffService,
                dependencyCoordinator,
                multiAgentEventPublisher);
        ExecutionRouteDecider executionRouteDecider = new ExecutionRouteDecider(routingPolicy, stepArgumentReader);
        ExecutionResultAssembler executionResultAssembler = new ExecutionResultAssembler();
        MultiAgentExecutionUseCase executionUseCase = new MultiAgentExecutionUseCase(
                executionRouteDecider,
                executionResultAssembler,
                supervisorCoordinator,
                dagPlanner,
                dagActorRuntime);

        MultiAgentCoordinator coordinator = new MultiAgentCoordinator(modelInvocationService,
                modelToolResolver,
                inputSummaryBuilder,
                promptBuilder,
                roleResolver,
                multiAgentEventPublisher,
                executionUseCase);

        String content = "{\"team\":["
                + "{\"roleId\":\"planner\",\"name\":\"Planner\",\"modelId\":\"m1\",\"description\":\"plan\"},"
                + "{\"roleId\":\"writer\",\"name\":\"Writer\",\"modelId\":\"m2\",\"description\":\"write\"}"
                + "]}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.PLANNER),
                any(), any(), any(), eq("multi_agent"), any()))
                .thenReturn(new ModelResponse("multi", content, 10, 10));

        StepSpec step = new StepSpec("MULTI_AGENT", Map.of(
                "query", "q",
                "consumes", List.of("topic.input"),
                "produces", List.of("topic.output"),
                "dagDependencies", Map.of("writer", List.of("planner"))
        ));
        Map<String, Object> result = coordinator.coordinate(step,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-actor-dag",
                new AtomicLong(0));

        assertEquals("DAG", result.get("mode"));
        assertEquals("COMPLETED", result.get("status"));
        assertTrue(result.containsKey("dagNodes"));
        assertTrue(workspaceSyncService.hasTopic("wf-actor-dag", "topic.output"));
    }
}

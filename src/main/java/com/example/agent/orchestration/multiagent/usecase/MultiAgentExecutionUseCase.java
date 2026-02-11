package com.example.agent.orchestration.multiagent.usecase;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentExecutionMode;
import com.example.agent.orchestration.multiagent.dag.DagPlan;
import com.example.agent.orchestration.multiagent.dag.DagPlanner;
import com.example.agent.orchestration.multiagent.dag.actor.DagActorRuntime;
import com.example.agent.orchestration.multiagent.model.DagExecutionResult;
import com.example.agent.orchestration.multiagent.model.MultiAgentExecutionResult;
import com.example.agent.orchestration.multiagent.model.SupervisorExecutionResult;
import com.example.agent.orchestration.multiagent.supervisor.SupervisorCoordinator;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 多智能体执行用例。
 *
 * <p>用途：统一承载执行路径分发与 DAG/Supervisor 调用，保持应用入口简洁。</p>
 */
@Component
public class MultiAgentExecutionUseCase {

    private final ExecutionRouteDecider executionRouteDecider;
    private final ExecutionResultAssembler executionResultAssembler;
    private final SupervisorCoordinator supervisorCoordinator;
    private final DagPlanner dagPlanner;
    private final DagActorRuntime dagActorRuntime;

    /**
     * 构造执行用例。
     */
    public MultiAgentExecutionUseCase(ExecutionRouteDecider executionRouteDecider,
                                      ExecutionResultAssembler executionResultAssembler,
                                      SupervisorCoordinator supervisorCoordinator,
                                      DagPlanner dagPlanner,
                                      DagActorRuntime dagActorRuntime) {
        this.executionRouteDecider = executionRouteDecider;
        this.executionResultAssembler = executionResultAssembler;
        this.supervisorCoordinator = supervisorCoordinator;
        this.dagPlanner = dagPlanner;
        this.dagActorRuntime = dagActorRuntime;
    }

    /**
     * 执行多智能体流程并返回统一结果。
     */
    public MultiAgentExecutionResult execute(StepSpec step,
                                             TenantContext tenantContext,
                                             String workflowId,
                                             AtomicLong seqCounter,
                                             List<AgentRole> roles,
                                             String rawRef) {
        List<String> consumes = executionRouteDecider.resolveTopicList(step, "consumes");
        List<String> produces = executionRouteDecider.resolveTopicList(step, "produces");
        MultiAgentExecutionMode mode = executionRouteDecider.resolveMode(step);

        DagExecutionResult dagResult = null;
        SupervisorExecutionResult supervisorResult = null;
        if (mode == MultiAgentExecutionMode.SUPERVISOR) {
            supervisorResult = supervisorCoordinator.coordinate(workflowId,
                    tenantContext,
                    seqCounter,
                    roles,
                    consumes,
                    produces);
        } else {
            dagResult = executeDag(workflowId,
                    tenantContext,
                    seqCounter,
                    roles,
                    consumes,
                    produces,
                    step);
        }

        return executionResultAssembler.assemble(dagResult, supervisorResult, roles, mode, rawRef);
    }

    /**
     * 执行 DAG 路径。
     */
    private DagExecutionResult executeDag(String workflowId,
                                          TenantContext tenantContext,
                                          AtomicLong seqCounter,
                                          List<AgentRole> roles,
                                          List<String> consumes,
                                          List<String> produces,
                                          StepSpec step) {
        StepSpec dagStep = new StepSpec();
        if (step != null) {
            dagStep.setStepType(step.getStepType());
            dagStep.setContext(step.getContext());
            dagStep.setPolicy(step.getPolicy());
            dagStep.setDependsOn(step.getDependsOn() == null ? null : new java.util.ArrayList<>(step.getDependsOn()));
        }

        Map<String, Object> dagArguments = new HashMap<>();
        if (step != null && step.getArguments() != null && !step.getArguments().isEmpty()) {
            dagArguments.putAll(step.getArguments());
        }
        if (consumes != null && !consumes.isEmpty()) {
            dagArguments.put("consumes", consumes);
        }
        if (produces != null && !produces.isEmpty()) {
            dagArguments.put("produces", produces);
        }
        dagStep.setArguments(dagArguments);

        DagPlan dagPlan = dagPlanner.build(roles, dagStep);
        DagActorRuntime.DagActorRuntimeResult runtimeResult = dagActorRuntime.run(workflowId,
                tenantContext,
                seqCounter,
                dagPlan);

        DagExecutionResult result = new DagExecutionResult();
        result.setStatus(runtimeResult.getStatus());
        result.setDagRunId(runtimeResult.getDagRunId());
        result.setDagOrder(runtimeResult.getDagOrder());
        result.setDagNodes(runtimeResult.getDagNodes());
        result.setFailures(runtimeResult.getFailures());
        return result;
    }
}

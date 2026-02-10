package com.example.agent.runtime.step.executor;

import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.ReasoningInput;
import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import com.example.agent.reasoning.common.orchestrator.ReasoningExecutionPlan;
import com.example.agent.reasoning.common.orchestrator.ReasoningOrchestrator;
import com.example.agent.reasoning.common.result.ThoughtTreePayload;
import com.example.agent.reasoning.common.selection.ReasoningDegradePolicy;
import com.example.agent.reasoning.common.selection.ReasoningStrategySelector;
import com.example.agent.reasoning.common.telemetry.ReasoningEventPublisher;
import com.example.agent.reasoning.thoughttree.ThoughtNode;
import com.example.agent.reasoning.thoughttree.ThoughtTreeConfig;
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import com.example.agent.streaming.domain.EventType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 思维树步骤执行器。
 *
 * <p>用途：封装 {@code THOUGHT_TREE} 步骤执行逻辑，并发布思维节点展开事件。
 * <p>输入：步骤定义与运行上下文。
 * <p>输出：思维树结果映射。
 * <p>边界：当节点为空时不发布事件，异常由上层统一捕获并按恢复策略处理。
 */
@Component
public class ThoughtTreeStepExecutor implements StepTypeExecutor {

    private final ReasoningOrchestrator reasoningOrchestrator;
    private final ReasoningInputResolver reasoningInputResolver;
    private final ReasoningEventPublisher reasoningEventPublisher;
    private final ReasoningConfigResolver reasoningConfigResolver;
    private final ReasoningStrategySelector reasoningStrategySelector;
    private final ReasoningDegradePolicy reasoningDegradePolicy;

    public ThoughtTreeStepExecutor(ReasoningOrchestrator reasoningOrchestrator,
                                   ReasoningInputResolver reasoningInputResolver,
                                   ReasoningEventPublisher reasoningEventPublisher,
                                   ReasoningConfigResolver reasoningConfigResolver,
                                   ReasoningStrategySelector reasoningStrategySelector,
                                   ReasoningDegradePolicy reasoningDegradePolicy) {
        this.reasoningOrchestrator = reasoningOrchestrator;
        this.reasoningInputResolver = reasoningInputResolver;
        this.reasoningEventPublisher = reasoningEventPublisher;
        this.reasoningConfigResolver = reasoningConfigResolver;
        this.reasoningStrategySelector = reasoningStrategySelector;
        this.reasoningDegradePolicy = reasoningDegradePolicy;
    }

    @Override
    public boolean supports(String stepType) {
        return stepType != null && "THOUGHT_TREE".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        Map<String, Object> input = reasoningInputResolver.resolveExecutionInput(request);
        if (request != null && request.getStep() != null) {
            StepInputView stepInputView = StepInputView.from(request.getStep(), request.getRuntimeContext());
            Map<String, Object> inputFromStep = stepInputView.toExecutionMap();
            if (inputFromStep != null && !inputFromStep.isEmpty()) {
                input.putAll(inputFromStep);
            }
        }
        String prompt = reasoningInputResolver.resolvePrompt(
                input,
                request != null ? request.getTaskRequest() : null,
                "prompt",
                "question",
                "topic",
                "query"
        );
        ThoughtTreeConfig config = reasoningConfigResolver.resolveThoughtTreeConfig(input);
        input.put("reasoning.thoughtTreeConfig", config);
        ReasoningRequest reasoningRequest = new ReasoningRequest(
                "THOUGHT_TREE",
                prompt,
                input,
                request != null ? request.getTenantContext() : null,
                request != null ? request.getWorkflowId() : null,
                request != null ? request.getSeqCounter() : null
        );
        ReasoningInput reasoningInput = reasoningRequest.getInput();
        String preferredStrategy = reasoningInputResolver.resolvePreferredStrategy(reasoningInput, "thought_tree");
        String primaryStrategy = reasoningStrategySelector.selectPrimary(preferredStrategy, reasoningInput);
        boolean parallelEnabled = reasoningInputResolver.resolveParallelEnabled(reasoningInput);
        List<String> candidateStrategies = parallelEnabled
                ? reasoningInputResolver.resolveCandidateStrategies(reasoningInput, primaryStrategy)
                : reasoningDegradePolicy.resolveFallbackOrder(primaryStrategy);
        ReasoningExecutionPlan.Builder planBuilder = ReasoningExecutionPlan.builder()
                .primaryStrategy(primaryStrategy)
                .candidateStrategies(candidateStrategies)
                .parallelEnabled(parallelEnabled);
        Long timeoutMillis = reasoningInput.getLong("timeoutMillis");
        if (timeoutMillis != null) {
            planBuilder.timeoutMillis(timeoutMillis);
        }
        ReasoningExecutionPlan plan = planBuilder.build();
        ReasoningResult result = reasoningOrchestrator.execute(reasoningRequest, plan);
        ThoughtTreePayload payload = result.getPayload() instanceof ThoughtTreePayload typedPayload
                ? typedPayload
                : new ThoughtTreePayload(0, 0, List.of(), result.getSummary());
        List<ThoughtNode> nodes = payload.getBestPath();
        publishThoughtEvents(request, nodes);

        Map<String, Object> output = new HashMap<>();
        output.put("bestSolution", result.getSummary());
        output.put("confidence", result.getConfidence());
        output.put("totalThoughts", payload.getTotalThoughts());
        output.put("treeDepth", payload.getTreeDepth());
        output.put("nodes", nodes);
        return StepExecutionOutput.fromPayload(output);
    }

    private void publishThoughtEvents(StepExecutionRequest request, List<ThoughtNode> nodes) {
        if (nodes == null || nodes.isEmpty() || request.getEventPublisher() == null) {
            return;
        }
        for (ThoughtNode node : nodes) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("nodeId", node.getNodeId());
            payload.put("score", node.getScore());
            payload.put("depth", node.getDepth());
            if (node.getParentId() != null) {
                payload.put("parentId", node.getParentId());
            }
            reasoningEventPublisher.publishRuntimeEvent(
                    request.getEventPublisher(),
                    request.getTenantContext(),
                    request.getWorkflowId(),
                    request.getSeqCounter(),
                    EventType.THOUGHT_EXPANDED,
                    "thought_tree",
                    payload
            );
        }
    }

}

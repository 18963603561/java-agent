package com.example.agent.runtime.step.executor;

import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.ReasoningInput;
import com.example.agent.reasoning.common.orchestrator.ReasoningExecutionPlan;
import com.example.agent.reasoning.common.orchestrator.ReasoningOrchestrator;
import com.example.agent.reasoning.common.selection.ReasoningDegradePolicy;
import com.example.agent.reasoning.common.selection.ReasoningStrategySelector;
import com.example.agent.reasoning.common.result.CotPayload;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 链式推理步骤执行器。
 *
 * <p>用途：封装 {@code CHAIN_OF_THOUGHT}/{@code COT} 步骤执行逻辑，输出结构化摘要结果。
 * <p>输入：任务请求、步骤输入与运行上下文。
 * <p>输出：链式推理结果映射。
 * <p>边界：异常由上层统一捕获并按恢复策略处理。
 */
@Component
public class ChainOfThoughtStepExecutor implements StepTypeExecutor {

    private final ReasoningOrchestrator reasoningOrchestrator;
    private final ReasoningInputResolver reasoningInputResolver;
    private final ReasoningStrategySelector reasoningStrategySelector;
    private final ReasoningDegradePolicy reasoningDegradePolicy;

    public ChainOfThoughtStepExecutor(ReasoningOrchestrator reasoningOrchestrator,
                                      ReasoningInputResolver reasoningInputResolver,
                                      ReasoningStrategySelector reasoningStrategySelector,
                                      ReasoningDegradePolicy reasoningDegradePolicy) {
        this.reasoningOrchestrator = reasoningOrchestrator;
        this.reasoningInputResolver = reasoningInputResolver;
        this.reasoningStrategySelector = reasoningStrategySelector;
        this.reasoningDegradePolicy = reasoningDegradePolicy;
    }

    @Override
    public boolean supports(String stepType) {
        if (stepType == null) {
            return false;
        }
        return "CHAIN_OF_THOUGHT".equalsIgnoreCase(stepType) || "COT".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        ReasoningRequest reasoningRequest = reasoningInputResolver.buildRequest(
                "COT",
                request,
                "question",
                "topic",
                "prompt",
                "query"
        );
        ReasoningInput stepInput = reasoningRequest.getInput();
        String preferredStrategy = reasoningInputResolver.resolvePreferredStrategy(stepInput, "cot");
        String primaryStrategy = reasoningStrategySelector.selectPrimary(preferredStrategy, stepInput);
        boolean parallelEnabled = reasoningInputResolver.resolveParallelEnabled(stepInput);
        List<String> candidateStrategies = parallelEnabled
                ? reasoningInputResolver.resolveCandidateStrategies(stepInput, primaryStrategy)
                : reasoningDegradePolicy.resolveFallbackOrder(primaryStrategy);
        ReasoningExecutionPlan.Builder planBuilder = ReasoningExecutionPlan.builder()
                .primaryStrategy(primaryStrategy)
                .candidateStrategies(candidateStrategies)
                .parallelEnabled(parallelEnabled);
        Long timeoutMillis = stepInput.getLong("timeoutMillis");
        if (timeoutMillis != null) {
            planBuilder.timeoutMillis(timeoutMillis);
        }
        ReasoningExecutionPlan plan = planBuilder.build();
        ReasoningResult result = reasoningOrchestrator.execute(reasoningRequest, plan);
        CotPayload payload = result.getPayload() instanceof CotPayload typedPayload
                ? typedPayload
                : new CotPayload(0, result.getSummary());

        Map<String, Object> output = new HashMap<>();
        output.put("finalAnswer", result.getSummary());
        output.put("stepsCount", payload.getStepsCount());
        output.put("confidence", result.getConfidence());
        output.put("stopReason", result.getStopReason());
        output.put("status", result.getStatus());
        if (result.getRawRef() != null && !result.getRawRef().isBlank()) {
            output.put("rawRef", result.getRawRef());
            output.put("modelRawRef", result.getRawRef());
        }
        return StepExecutionOutput.fromPayload(output);
    }
}

package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.reasoning.common.ReasoningInput;
import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.orchestrator.ReasoningExecutionPlan;
import com.example.agent.reasoning.common.orchestrator.ReasoningOrchestrator;
import com.example.agent.reasoning.common.selection.ReasoningDegradePolicy;
import com.example.agent.reasoning.common.selection.ReasoningStrategySelector;
import com.example.agent.reasoning.common.result.DebatePayload;
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 辩论步骤执行器。
 *
 * <p>用途：封装 {@code DEBATE} 步骤执行逻辑，生成稳定输出结构。
 * <p>输入：任务请求、步骤定义与运行上下文。
 * <p>输出：辩论轮次摘要映射。
 * <p>边界：异常由上层统一捕获并按恢复策略处理。
 */
@Component
public class DebateStepExecutor implements StepTypeExecutor {

    private final ReasoningOrchestrator reasoningOrchestrator;
    private final ReasoningInputResolver reasoningInputResolver;
    private final ReasoningStrategySelector reasoningStrategySelector;
    private final ReasoningDegradePolicy reasoningDegradePolicy;

    public DebateStepExecutor(ReasoningOrchestrator reasoningOrchestrator,
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
        return stepType != null && "DEBATE".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        Map<String, Object> stepInput = reasoningInputResolver.resolveExecutionInput(request);
        TaskRequest taskRequest = request != null ? request.getTaskRequest() : null;
        if (request != null && request.getStep() != null) {
            StepInputView stepInputView = StepInputView.from(request.getStep(), request.getRuntimeContext());
            Map<String, Object> inputFromStep = stepInputView.toExecutionMap();
            if (inputFromStep != null && !inputFromStep.isEmpty()) {
                stepInput.putAll(inputFromStep);
            }
        }
        String topic = reasoningInputResolver.resolvePrompt(stepInput, taskRequest, "topic", "question", "prompt", "query");
        ReasoningRequest reasoningRequest = new ReasoningRequest(
                "DEBATE",
                topic,
                stepInput,
                request != null ? request.getTenantContext() : null,
                request != null ? request.getWorkflowId() : null,
                request != null ? request.getSeqCounter() : null
        );
        ReasoningInput reasoningInput = reasoningRequest.getInput();
        String preferredStrategy = reasoningInputResolver.resolvePreferredStrategy(reasoningInput, "debate");
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
        DebatePayload payload = result.getPayload() instanceof DebatePayload typedPayload
                ? typedPayload
                : new DebatePayload(null, topic, result.getSummary());
        Map<String, Object> output = new HashMap<>();
        output.put("roundId", payload.getRoundId());
        output.put("topic", payload.getTopic());
        output.put("conclusion", result.getSummary());
        if (result.getRawRef() != null && !result.getRawRef().isBlank()) {
            output.put("rawRef", result.getRawRef());
            output.put("modelRawRef", result.getRawRef());
        }
        return StepExecutionOutput.fromPayload(output);
    }
}

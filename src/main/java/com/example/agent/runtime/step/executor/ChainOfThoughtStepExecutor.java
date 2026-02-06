package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.reasoning.cot.ChainOfThoughtResult;
import com.example.agent.reasoning.cot.ChainOfThoughtService;
import com.example.agent.runtime.step.StepExecutionRequest;
import com.example.agent.runtime.step.StepExecutionOutput;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 链式推理步骤执行器。
 *
 * <p>用途：封装 {@code CHAIN_OF_THOUGHT}/{@code COT} 步骤执行逻辑，输出结构化摘要以避免暴露推理细节。
 * <p>输入：任务请求、步骤输入与链路上下文。
 * <p>输出：链式推理输出映射。
 * <p>边界：异常由上层捕获并按恢复策略处理。
 */
@Component
public class ChainOfThoughtStepExecutor implements StepTypeExecutor {

    private final ChainOfThoughtService chainOfThoughtService;

    public ChainOfThoughtStepExecutor(ChainOfThoughtService chainOfThoughtService) {
        this.chainOfThoughtService = chainOfThoughtService;
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
        TaskRequest taskRequest = request.getTaskRequest();
        Map<String, Object> stepInput = request.getStepInput();
        String question = resolveStepQuestion(stepInput, taskRequest);
        ChainOfThoughtResult result = chainOfThoughtService.run(
                question,
                stepInput,
                request.getTenantContext(),
                request.getWorkflowId(),
                request.getSeqCounter()
        );
        Map<String, Object> output = new HashMap<>();
        output.put("finalAnswer", result.getFinalAnswer());
        output.put("stepsCount", result.getStepsCount());
        output.put("confidence", result.getConfidence());
        output.put("stopReason", result.getStopReason());
        output.put("status", result.isCompleted() ? "COMPLETED" : "STOPPED");
        if (result.getRawRef() != null && !result.getRawRef().isBlank()) {
            output.put("rawRef", result.getRawRef());
            output.put("modelRawRef", result.getRawRef());
        }
        return StepExecutionOutput.fromPayload(output);
    }

    private String resolveStepQuestion(Map<String, Object> stepInput, TaskRequest request) {
        if (stepInput != null) {
            Object question = stepInput.get("question");
            if (question instanceof String value && !value.isBlank()) {
                return value;
            }
            Object topic = stepInput.get("topic");
            if (topic instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return resolveStepQuery(request, stepInput);
    }

    private String resolveStepQuery(TaskRequest request, Map<String, Object> stepInput) {
        if (stepInput != null) {
            Object query = stepInput.get("query");
            if (query instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        if (request != null && request.getQuery() != null) {
            return request.getQuery();
        }
        return "";
    }
}

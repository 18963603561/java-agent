package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.reasoning.debate.DebateCoordinator;
import com.example.agent.reasoning.debate.DebateRound;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.HashMap;
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

    private final DebateCoordinator debateCoordinator;

    public DebateStepExecutor(DebateCoordinator debateCoordinator) {
        this.debateCoordinator = debateCoordinator;
    }

    @Override
    public boolean supports(String stepType) {
        return stepType != null && "DEBATE".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        String topic = resolveStepTopic(request.getTaskRequest(), request.getStep());
        DebateRound round = debateCoordinator.debate(topic, request.getTenantContext(), request.getWorkflowId(), request.getSeqCounter());
        Map<String, Object> output = new HashMap<>();
        output.put("roundId", round.getRoundId());
        output.put("topic", round.getTopic());
        output.put("conclusion", round.getConclusion());
        if (round.getRawRef() != null && !round.getRawRef().isBlank()) {
            output.put("rawRef", round.getRawRef());
            output.put("modelRawRef", round.getRawRef());
        }
        return StepExecutionOutput.fromPayload(output);
    }

    private String resolveStepTopic(TaskRequest request, com.example.agent.runtime.model.StepSpec step) {
        Map<String, Object> stepInput = resolveStepInput(step);
        if (stepInput != null) {
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

    private Map<String, Object> resolveStepInput(com.example.agent.runtime.model.StepSpec step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toExecutionInput();
        return input == null || input.isEmpty() ? null : input;
    }
}


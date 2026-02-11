package com.example.agent.runtime.step.executor;

import com.example.agent.orchestration.multiagent.MultiAgentCoordinator;
import com.example.agent.orchestration.multiagent.model.MultiAgentExecutionResult;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 多智能体步骤执行器。
 *
 * <p>用途：封装 {@code MULTI_AGENT} 步骤执行逻辑，保持编排层简洁。
 * <p>输入：步骤执行请求对象。
 * <p>输出：多智能体协作结果映射。
 * <p>边界：异常由上层统一捕获并按恢复策略处理。
 */
@Component
public class MultiAgentStepExecutor implements StepTypeExecutor {

    private final MultiAgentCoordinator multiAgentCoordinator;

    public MultiAgentStepExecutor(MultiAgentCoordinator multiAgentCoordinator) {
        this.multiAgentCoordinator = multiAgentCoordinator;
    }

    @Override
    public boolean supports(String stepType) {
        return stepType != null && "MULTI_AGENT".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        MultiAgentExecutionResult result = multiAgentCoordinator.coordinateResult(
                request.getStep(),
                request.getTenantContext(),
                request.getWorkflowId(),
                request.getSeqCounter()
        );
        Map<String, Object> output = result.toMap();
        return StepExecutionOutput.fromPayload(output);
    }
}

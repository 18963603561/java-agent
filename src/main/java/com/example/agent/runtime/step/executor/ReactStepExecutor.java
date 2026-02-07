package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.react.ReactLoopResult;
import com.example.agent.runtime.react.ReactLoopService;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code ReAct} 步骤执行器。
 *
 * <p>用途：封装 {@code REACT} 步骤执行逻辑，输出稳定摘要结构。
 * <p>输入：任务请求、步骤输入与运行上下文。
 * <p>输出：{@code ReAct} 执行结果映射。
 * <p>边界：异常由上层统一捕获并按恢复策略处理。
 */
@Component
public class ReactStepExecutor implements StepTypeExecutor {

    private final ReactLoopService reactLoopService;

    public ReactStepExecutor(ReactLoopService reactLoopService) {
        this.reactLoopService = reactLoopService;
    }

    @Override
    public boolean supports(String stepType) {
        return stepType != null && "REACT".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        TaskRequest taskRequest = request != null ? request.getTaskRequest() : null;
        Map<String, Object> stepInput = request != null ? request.getStepInput() : null;
        TaskRequest reactRequest = buildRequestWithContext(taskRequest, stepInput);
        ReactLoopResult result = reactLoopService.run(
                reactRequest,
                request.getTenantContext(),
                request.getWorkflowId(),
                request.getTaskId(),
                request.getSeqCounter()
        );

        Map<String, Object> output = new HashMap<>();
        output.put("iterations", result.getIterations());
        output.put("completed", result.isCompleted());
        output.put("stopReason", result.getStopReason());
        output.put("finalAnswer", result.getFinalAnswer());
        output.put("observations", result.getObservations());
        output.put("status", result.isCompleted() ? "COMPLETED" : "UNRESOLVED");
        if (result.getRawRef() != null && !result.getRawRef().isBlank()) {
            output.put("rawRef", result.getRawRef());
            output.put("modelRawRef", result.getRawRef());
        }
        return StepExecutionOutput.fromPayload(output);
    }

    /**
     * 构造携带运行上下文的任务请求副本，避免修改原请求对象。
     */
    private TaskRequest buildRequestWithContext(TaskRequest request, Map<String, Object> runtimeContext) {
        if (request == null) {
            return null;
        }
        TaskRequest copy = new TaskRequest();
        copy.setQuery(request.getQuery());
        copy.setSessionId(request.getSessionId());
        copy.setSkillName(request.getSkillName());
        copy.setIdempotencyKey(request.getIdempotencyKey());
        copy.setToolChoice(request.getToolChoice());
        copy.setContext(runtimeContext);
        return copy;
    }
}


package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.llm.LlmStepService;
import com.example.agent.runtime.step.StepExecutionRequest;
import com.example.agent.runtime.step.StepExecutionOutput;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 大模型步骤执行器。
 *
 * <p>用途：统一封装 LLM/ANSWER 步骤执行入口，保持输出结构与日志一致。
 * <p>输入：任务请求、步骤输入与链路上下文。
 * <p>输出：大模型输出映射；输出为空时回退为 {@code no_response}。
 * <p>边界：异常由上层捕获并按恢复策略处理。
 */
@Component
public class LlmStepExecutor implements StepTypeExecutor {

    private static final Logger log = LoggerFactory.getLogger(LlmStepExecutor.class);

    private final LlmStepService llmStepService;

    public LlmStepExecutor(LlmStepService llmStepService) {
        this.llmStepService = llmStepService;
    }

    @Override
    public boolean supports(String stepType) {
        if (stepType == null) {
            return false;
        }
        return "LLM".equalsIgnoreCase(stepType) || "ANSWER".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        TaskRequest taskRequest = request != null ? request.getTaskRequest() : null;
        Map<String, Object> stepInput = request != null ? request.getStepInput() : null;
        String workflowId = request != null ? request.getWorkflowId() : null;
        String taskId = request != null ? request.getTaskId() : null;

        String query = taskRequest != null ? taskRequest.getQuery() : null;
        int queryLength = query != null ? query.length() : 0;
        boolean hasContext = stepInput != null && stepInput.get("context") != null;
        LlmStepService.ToolSummaryMode summaryMode = llmStepService.resolveToolSummaryMode(taskRequest, stepInput);
        // 记录步骤入口信息，便于排查上下文缺失问题
        log.info("LLM 步骤开始, workflowId={}, queryLength={}, hasContext={}, summaryMode={}",
                workflowId, queryLength, hasContext, summaryMode);

        Map<String, Object> output = llmStepService.run(taskRequest, stepInput, request.getTenantContext(),
                workflowId, taskId, request.getSeqCounter(), summaryMode);
        if (output == null || output.isEmpty()) {
            // 模型输出为空时的兜底处理
            output = new HashMap<>();
            output.put("answer", "no_response");
            output.put("source", "llm_step");
        }
        log.info("LLM 步骤完成, workflowId={}, outputKeys={}", workflowId, output.keySet());
        return StepExecutionOutput.fromPayload(output);
    }
}

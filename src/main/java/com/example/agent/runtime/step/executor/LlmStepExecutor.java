package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.llm.LlmStepService;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM 步骤执行器。
 *
 * <p>用途：处理 {@code LLM}/{@code ANSWER} 类型步骤并调用 LLM 服务产出结果。
 * <p>输入：任务请求、步骤输入、租户上下文与链路信息。
 * <p>输出：步骤执行输出对象。
 * <p>边界：当 LLM 无输出时返回兜底响应，避免主流程中断。
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
        log.info("LLM 步骤开始, workflowId={}, queryLength={}, hasContext={}, summaryMode={}",
                workflowId, queryLength, hasContext, summaryMode);

        Map<String, Object> output = llmStepService.run(taskRequest, stepInput, request.getTenantContext(),
                workflowId, taskId, request.getSeqCounter(), summaryMode);
        if (output == null || output.isEmpty()) {
            output = new HashMap<>();
            output.put("answer", "no_response");
            output.put("source", "llm_step");
        }
        log.info("LLM 步骤完成, workflowId={}, outputKeys={}", workflowId, output.keySet());
        return StepExecutionOutput.fromPayload(output);
    }
}


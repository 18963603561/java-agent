package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.EvidencePack;
import com.example.agent.capabilities.context.EvidencePackService;
import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.example.agent.capabilities.tools.validation.ToolArgumentValidatorRuntime;
import com.example.agent.runtime.control.RuntimeExecutionGate;
import com.example.agent.runtime.llm.LlmStepService;
import com.example.agent.runtime.step.StepExecutionOutput;
import com.example.agent.runtime.step.StepExecutionRequest;
import com.example.agent.runtime.step.StepRecord;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 工具步骤执行器。
 *
 * <p>用途：封装 {@code TOOL} 步骤执行逻辑，包含 toolChoice 补齐与直达工具路径。
 * <p>输入：任务请求、步骤输入与链路上下文。
 * <p>输出：工具执行结果映射（可能为直达工具输出或 LLM 工具调用输出）。
 * <p>边界：直达工具执行失败会回退为 LLM 决策路径。
 */
@Component
public class ToolStepExecutor implements StepTypeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolStepExecutor.class);

    /**
     * 工具参数中需要过滤的保留字段。
     *
     * <p>用途：避免将步骤控制字段误传给工具参数。</p>
     */
    private static final Set<String> TOOL_ARGUMENT_RESERVED_KEYS = Set.of(
            "tool",
            "toolName",
            "context",
            "query",
            "dependsOn",
            "requiresApproval",
            "approvalSource",
            "fallbackTool",
            "evidencePack",
            "EvidencePack",
            "_internalEvidencePack",
            "arguments"
    );

    private final LlmStepExecutor llmStepExecutor;
    private final LlmStepService llmStepService;
    private final EnforcementGateway enforcementGateway;
    private final HookManager hookManager;
    private final RuntimeExecutionGate runtimeExecutionGate;
    private final ToolArgumentValidatorRuntime toolArgumentValidator;
    private final EvidencePackService evidencePackService;
    private final boolean directToolEnabled;

    public ToolStepExecutor(LlmStepExecutor llmStepExecutor,
                            LlmStepService llmStepService,
                            EnforcementGateway enforcementGateway,
                            HookManager hookManager,
                            RuntimeExecutionGate runtimeExecutionGate,
                            ToolArgumentValidatorRuntime toolArgumentValidator,
                            EvidencePackService evidencePackService,
                            @Value("${agent.runtime.direct-tool.enabled:true}") boolean directToolEnabled) {
        this.llmStepExecutor = llmStepExecutor;
        this.llmStepService = llmStepService;
        this.enforcementGateway = enforcementGateway;
        this.hookManager = hookManager;
        this.runtimeExecutionGate = runtimeExecutionGate;
        this.toolArgumentValidator = toolArgumentValidator;
        this.evidencePackService = evidencePackService;
        this.directToolEnabled = directToolEnabled;
    }

    @Override
    public boolean supports(String stepType) {
        return stepType != null && "TOOL".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        applyToolChoiceForToolStep(
                request.getStep(),
                request.getTaskRequest(),
                request.getStepInput(),
                request.getWorkflowId(),
                request.getRecord()
        );
        StepExecutionOutput directOutput = tryExecuteDirectToolStep(request);
        if (directOutput != null) {
            return directOutput;
        }
        return llmStepExecutor.execute(request);
    }

    /**
     * 工具执行入口（供恢复兜底路径复用）。
     */
    public StepExecutionOutput executeTool(StepExecutionRequest request,
                                           String toolName,
                                           Map<String, Object> toolArguments) {
        Map<String, Object> output = executeToolInternal(request, toolName, toolArguments);
        return StepExecutionOutput.fromPayload(output, toolName);
    }

    /**
     * 解析步骤需要使用的工具名称。
     *
     * <p>用途：用于兜底输出补充 fallbackFrom 字段。</p>
     */
    public String resolveToolName(TaskRequest request, com.example.agent.runtime.model.StepSpec step) {
        Map<String, Object> stepInput = resolveStepInput(step);
        if (stepInput != null) {
            Object tool = stepInput.get("tool");
            if (tool instanceof String toolName && !toolName.isBlank()) {
                return toolName;
            }
            Object toolName = stepInput.get("toolName");
            if (toolName instanceof String name && !name.isBlank()) {
                return name;
            }
        }
        if (request != null && request.getContext() != null) {
            Object tool = request.getContext().get("tool");
            if (tool instanceof String toolName && !toolName.isBlank()) {
                return toolName;
            }
        }
        return null;
    }

    private StepExecutionOutput tryExecuteDirectToolStep(StepExecutionRequest request) {
        if (!directToolEnabled) {
            return null;
        }
        TaskRequest taskRequest = request.getTaskRequest();
        Map<String, Object> stepInput = request.getStepInput();
        String toolName = resolveToolNameForToolStep(taskRequest, request.getStep(), stepInput);
        if (toolName == null || toolName.isBlank()) {
            log.info("直达工具跳过, toolName 缺失, workflowId={}, stepId={}",
                    request.getWorkflowId(),
                    request.getRecord() != null ? request.getRecord().getStepId() : null);
            return null;
        }
        Map<String, Object> toolArguments = resolveDirectToolArguments(stepInput);
        if (toolArguments == null || toolArguments.isEmpty()) {
            log.info("直达工具跳过, 参数缺失, workflowId={}, stepId={}, tool={}",
                    request.getWorkflowId(),
                    request.getRecord() != null ? request.getRecord().getStepId() : null,
                    toolName);
            return null;
        }
        if (toolArgumentValidator != null) {
            ToolArgumentValidatorRuntime.ValidationResult validation = toolArgumentValidator.validate(toolName, toolArguments);
            if (!validation.isValid()) {
                log.info("直达工具跳过, 参数校验失败, workflowId={}, stepId={}, tool={}, reason={}, missing={}",
                        request.getWorkflowId(),
                        request.getRecord() != null ? request.getRecord().getStepId() : null,
                        toolName,
                        validation.getReason(),
                        validation.getMissingFields());
                return null;
            }
        }
        try {
            log.info("直达工具执行, workflowId={}, stepId={}, tool={}, argKeys={}",
                    request.getWorkflowId(),
                    request.getRecord() != null ? request.getRecord().getStepId() : null,
                    toolName,
                    toolArguments.keySet());
            Map<String, Object> toolResult = executeToolInternal(request, toolName, toolArguments);
            LlmStepService.ToolSummaryMode summaryMode = llmStepService.resolveToolSummaryMode(taskRequest, stepInput);
            if (summaryMode != LlmStepService.ToolSummaryMode.LLM_SUMMARY) {
                Map<String, Object> output = llmStepService.buildDirectToolOutput(summaryMode, toolName,
                        toolArguments, toolResult);
                log.info("直达工具执行完成, workflowId={}, stepId={}, summaryMode={}, outputKeys={}",
                        request.getWorkflowId(),
                        request.getRecord() != null ? request.getRecord().getStepId() : null,
                        summaryMode,
                        output.keySet());
                return StepExecutionOutput.fromPayload(output, toolName);
            }
            Map<String, Object> summaryOutput = llmStepService.summarizeDirectToolResult(
                    taskRequest,
                    stepInput,
                    request.getTenantContext(),
                    request.getWorkflowId(),
                    request.getSeqCounter(),
                    toolName,
                    toolArguments,
                    toolResult
            );
            log.info("直达工具总结完成, workflowId={}, stepId={}, outputKeys={}",
                    request.getWorkflowId(),
                    request.getRecord() != null ? request.getRecord().getStepId() : null,
                    summaryOutput.keySet());
            return StepExecutionOutput.fromPayload(summaryOutput, toolName);
        } catch (Throwable ex) {
            log.warn("直达工具执行失败, 回退 LLM 决策, workflowId={}, stepId={}, tool={}",
                    request.getWorkflowId(),
                    request.getRecord() != null ? request.getRecord().getStepId() : null,
                    toolName,
                    ex);
            return null;
        }
    }

    /**
     * TOOL 步骤补齐 toolChoice，保证规划工具能被模型决策流程识别。
     */
    private void applyToolChoiceForToolStep(com.example.agent.runtime.model.StepSpec step,
                                            TaskRequest request,
                                            Map<String, Object> stepInput,
                                            String workflowId,
                                            StepRecord record) {
        if (stepInput == null || hasToolChoice(stepInput)) {
            return;
        }
        String toolName = resolveToolNameForToolStep(request, step, stepInput);
        if (toolName == null || toolName.isBlank()) {
            log.warn("TOOL 步骤缺少 toolName, 无法补齐 toolChoice, workflowId={}, stepId={}",
                    workflowId, record != null ? record.getStepId() : null);
            return;
        }
        Map<String, Object> toolChoice = new HashMap<>();
        toolChoice.put("mode", "specified");
        toolChoice.put("toolName", toolName);
        stepInput.put("toolChoice", toolChoice);
        log.info("TOOL 步骤补齐 toolChoice, workflowId={}, stepId={}, tool={}",
                workflowId, record != null ? record.getStepId() : null, toolName);
    }

    private boolean hasToolChoice(Map<String, Object> stepInput) {
        if (stepInput == null) {
            return false;
        }
        if (stepInput.get("toolChoice") != null) {
            return true;
        }
        Object context = stepInput.get("context");
        if (context instanceof Map<?, ?> contextMap) {
            return contextMap.get("toolChoice") != null;
        }
        return false;
    }

    private String resolveToolNameForToolStep(TaskRequest request,
                                              com.example.agent.runtime.model.StepSpec step,
                                              Map<String, Object> stepInput) {
        String toolName = resolveToolName(request, step);
        if (toolName != null && !toolName.isBlank()) {
            return toolName;
        }
        if (stepInput == null) {
            return null;
        }
        Object tool = stepInput.get("tool");
        if (tool != null && !tool.toString().isBlank()) {
            return tool.toString();
        }
        Object toolNameObj = stepInput.get("toolName");
        if (toolNameObj != null && !toolNameObj.toString().isBlank()) {
            return toolNameObj.toString();
        }
        return null;
    }

    private Map<String, Object> resolveDirectToolArguments(Map<String, Object> stepInput) {
        if (stepInput == null) {
            return null;
        }
        Object raw = stepInput.get("arguments");
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> arguments = new HashMap<>();
        rawMap.forEach((key, value) -> arguments.put(String.valueOf(key), value));
        filterReservedToolArguments(arguments);
        return arguments;
    }

    private void filterReservedToolArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        for (String key : TOOL_ARGUMENT_RESERVED_KEYS) {
            arguments.remove(key);
        }
    }

    /**
     * 执行工具步骤（支持外部传入参数覆盖）。
     */
    private Map<String, Object> executeToolInternal(StepExecutionRequest request,
                                                    String toolName,
                                                    Map<String, Object> toolArguments) {
        // 工具执行前先检查执行控制状态。
        runtimeExecutionGate.apply(request.getWorkflowId(),
                request.getTenantContext(),
                request.getSeqCounter(),
                request.getEventPublisher());
        // 执行工具前置钩子。
        if (hookManager != null) {
            hookManager.preTool(request.getTenantContext(), request.getRecord(), toolName);
        }
        Map<String, Object> output;
        if (toolArguments == null || toolArguments.isEmpty()) {
            output = enforcementGateway.execute(
                    request.getTaskRequest(),
                    request.getTenantContext(),
                    request.getWorkflowId(),
                    request.getTaskId(),
                    request.getSeqCounter(),
                    toolName
            );
        } else {
            output = enforcementGateway.executeWithArguments(
                    request.getTaskRequest(),
                    request.getTenantContext(),
                    request.getWorkflowId(),
                    request.getTaskId(),
                    request.getSeqCounter(),
                    toolName,
                    toolArguments
            );
        }
        // 执行工具后置钩子。
        if (hookManager != null) {
            hookManager.postTool(request.getTenantContext(), request.getRecord(), toolName, output);
        }
        if (request.getTaskRequest() != null && request.getTaskRequest().getContext() != null) {
            syncEvidencePackFromStore(request.getTaskRequest().getContext(),
                    request.getTenantContext(),
                    request.getWorkflowId());
        }
        return output;
    }

    /**
     * 从证据包服务同步当前工作流证据到运行上下文。
     */
    private void syncEvidencePackFromStore(Map<String, Object> runtimeContext,
                                           com.example.agent.security.auth.TenantContext tenantContext,
                                           String workflowId) {
        if (runtimeContext == null || evidencePackService == null || tenantContext == null
                || workflowId == null || workflowId.isBlank()) {
            return;
        }
        EvidencePack pack = evidencePackService.getPack(tenantContext.getTenantId(), workflowId);
        if (pack != null) {
            runtimeContext.put("evidencePack", pack);
        }
    }

    private Map<String, Object> resolveStepInput(com.example.agent.runtime.model.StepSpec step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toExecutionInput();
        return input == null || input.isEmpty() ? null : input;
    }
}

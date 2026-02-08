package com.example.agent.runtime.llm;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.provider.ModelRequest;
import com.example.agent.capabilities.llm.provider.ModelResponse;
import com.example.agent.capabilities.llm.provider.ModelScene;
import com.example.agent.capabilities.llm.tooling.ModelToolDefinition;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.security.auth.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * LLM 步骤执行服务。
 *
 * <p>用途：负责 LLM 步骤主流程编排，协调决策、工具调用、结果总结与输出组装。
 * <p>输入：任务请求、步骤输入、租户上下文与链路信息。
 * <p>输出：标准化输出映射。
 * <p>边界：当模型输出不可解析或工具调用失败时，返回可恢复的兜底输出，不中断主流程。
 */
@Service
public class LlmStepService {

    private static final Logger log = LoggerFactory.getLogger(LlmStepService.class);

    /**
     * 工具结果摘要模式。
     */
    public enum ToolSummaryMode {
        /**
         * 返回原始结果。
         */
        RAW,
        /**
         * 模板化摘要。
         */
        TEMPLATE,
        /**
         * 模型二次摘要。
         */
        LLM_SUMMARY
    }

    private final ModelInvocationService modelInvocationService;
    private final ModelToolResolver modelToolResolver;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final LlmDecisionService llmDecisionService;
    private final ToolCallOrchestrator toolCallOrchestrator;
    private final ToolSummaryService toolSummaryService;
    private final ToolOutputAssembler toolOutputAssembler;

    public LlmStepService(ModelInvocationService modelInvocationService,
                          ModelToolResolver modelToolResolver,
                          ToolRegistry toolRegistry,
                          ObjectMapper objectMapper,
                          LlmDecisionService llmDecisionService,
                          ToolCallOrchestrator toolCallOrchestrator,
                          ToolSummaryService toolSummaryService,
                          ToolOutputAssembler toolOutputAssembler) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.llmDecisionService = llmDecisionService;
        this.toolCallOrchestrator = toolCallOrchestrator;
        this.toolSummaryService = toolSummaryService;
        this.toolOutputAssembler = toolOutputAssembler;
    }

    /**
     * 解析工具摘要模式，优先级：步骤输入 > 步骤上下文 > 请求上下文。
     *
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @return 摘要模式
     */
    public ToolSummaryMode resolveToolSummaryMode(TaskRequest request, Map<String, Object> stepInput) {
        ToolSummaryMode mode = resolveToolSummaryModeFromStepInput(stepInput);
        if (mode != null) {
            return mode;
        }
        mode = resolveToolSummaryModeFromContext(stepInput != null ? stepInput.get("context") : null);
        if (mode != null) {
            return mode;
        }
        mode = resolveToolSummaryModeFromContext(request != null ? request.getContext() : null);
        return mode != null ? mode : ToolSummaryMode.LLM_SUMMARY;
    }

    /**
     * 构建直达工具输出。
     *
     * @param summaryMode 摘要模式
     * @param toolName 工具名
     * @param toolArguments 工具参数
     * @param toolResult 工具结果
     * @return 输出
     */
    public Map<String, Object> buildDirectToolOutput(ToolSummaryMode summaryMode,
                                                      String toolName,
                                                      Map<String, Object> toolArguments,
                                                      Map<String, Object> toolResult) {
        ToolSummaryMode resolvedMode = summaryMode == null || summaryMode == ToolSummaryMode.LLM_SUMMARY
                ? ToolSummaryMode.RAW
                : summaryMode;
        ToolCallOrchestrator.ToolCallOutcome toolCallResult = ToolCallOrchestrator.ToolCallOutcome.success(
                toolResult == null ? Map.of() : toolResult
        );
        String source = resolvedMode == ToolSummaryMode.TEMPLATE ? "direct_tool_template" : "direct_tool_raw";
        return toolOutputAssembler.buildToolOutput(
                resolvedMode,
                toolName,
                toolArguments,
                toolCallResult,
                source,
                toolSummaryService
        );
    }

    /**
     * 执行 LLM 步骤。
     *
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 序列计数器
     * @return 输出
     */
    public Map<String, Object> run(TaskRequest request,
                                   Map<String, Object> stepInput,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   String taskId,
                                   AtomicLong seqCounter) {
        ToolSummaryMode summaryMode = resolveToolSummaryMode(request, stepInput);
        return run(request, stepInput, tenantContext, workflowId, taskId, seqCounter, summaryMode);
    }

    /**
     * 执行 LLM 步骤（显式摘要模式）。
     *
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 序列计数器
     * @param summaryMode 摘要模式
     * @return 输出
     */
    public Map<String, Object> run(TaskRequest request,
                                   Map<String, Object> stepInput,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   String taskId,
                                   AtomicLong seqCounter,
                                   ToolSummaryMode summaryMode) {
        String query = resolveStepQuestion(stepInput, request);
        int queryLength = query != null ? query.length() : 0;
        boolean hasContext = stepInput != null && stepInput.get("context") != null;
        ToolSummaryMode resolvedSummaryMode = summaryMode != null ? summaryMode : resolveToolSummaryMode(request, stepInput);
        log.info("LLM 步骤开始, workflowId={}, queryLength={}, hasContext={}, summaryMode={}",
                workflowId, queryLength, hasContext, resolvedSummaryMode);

        ModelRequest decisionRequest = new ModelRequest();
        decisionRequest.setScene(ModelScene.LLM_STEP);
        modelToolResolver.applyTooling(decisionRequest, request, stepInput);

        Map<String, Object> contextPayload = llmDecisionService.buildDecisionContext(
                query,
                decisionRequest,
                request,
                stepInput,
                tenantContext,
                isToolsDisabled(request, stepInput)
        );
        String decisionPrompt = llmDecisionService.buildDecisionPrompt(llmDecisionService.toJson(contextPayload));
        decisionRequest.setPrompt(decisionPrompt);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("promptScene", "llm_step_decision");
        metadata.put("ts", Instant.now().toString());
        ModelResponse decisionResponse = modelInvocationService.invoke(
                decisionRequest,
                ModelScene.LLM_STEP,
                tenantContext,
                workflowId,
                seqCounter,
                "llm_step_decision",
                metadata
        );

        String rawDecision = decisionResponse != null ? decisionResponse.getContent() : null;
        String decisionRawRef = decisionResponse != null ? decisionResponse.getRawRef() : null;
        Map<String, Object> decision = llmDecisionService.parseJsonMap(rawDecision);
        String mode = normalizeMode(llmDecisionService.readString(decision, "mode"));
        Map<String, Object> toolMap = llmDecisionService.readMap(decision.get("tool"));
        String toolName = llmDecisionService.readString(toolMap, "name");
        log.info("LLM 步骤决策完成, workflowId={}, mode={}, toolName={}", workflowId, mode, toolName);

        if (!ToolOutputAssembler.MODE_TOOL_CALL.equals(mode)) {
            Map<String, Object> answerOutput = toolOutputAssembler.buildAnswerOutput(
                    decision,
                    rawDecision,
                    "llm_step",
                    decisionRawRef,
                    llmDecisionService
            );
            log.info("LLM 步骤完成, workflowId={}, outputKeys={}", workflowId, answerOutput.keySet());
            return answerOutput;
        }

        Map<String, Object> toolArguments = llmDecisionService.readMap(toolMap.get("arguments"));
        if (!StringUtils.hasText(toolName)) {
            Map<String, Object> failure = toolOutputAssembler.buildToolFailureOutput(
                    decision,
                    "工具名称为空",
                    ToolCallOrchestrator.TOOL_INVALID_REQUEST,
                    "llm_step",
                    decisionRawRef
            );
            log.info("LLM 步骤工具调用失败, workflowId={}, reason={}", workflowId, "工具名称为空");
            return failure;
        }
        if (!isToolAvailable(toolName, decisionRequest.getTools())) {
            Map<String, Object> failure = toolOutputAssembler.buildToolFailureOutput(
                    decision,
                    "工具不在可用列表中",
                    ToolCallOrchestrator.TOOL_NOT_FOUND,
                    "llm_step",
                    decisionRawRef
            );
            log.info("LLM 步骤工具调用失败, workflowId={}, reason={}", workflowId, "工具不在可用列表中");
            return failure;
        }

        ToolCallOrchestrator.ToolCallOutcome toolResult = toolCallOrchestrator.executeToolCall(
                request,
                tenantContext,
                workflowId,
                taskId,
                seqCounter,
                toolName,
                toolArguments
        );
        updateToolContext(request, stepInput, toolName, toolResult);

        if (resolvedSummaryMode != ToolSummaryMode.LLM_SUMMARY) {
            Map<String, Object> output = toolOutputAssembler.buildToolOutput(
                    resolvedSummaryMode,
                    toolName,
                    toolArguments,
                    toolResult,
                    "llm_step",
                    toolSummaryService
            );
            output.putIfAbsent(OutputKeys.DECISION_RAW_REF, decisionRawRef);
            toolOutputAssembler.mergeRef(output, OutputKeys.DECISION_RAW_REF, decisionRawRef);
            log.info("LLM 步骤工具调用完成, workflowId={}, summaryMode={}, toolStatus={}, outputKeys={}",
                    workflowId, resolvedSummaryMode, toolResult.getStatus(), output.keySet());
            return output;
        }

        ToolSummaryService.ToolSummaryOutput summaryOutput = toolSummaryService.summarizeToolResult(
                query,
                decision,
                toolResult,
                toolName,
                toolArguments,
                request,
                stepInput,
                tenantContext,
                workflowId,
                seqCounter
        );
        Map<String, Object> output = summaryOutput.getOutput();
        toolOutputAssembler.applyToolOutputDefaults(
                output,
                toolName,
                toolArguments,
                toolResult,
                "llm_step",
                decisionRawRef,
                summaryOutput.getSummaryRawRef(),
                toolSummaryService
        );
        log.info("LLM 步骤完成, workflowId={}, toolStatus={}, outputKeys={}",
                workflowId, toolResult.getStatus(), output.keySet());
        return output;
    }

    /**
     * 直达工具路径总结。
     *
     * @param request 请求
     * @param stepInput 步骤输入
     * @param tenantContext 租户上下文
     * @param workflowId 工作流
     * @param seqCounter 序列
     * @param toolName 工具名
     * @param toolArguments 参数
     * @param toolResult 结果
     * @return 输出
     */
    public Map<String, Object> summarizeDirectToolResult(TaskRequest request,
                                                         Map<String, Object> stepInput,
                                                         TenantContext tenantContext,
                                                         String workflowId,
                                                         AtomicLong seqCounter,
                                                         String toolName,
                                                         Map<String, Object> toolArguments,
                                                         Map<String, Object> toolResult) {
        String query = resolveStepQuestion(stepInput, request);
        ToolCallOrchestrator.ToolCallOutcome toolCallResult = ToolCallOrchestrator.ToolCallOutcome.success(
                toolResult == null ? Map.of() : toolResult
        );
        updateToolContext(request, stepInput, toolName, toolCallResult);

        ToolSummaryService.ToolSummaryOutput summaryOutput = toolSummaryService.summarizeToolResult(
                query,
                null,
                toolCallResult,
                toolName,
                toolArguments,
                request,
                stepInput,
                tenantContext,
                workflowId,
                seqCounter
        );
        Map<String, Object> output = summaryOutput.getOutput();
        toolOutputAssembler.applyToolOutputDefaults(
                output,
                toolName,
                toolArguments,
                toolCallResult,
                "direct_tool",
                null,
                summaryOutput.getSummaryRawRef(),
                toolSummaryService
        );
        log.info("直达工具总结完成, workflowId={}, tool={}, outputKeys={}",
                workflowId, toolName, output.keySet());
        return output;
    }

    private ToolSummaryMode resolveToolSummaryModeFromStepInput(Map<String, Object> stepInput) {
        if (stepInput == null) {
            return null;
        }
        if (isTruthy(stepInput.get("rawOnly"))) {
            return ToolSummaryMode.RAW;
        }
        ToolSummaryMode mode = parseToolSummaryMode(stepInput.get("summaryMode"));
        if (mode != null) {
            return mode;
        }
        return null;
    }

    private ToolSummaryMode resolveToolSummaryModeFromContext(Object context) {
        if (!(context instanceof Map<?, ?> contextMap)) {
            return null;
        }
        if (isTruthy(contextMap.get("rawOnly"))) {
            return ToolSummaryMode.RAW;
        }
        return parseToolSummaryMode(contextMap.get("toolSummaryMode"));
    }

    private ToolSummaryMode parseToolSummaryMode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ToolSummaryMode mode) {
            return mode;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "raw", "raw_only", "rawonly" -> ToolSummaryMode.RAW;
                case "template", "tpl" -> ToolSummaryMode.TEMPLATE;
                case "llm", "llm_summary", "summary", "model" -> ToolSummaryMode.LLM_SUMMARY;
                default -> null;
            };
        }
        return null;
    }

    private String resolveStepQuestion(Map<String, Object> stepInput, TaskRequest request) {
        if (stepInput != null) {
            Object question = stepInput.get("question");
            if (question instanceof String value && StringUtils.hasText(value)) {
                return value;
            }
            Object topic = stepInput.get("topic");
            if (topic instanceof String value && StringUtils.hasText(value)) {
                return value;
            }
        }
        return request != null ? request.getQuery() : null;
    }

    private boolean isToolsDisabled(TaskRequest request, Map<String, Object> stepInput) {
        if (stepInput != null) {
            Object disable = stepInput.get("disableTools");
            if (isTruthy(disable)) {
                return true;
            }
            Object context = stepInput.get("context");
            if (context instanceof Map<?, ?> contextMap && isTruthy(contextMap.get("disableTools"))) {
                return true;
            }
        }
        if (request != null && request.getContext() != null) {
            return isTruthy(request.getContext().get("disableTools"));
        }
        return false;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return ToolOutputAssembler.MODE_ANSWER;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return ToolOutputAssembler.MODE_TOOL_CALL.equals(normalized)
                ? ToolOutputAssembler.MODE_TOOL_CALL
                : ToolOutputAssembler.MODE_ANSWER;
    }

    private boolean isToolAvailable(String toolName, List<ModelToolDefinition> tools) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        if (tools != null) {
            for (ModelToolDefinition tool : tools) {
                if (tool != null && toolName.equals(tool.getName())) {
                    return true;
                }
            }
        }
        return toolRegistry != null && toolRegistry.getDefinition(toolName) != null;
    }

    private void updateToolContext(TaskRequest request,
                                   Map<String, Object> stepInput,
                                   String toolName,
                                   ToolCallOrchestrator.ToolCallOutcome toolResult) {
        Map<String, Object> errorPayload = buildToolErrorPayload(toolResult);
        if (stepInput != null) {
            putToolContext(stepInput, toolName, toolResult, errorPayload);
            Object context = stepInput.get("context");
            if (context instanceof Map<?, ?> contextMap) {
                Map<String, Object> mutable = toMutableMap(contextMap);
                putToolContext(mutable, toolName, toolResult, errorPayload);
                stepInput.put("context", mutable);
            }
        }
        Map<String, Object> requestContext = ensureMutableContext(request);
        if (requestContext != null) {
            putToolContext(requestContext, toolName, toolResult, errorPayload);
        }
    }

    private Map<String, Object> buildToolErrorPayload(ToolCallOrchestrator.ToolCallOutcome toolResult) {
        if (toolResult == null || toolResult.getErrorCode() == null) {
            return null;
        }
        Map<String, Object> error = new HashMap<>();
        error.put("code", toolResult.getErrorCode());
        if (toolResult.getErrorMessage() != null) {
            error.put("message", toolResult.getErrorMessage());
        }
        return error;
    }

    private void putToolContext(Map<String, Object> target,
                                String toolName,
                                ToolCallOrchestrator.ToolCallOutcome toolResult,
                                Map<String, Object> errorPayload) {
        if (target == null) {
            return;
        }
        target.put("lastToolResult", toolResult != null ? toolResult.getResult() : null);
        if (errorPayload != null) {
            target.put("lastToolError", errorPayload);
        } else {
            target.remove("lastToolError");
        }
        target.put("selectedTools", List.of(toolName));
    }

    private Map<String, Object> ensureMutableContext(TaskRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> context = request.getContext();
        if (context == null) {
            context = new HashMap<>();
            request.setContext(context);
            return context;
        }
        if (context instanceof HashMap) {
            return context;
        }
        Map<String, Object> mutable = new HashMap<>(context);
        request.setContext(mutable);
        return mutable;
    }

    private Map<String, Object> toMutableMap(Map<?, ?> source) {
        Map<String, Object> target = new HashMap<>();
        if (source == null) {
            return target;
        }
        source.forEach((key, value) -> {
            if (key != null) {
                target.put(String.valueOf(key), value);
            }
        });
        return target;
    }
}

package com.example.agent.runtime;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolChoice;
import com.example.agent.model.ModelToolDefinition;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * LLM 步骤执行服务，负责决策提示、工具调用与二次总结输出。
 *
 * <p>功能：基于任务输入与可用工具列表，驱动模型返回 answer 或 tool_call。</p>
 * <p>边界：模型输出解析失败时回退为兜底回答，避免流程中断。</p>
 */
@Service
public class LlmStepService {

    private static final Logger log = LoggerFactory.getLogger(LlmStepService.class);

    private static final String MODE_ANSWER = "answer";
    private static final String MODE_TOOL_CALL = "tool_call";
    private static final String TOOL_STATUS_SUCCESS = "SUCCESS";
    private static final String TOOL_STATUS_FAILED = "FAILED";
    private static final String TOOL_INVALID_REQUEST = "TOOL_INVALID_REQUEST";
    private static final String TOOL_NOT_FOUND = "TOOL_NOT_FOUND";
    private static final String TOOL_TIMEOUT = "TOOL_TIMEOUT";
    private static final String TOOL_RATE_LIMITED = "TOOL_RATE_LIMITED";
    private static final String TOOL_UNAVAILABLE = "TOOL_UNAVAILABLE";
    private static final String TOOL_EXECUTION_FAILED = "TOOL_EXECUTION_FAILED";

    private final ModelInvocationService modelInvocationService;
    private final PromptAssembler promptAssembler;
    private final ModelToolResolver modelToolResolver;
    private final EnforcementGateway enforcementGateway;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 工具调用最大重试次数。
     */
    @Value("${agent.llm-step.tool-retry.max-attempts:2}")
    private int toolMaxAttempts;

    /**
     * 工具调用重试基础延迟（毫秒）。
     */
    @Value("${agent.llm-step.tool-retry.base-delay-ms:100}")
    private long toolBaseDelayMs;

    /**
     * 工具调用重试最大延迟（毫秒）。
     */
    @Value("${agent.llm-step.tool-retry.max-delay-ms:1000}")
    private long toolMaxDelayMs;

    /**
     * 工具调用重试抖动比例。
     */
    @Value("${agent.llm-step.tool-retry.jitter-ratio:0.2}")
    private double toolJitterRatio;

    public LlmStepService(ModelInvocationService modelInvocationService,
                          PromptAssembler promptAssembler,
                          ModelToolResolver modelToolResolver,
                          EnforcementGateway enforcementGateway,
                          ToolRegistry toolRegistry,
                          ObjectMapper objectMapper) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.modelToolResolver = modelToolResolver;
        this.enforcementGateway = enforcementGateway;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 LLM 步骤，支持工具调用与二次总结输出。
     *
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     * @return LLM 步骤输出
     */
    public Map<String, Object> run(TaskRequest request,
                                   Map<String, Object> stepInput,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   String taskId,
                                   AtomicLong seqCounter) {
        String query = resolveStepQuestion(stepInput, request);
        int queryLength = query != null ? query.length() : 0;
        boolean hasContext = stepInput != null && stepInput.get("context") != null;
        // 记录 LLM 步骤入口，便于排查上下文与查询长度
        log.info("LLM 步骤开始, workflowId={}, queryLength={}, hasContext={}",
                workflowId, queryLength, hasContext);

        // 决策阶段：生成工具调用或直接回答的指令
        ModelRequest decisionRequest = new ModelRequest();
        decisionRequest.setScene(ModelScene.LLM_STEP);
        modelToolResolver.applyTooling(decisionRequest, request, stepInput);

        // 构建决策上下文并生成提示词
        Map<String, Object> contextPayload = buildDecisionContext(query, decisionRequest, request,
                stepInput, tenantContext);
        String decisionPrompt = buildDecisionPrompt(toJson(contextPayload));
        decisionRequest.setPrompt(decisionPrompt);
        applyPromptBundle(decisionRequest, decisionPrompt, request, stepInput);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("promptScene", "llm_step_decision");
        metadata.put("ts", Instant.now().toString());
        // 调用模型获取决策结果
        ModelResponse decisionResponse = modelInvocationService.invoke(
                decisionRequest,
                ModelScene.LLM_STEP,
                tenantContext,
                workflowId,
                seqCounter,
                "llm_step_decision",
                metadata
        );

        // 解析模型决策输出
        String rawDecision = decisionResponse != null ? decisionResponse.getContent() : null;
        Map<String, Object> decision = parseJsonMap(rawDecision);
        String mode = normalizeMode(readString(decision, "mode"));

        Map<String, Object> toolMap = readMap(decision.get("tool"));
        String toolName = readString(toolMap, "name");
        log.info("LLM 步骤决策完成, workflowId={}, mode={}, toolName={}", workflowId, mode, toolName);

        if (!MODE_TOOL_CALL.equals(mode)) {
            Map<String, Object> answerOutput = buildAnswerOutput(decision, rawDecision, "llm_step");
            log.info("LLM 步骤完成, workflowId={}, outputKeys={}", workflowId, answerOutput.keySet());
            return answerOutput;
        }

        // 校验工具名称与参数
        Map<String, Object> toolArguments = readMap(toolMap.get("arguments"));
        if (!StringUtils.hasText(toolName)) {
            Map<String, Object> failure = buildToolFailureOutput(decision, "工具名称为空",
                    TOOL_INVALID_REQUEST, "llm_step");
            log.info("LLM 步骤工具调用失败, workflowId={}, reason={}", workflowId, "工具名称为空");
            return failure;
        }
        if (!isToolAvailable(toolName, decisionRequest.getTools())) {
            Map<String, Object> failure = buildToolFailureOutput(decision, "工具不在可用列表中",
                    TOOL_NOT_FOUND, "llm_step");
            log.info("LLM 步骤工具调用失败, workflowId={}, reason={}", workflowId, "工具不在可用列表中");
            return failure;
        }

        // 执行工具调用并回写上下文
        ToolCallResult toolResult = executeToolCall(request, tenantContext, workflowId, taskId,
                seqCounter, toolName, toolArguments);
        updateToolContext(request, stepInput, toolName, toolResult);
        Map<String, Object> summaryOutput = summarizeToolResult(query, decision, toolResult,
                toolName, toolArguments, request, stepInput, tenantContext, workflowId, seqCounter);

        summaryOutput.put("mode", MODE_TOOL_CALL);
        Map<String, Object> toolPayload = new HashMap<>();
        toolPayload.put("name", toolName);
        toolPayload.put("arguments", toolArguments == null ? Map.of() : toolArguments);
        summaryOutput.put("tool", toolPayload);
        summaryOutput.put("toolStatus", toolResult.status);
        if (toolResult.errorCode != null) {
            summaryOutput.put("toolErrorCode", toolResult.errorCode);
        }
        if (toolResult.errorMessage != null) {
            summaryOutput.put("toolErrorMessage", toolResult.errorMessage);
        }
        summaryOutput.putIfAbsent("source", "llm_step");
        log.info("LLM 步骤完成, workflowId={}, toolStatus={}, outputKeys={}",
                workflowId, toolResult.status, summaryOutput.keySet());
        return summaryOutput;
    }

    /**
     * 执行工具调用，统一处理重试与错误码映射。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     * @param toolName 工具名称
     * @param toolArguments 工具调用参数
     * @return 工具执行结果封装
     */
    private ToolCallResult executeToolCall(TaskRequest request,
                                           TenantContext tenantContext,
                                           String workflowId,
                                           String taskId,
                                           AtomicLong seqCounter,
                                           String toolName,
                                           Map<String, Object> toolArguments) {
        RetryPolicy retryPolicy = new RetryPolicy(toolBaseDelayMs, toolMaxDelayMs, toolJitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                // 执行工具调用
                log.info("LLM 步骤工具调用开始, workflowId={}, tool={}, attempt={}",
                        workflowId, toolName, attempt);
                Map<String, Object> result = enforcementGateway.executeWithArguments(
                        request, tenantContext, workflowId, taskId, seqCounter, toolName, toolArguments);
                log.info("LLM 步骤工具调用成功, workflowId={}, tool={}, attempt={}",
                        workflowId, toolName, attempt);
                return ToolCallResult.success(result);
            } catch (ErrorCodeException ex) {
                // 已知错误：映射为统一错误码并判断是否可重试
                String mapped = mapToolErrorCode(ex);
                boolean retryable = isRetryableToolCode(mapped);
                log.warn("LLM 步骤工具调用失败, workflowId={}, tool={}, attempt={}, errorCode={}",
                        workflowId, toolName, attempt, mapped, ex);
                if (retryable && attempt < toolMaxAttempts) {
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                return ToolCallResult.failure(mapped, ex.getReason(), retryable);
            } catch (Exception ex) {
                // 未知异常：统一归类为执行失败
                String mapped = TOOL_EXECUTION_FAILED;
                boolean retryable = isRetryableToolCode(mapped);
                log.error("LLM 步骤工具调用异常, workflowId={}, tool={}, attempt={}",
                        workflowId, toolName, attempt, ex);
                if (retryable && attempt < toolMaxAttempts) {
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                return ToolCallResult.failure(mapped, ex.getMessage(), retryable);
            }
        }
    }

    /**
     * 基于工具执行结果生成二次总结输出。
     *
     * @param query 原始问题
     * @param decision 决策阶段输出
     * @param toolResult 工具执行结果
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 二次总结输出
     */
    private Map<String, Object> summarizeToolResult(String query,
                                                    Map<String, Object> decision,
                                                    ToolCallResult toolResult,
                                                    String toolName,
                                                    Map<String, Object> toolArguments,
                                                    TaskRequest request,
                                                    Map<String, Object> stepInput,
                                                    TenantContext tenantContext,
                                                    String workflowId,
                                                    AtomicLong seqCounter) {
        // 构造用于总结的上下文
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put("query", query);
        toolContext.put("tool", toolName);
        toolContext.put("arguments", toolArguments == null ? Map.of() : toolArguments);
        toolContext.put("status", toolResult.status);
        toolContext.put("result", toolResult.result == null ? Map.of() : toolResult.result);
        if (toolResult.errorCode != null) {
            toolContext.put("errorCode", toolResult.errorCode);
        }
        if (toolResult.errorMessage != null) {
            toolContext.put("errorMessage", toolResult.errorMessage);
        }
        if (decision != null) {
            toolContext.put("decision", decision);
        }
        String summaryPrompt = buildSummaryPrompt(toJson(toolContext));
        ModelRequest summaryRequest = new ModelRequest(summaryPrompt, ModelScene.LLM_STEP);
        applyPromptBundle(summaryRequest, summaryPrompt, request, stepInput);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("promptScene", "llm_step_summary");
        metadata.put("ts", Instant.now().toString());
        ModelResponse summaryResponse = modelInvocationService.invoke(
                summaryRequest,
                ModelScene.LLM_STEP,
                tenantContext,
                workflowId,
                seqCounter,
                "llm_step_summary",
                metadata
        );
        String rawSummary = summaryResponse != null ? summaryResponse.getContent() : null;
        Map<String, Object> parsed = parseJsonMap(rawSummary);
        if (parsed.isEmpty()) {
            // 解析失败时回退为纯文本回答
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("answer", StringUtils.hasText(rawSummary) ? rawSummary : "no_response");
            fallback.put("highlights", toolResult.status.equals(TOOL_STATUS_SUCCESS)
                    ? "工具执行成功" : "工具执行失败");
            fallback.put("confidence", toolResult.status.equals(TOOL_STATUS_SUCCESS) ? 0.5 : 0.2);
            return fallback;
        }
        return parsed;
    }

    /**
     * 构建决策阶段的上下文载荷。
     *
     * @param query 用户问题
     * @param decisionRequest 决策阶段模型请求
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param tenantContext 租户上下文
     * @return 决策上下文映射
     */
    private Map<String, Object> buildDecisionContext(String query,
                                                     ModelRequest decisionRequest,
                                                     TaskRequest request,
                                                     Map<String, Object> stepInput,
                                                     TenantContext tenantContext) {
        Map<String, Object> context = new HashMap<>();
        context.put("query", query);
        context.put("toolChoice", buildToolChoicePayload(decisionRequest.getToolChoice()));
        context.put("availableTools", buildAvailableTools(decisionRequest.getTools()));
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("disableTools", isToolsDisabled(request, stepInput));
        constraints.put("allowedTools", resolveAllowedToolNames(decisionRequest.getTools()));
        context.put("constraints", constraints);
        Map<String, Object> runtime = new HashMap<>();
        if (tenantContext != null) {
            runtime.put("tenantId", tenantContext.getTenantId());
            runtime.put("traceId", tenantContext.getTraceId());
            runtime.put("requestId", tenantContext.getRequestId());
        }
        if (request != null) {
            runtime.put("sessionId", request.getSessionId());
        }
        context.put("runtime", runtime);
        return context;
    }

    /**
     * 生成工具选择策略的 JSON 载荷。
     *
     * @param choice 工具选择策略
     * @return 工具选择载荷
     */
    private Map<String, Object> buildToolChoicePayload(ModelToolChoice choice) {
        if (choice == null || choice.getMode() == null) {
            return Map.of("mode", "auto");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", choice.getMode().name().toLowerCase(Locale.ROOT));
        if (choice.getMode() == ModelToolChoice.Mode.SPECIFIED && StringUtils.hasText(choice.getToolName())) {
            payload.put("toolName", choice.getToolName());
        }
        return payload;
    }

    /**
     * 将工具定义列表转换为可下发的摘要列表。
     *
     * @param tools 工具定义列表
     * @return 工具摘要列表
     */
    private List<Map<String, Object>> buildAvailableTools(List<ModelToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (ModelToolDefinition tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.getName())) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("name", tool.getName());
            item.put("description", tool.getDescription());
            item.put("tags", tool.getTags());
            list.add(item);
        }
        return list;
    }

    /**
     * 提取可用工具名称集合。
     *
     * @param tools 工具定义列表
     * @return 可用工具名称列表
     */
    private List<String> resolveAllowedToolNames(List<ModelToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (ModelToolDefinition tool : tools) {
            if (tool != null && StringUtils.hasText(tool.getName())) {
                names.add(tool.getName());
            }
        }
        return names;
    }

    /**
     * 生成决策阶段 Prompt。
     *
     * @param contextJson 决策上下文 JSON
     * @return Prompt 文本
     */
    private String buildDecisionPrompt(String contextJson) {
        return """
                你是任务执行助手（LLM Step Runner）。你的任务是基于 LLM_STEP_CONTEXT_JSON 决定下一步：
                1) 直接回答（mode="answer"）；或
                2) 选择一个合适的工具并返回工具调用指令（mode="tool_call"）。
                
                【核心原则】
                - 只根据上下文中已有信息回答；禁止编造外部数据结果。
                - 当问题需要外部数据/系统查询/实时状态/数据库检索时，必须选择 tool_call。
                - 当问题属于解释/总结/改写/方案建议等不依赖外部数据时，选择 answer。
                
                【必须使用工具（tool_call）的典型场景】
                - “查询/检索/查库/获取用户信息/订单/日志/监控/实时状态”等需要数据源的任务
                - 上下文明确要求调用工具才能完成（例如提供了 tool schema 或标记 toolRequired=true）
                - 需要精确事实但上下文未提供（如最新状态、具体数值、列表结果）
                
                【必须直接回答（answer）的典型场景】
                - 概念解释、差异对比、步骤说明、代码建议、文档总结（且上下文足够）
                - 工具不可用/无工具满足且可以给出合理的“方法/建议/下一步”，但必须明确限制
                
                【工具选择规则】
                - 工具名称必须严格来自上下文提供的 tools 列表（如 context.tools 或 context.availableTools）；如果未提供工具列表，禁止输出 tool_call，只能输出 answer 并在 reason 中说明“no_tool_list_provided”。
                - 禁止杜撰工具名或参数字段。
                - tool.arguments 必须是最小必要参数集：不得包含大段文本，不得把整个上下文塞进去。
                - 若上下文提供了参数 schema/示例，必须按 schema 组装 arguments。
                
                【防重复/防死循环规则】
                - 如果上下文显示上一次工具调用失败（如 lastToolStatus=FAILED 或 steps 中有 FAILED），再次调用必须调整 arguments 或更换工具；否则选择 answer 并说明原因。
                - 如果多次尝试仍无进展（如 attemptCount 接近上限），优先停止并给出可执行建议（mode="answer"）。
                
                【输出格式】
                只能输出一个 JSON 对象，不能包含任何其他文本，不能使用 Markdown/代码块。
                
                输出 JSON 规范：
                {
                  "mode": "answer" | "tool_call",
                  "answer": "......",
                  "tool": {
                    "name": "工具名称",
                    "arguments": { ... }
                  },
                  "reason": "简短理由（<= 30 字符，禁止逐字推理）",
                  "confidence": 0.0 ~ 1.0
                }
                
                约束：
                - mode="answer" 时：必须输出非空 answer；tool 必须省略或为 null/{}（推荐省略）。
                - mode="tool_call" 时：必须输出 tool.name 与 tool.arguments；answer 可为空串。
                - reason 必须极短，只写选择依据关键词，不得输出逐步推理。
                - confidence：有充分上下文/明确工具契约时更高；缺信息或无工具列表时降低。
                
                输入上下文（JSON）：
                LLM_STEP_CONTEXT_JSON:%s
                """.formatted(contextJson == null ? "{}" : contextJson);

    }

    /**
     * 生成二次总结 Prompt。
     *
     * @param contextJson 工具结果上下文 JSON
     * @return Prompt 文本
     */
    private String buildSummaryPrompt(String contextJson) {
        return """
                你是任务执行助手（LLM Step Runner - Tool Result Summarizer）。
                你的任务：仅依据 LLM_STEP_TOOL_RESULT_JSON 中的工具执行结果，生成最终回答（answer）。
                
                【核心原则】
                - 禁止编造：不得生成工具结果中不存在的事实、列表或数值。
                - 只做汇总：优先提炼关键字段与结论，避免复制大段原文。
                - answer 面向用户；highlights 面向审计/证据。
                
                【成功/失败处理规则】
                1) 工具执行成功（status=COMPLETED/OK 且无 error）：
                   - 若结果有数据：answer 给出结论与必要的结果摘要；highlights 提取关键证据（数量/关键字段/示例）。
                   - 若结果为空（如 total=0/rows=[]/no match）：answer 明确说明“未找到/结果为空”，并给出可调整的查询建议（例如扩大条件、检查关键词）；highlights 说明“空结果证据”。
                
                2) 工具执行失败（status=FAILED 或存在 error）：
                   - answer 必须说明失败原因（从 error.message/errorCode 提取，避免长堆栈），并给出可执行下一步建议：
                     * 需要补充哪些参数/权限
                     * 是否改用其他工具/更换查询条件
                     * 是否重试（并说明建议的修改）
                   - highlights 必须是失败原因摘要（errorCode + 简短原因）。
                
                【长度与合规】
                - answer：尽量简洁，避免粘贴原始返回；如有列表，只展示前 N 条（N<=5）。
                - highlights：1~2 句，<= 200 字符；禁止长引用/堆栈复制。
                
                【置信度（confidence）规则】
                - 有明确成功结果且证据充分：0.7~0.95
                - 成功但结果为空：0.5~0.7
                - 失败但原因明确且建议可执行：0.3~0.5
                - 失败且原因不明/上下文不足：0.0~0.3
                
                【输出格式】
                只能输出一个 JSON 对象，不能包含任何其他文本，不能使用 Markdown/代码块。
                
                输出 JSON 规范：
                {
                  "answer": "...",
                  "highlights": "关键证据或失败原因摘要",
                  "confidence": 0.0 ~ 1.0
                }
                
                输入上下文（JSON）：
                LLM_STEP_TOOL_RESULT_JSON:%s
                """.formatted(contextJson == null ? "{}" : contextJson);
    }

    /**
     * 将 Prompt 组装为多轮消息并写入模型请求。
     *
     * @param request 模型请求
     * @param prompt 当前 Prompt 文本
     * @param taskRequest 任务请求
     * @param stepInput 步骤输入
     */
    private void applyPromptBundle(ModelRequest request, String prompt, TaskRequest taskRequest,
                                   Map<String, Object> stepInput) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, taskRequest, stepInput);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }

    /**
     * 构建直接回答模式的输出结构。
     *
     * @param decision 决策输出
     * @param raw 原始输出文本
     * @param source 来源标识
     * @return 输出映射
     */
    private Map<String, Object> buildAnswerOutput(Map<String, Object> decision,
                                                  String raw,
                                                  String source) {
        Map<String, Object> output = new HashMap<>();
        String answer = readString(decision, "answer");
        if (!StringUtils.hasText(answer)) {
            answer = StringUtils.hasText(raw) ? raw : "no_response";
        }
        output.put("mode", MODE_ANSWER);
        output.put("answer", answer);
        output.put("reason", readString(decision, "reason"));
        output.put("confidence", readNumber(decision, "confidence", 0.5));
        output.put("source", source);
        return output;
    }

    /**
     * 构建工具调用失败时的输出结构。
     *
     * @param decision 决策输出
     * @param message 失败原因
     * @param errorCode 错误码
     * @param source 来源标识
     * @return 输出映射
     */
    private Map<String, Object> buildToolFailureOutput(Map<String, Object> decision,
                                                       String message,
                                                       String errorCode,
                                                       String source) {
        Map<String, Object> output = new HashMap<>();
        output.put("mode", MODE_TOOL_CALL);
        output.put("answer", message);
        output.put("highlights", message);
        output.put("confidence", 0.1);
        output.put("toolErrorCode", errorCode);
        if (decision != null) {
            output.put("toolDecision", decision);
        }
        output.put("source", source);
        return output;
    }

    /**
     * 优先从步骤输入中提取问题文本。
     *
     * @param stepInput 步骤输入
     * @param request 任务请求
     * @return 问题文本
     */
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

    /**
     * 判断是否禁用工具调用。
     *
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @return 是否禁用工具
     */
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

    /**
     * 将布尔或字符串值转换为布尔判断。
     *
     * @param value 待判断值
     * @return 是否为真
     */
    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /**
     * 规范化模型输出的 mode 字段。
     *
     * @param mode 原始 mode
     * @return 规范化 mode
     */
    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return MODE_ANSWER;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return MODE_TOOL_CALL.equals(normalized) ? MODE_TOOL_CALL : MODE_ANSWER;
    }

    /**
     * 判断工具是否在可用列表中或已注册。
     *
     * @param toolName 工具名称
     * @param tools 可用工具列表
     * @return 是否可用
     */
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

    /**
     * 将异常映射为统一工具错误码。
     *
     * @param ex 业务异常
     * @return 统一错误码
     */
    private String mapToolErrorCode(ErrorCodeException ex) {
        if (ex == null) {
            return TOOL_EXECUTION_FAILED;
        }
        int status = ex.getStatusCode() != null ? ex.getStatusCode().value() : -1;
        String code = ex.getErrorCode();
        if (status == HttpStatus.BAD_REQUEST.value() || "INVALID_REQUEST".equalsIgnoreCase(code)) {
            return TOOL_INVALID_REQUEST;
        }
        if (status == HttpStatus.NOT_FOUND.value() || "NOT_FOUND".equalsIgnoreCase(code)) {
            return TOOL_NOT_FOUND;
        }
        if (status == HttpStatus.REQUEST_TIMEOUT.value() || status == HttpStatus.GATEWAY_TIMEOUT.value()
                || "TIMEOUT".equalsIgnoreCase(code)) {
            return TOOL_TIMEOUT;
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value() || "RATE_LIMITED".equalsIgnoreCase(code)) {
            return TOOL_RATE_LIMITED;
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE.value() || "MCP_UNAVAILABLE".equalsIgnoreCase(code)
                || "CIRCUIT_OPEN".equalsIgnoreCase(code)) {
            return TOOL_UNAVAILABLE;
        }
        return TOOL_EXECUTION_FAILED;
    }

    /**
     * 判断工具错误码是否允许重试。
     *
     * @param code 错误码
     * @return 是否可重试
     */
    private boolean isRetryableToolCode(String code) {
        return TOOL_TIMEOUT.equals(code) || TOOL_RATE_LIMITED.equals(code) || TOOL_UNAVAILABLE.equals(code);
    }

    /**
     * 回写工具调用结果到上下文，便于后续步骤或摘要复用。
     *
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param toolName 工具名称
     * @param toolResult 工具执行结果
     */
    /**
     * 回写工具调用上下文，供后续步骤复用。
     *
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param toolName 工具名称
     * @param toolResult 工具执行结果
     */
    private void updateToolContext(TaskRequest request,
                                   Map<String, Object> stepInput,
                                   String toolName,
                                   ToolCallResult toolResult) {
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

    /**
     * 构建工具失败信息载荷。
     *
     * @param toolResult 工具执行结果
     * @return 错误信息载荷
     */
    private Map<String, Object> buildToolErrorPayload(ToolCallResult toolResult) {
        if (toolResult == null || toolResult.errorCode == null) {
            return null;
        }
        Map<String, Object> error = new HashMap<>();
        error.put("code", toolResult.errorCode);
        if (toolResult.errorMessage != null) {
            error.put("message", toolResult.errorMessage);
        }
        return error;
    }

    /**
     * 写入工具上下文关键字段。
     *
     * @param target 目标映射
     * @param toolName 工具名称
     * @param toolResult 工具结果
     * @param errorPayload 错误载荷
     */
    private void putToolContext(Map<String, Object> target,
                                String toolName,
                                ToolCallResult toolResult,
                                Map<String, Object> errorPayload) {
        if (target == null) {
            return;
        }
        target.put("lastToolResult", toolResult != null ? toolResult.result : null);
        if (errorPayload != null) {
            target.put("lastToolError", errorPayload);
        } else {
            target.remove("lastToolError");
        }
        target.put("selectedTools", List.of(toolName));
    }

    /**
     * 保证任务上下文为可变 Map。
     *
     * @param request 任务请求
     * @return 可变上下文
     */
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
        if (!(context instanceof HashMap)) {
            context = new HashMap<>(context);
            request.setContext(context);
        }
        return context;
    }

    /**
     * 将未知类型 Map 转换为可变 Map。
     *
     * @param source 原始映射
     * @return 可变映射
     */
    private Map<String, Object> toMutableMap(Map<?, ?> source) {
        Map<String, Object> target = new HashMap<>();
        if (source == null) {
            return target;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                target.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return target;
    }

    /**
     * 解析 JSON 字符串为 Map。
     *
     * @param raw JSON 文本
     * @return 解析结果
     */
    private Map<String, Object> parseJsonMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 对象转 JSON 文本。
     *
     * @param value 目标对象
     * @return JSON 文本
     */
    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    /**
     * 读取字符串字段。
     *
     * @param map 目标映射
     * @param key 字段名
     * @return 字符串值
     */
    private String readString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 读取数值字段并提供兜底值。
     *
     * @param map 目标映射
     * @param key 字段名
     * @param fallback 兜底值
     * @return 数值
     */
    private double readNumber(Map<String, Object> map, String key, double fallback) {
        if (map == null || key == null) {
            return fallback;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    /**
     * 将任意对象安全转换为 Map。
     *
     * @param value 待转换对象
     * @return Map 结果
     */
    private Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> output = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    output.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return output;
        }
        return new HashMap<>();
    }

    private static final class ToolCallResult {
        private final String status;
        private final Map<String, Object> result;
        private final String errorCode;
        private final String errorMessage;
        private final boolean retryable;

        private ToolCallResult(String status,
                               Map<String, Object> result,
                               String errorCode,
                               String errorMessage,
                               boolean retryable) {
            this.status = status;
            this.result = result;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.retryable = retryable;
        }

        private static ToolCallResult success(Map<String, Object> result) {
            return new ToolCallResult(TOOL_STATUS_SUCCESS, result, null, null, false);
        }

        private static ToolCallResult failure(String errorCode, String errorMessage, boolean retryable) {
            return new ToolCallResult(TOOL_STATUS_FAILED, null, errorCode, errorMessage, retryable);
        }
    }
}

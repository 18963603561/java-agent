package com.example.agent.reasoning.cot;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.ReasoningStrategy;
import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import com.example.agent.reasoning.common.result.CotPayload;
import com.example.agent.reasoning.common.telemetry.ReasoningEventPublisher;
import com.example.agent.reasoning.common.telemetry.ReasoningMetricsPublisher;
import com.example.agent.reasoning.common.telemetry.ReasoningTraceRecorder;
import java.util.ArrayList;
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
 * 链式推理执行器，负责按步调用模型并输出结构化结果。
 * <p>用途：通过多步模型调用生成简化推理摘要与最终答复。
 * <p>输入：问题文本、上下文输入与链路信息。
 * <p>输出：链式推理结果对象。
 * <p>边界：超过最大步数或解析失败时返回未完成状态。
 * <p>示例：
 * <pre>{@code
 * ChainOfThoughtResult result = chainOfThoughtService.run(question, input, ctx, wfId, seq);
 * }</pre>
 */
@Service
public class ChainOfThoughtService implements ReasoningStrategy {

    /**
     * 日志记录器。
     * <p>示例：记录链式推理起止与异常。
     */
    private static final Logger log = LoggerFactory.getLogger(ChainOfThoughtService.class);
    /**
     * 问题文本最大长度。
     * <p>示例：{@code 500}。
     */
    private final int maxQuestionChars;
    /**
     * 单步摘要最大长度。
     * <p>示例：{@code 200}。
     */
    private final int maxStepSummaryChars;

    /**
     * 模型调用服务。
     * <p>示例：执行分步调用获取推理结果。
     */
    private final ModelInvocationService modelInvocationService;
    /**
     * 提示词装配器。
     * <p>示例：构建模型消息列表。
     */
    private final PromptAssembler promptAssembler;
    private final JsonOutputRepairService jsonOutputRepairService;
    private final CotContextBuilder cotContextBuilder;
    private final CotDecisionParser cotDecisionParser;
    private final CotAnswerSanitizer cotAnswerSanitizer;
    private final ReasoningEventPublisher reasoningEventPublisher;
    private final ReasoningTraceRecorder reasoningTraceRecorder;
    private final ReasoningMetricsPublisher reasoningMetricsPublisher;
    /**
     * 链式推理配置。
     * <p>示例：控制最大步数与温度参数。
     */
    private final CotProperties properties;

    /**
     * 构造链式推理服务。
     *
     * <p>输入：模型调用服务、提示词装配器与配置对象。
     * <p>输出：初始化后的服务实例。
     * <p>示例：
     * <pre>{@code
     * new ChainOfThoughtService(invocationService, promptAssembler, mapper, publisher, streamService, props);
     * }</pre>
     *
     * @param modelInvocationService 模型调用服务
     * @param promptAssembler 提示词装配器
     * @param properties 配置对象
     */
    public ChainOfThoughtService(ModelInvocationService modelInvocationService,
                                 PromptAssembler promptAssembler,
                                 CotProperties properties,
                                 JsonOutputRepairService jsonOutputRepairService,
                                 CotContextBuilder cotContextBuilder,
                                 CotDecisionParser cotDecisionParser,
                                 CotAnswerSanitizer cotAnswerSanitizer,
                                 ReasoningEventPublisher reasoningEventPublisher,
                                 ReasoningTraceRecorder reasoningTraceRecorder,
                                 ReasoningMetricsPublisher reasoningMetricsPublisher,
                                 ReasoningConfigResolver reasoningConfigResolver) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.properties = properties;
        this.jsonOutputRepairService = jsonOutputRepairService;
        this.cotContextBuilder = cotContextBuilder;
        this.cotDecisionParser = cotDecisionParser;
        this.cotAnswerSanitizer = cotAnswerSanitizer;
        this.reasoningEventPublisher = reasoningEventPublisher;
        this.reasoningTraceRecorder = reasoningTraceRecorder;
        this.reasoningMetricsPublisher = reasoningMetricsPublisher;
        this.maxQuestionChars = reasoningConfigResolver.resolveCotMaxQuestionChars();
        this.maxStepSummaryChars = reasoningConfigResolver.resolveCotMaxStepSummaryChars();
    }

    /**
     * 执行链式推理。
     *
     * <p>输入：问题文本、上下文输入与链路信息。
     * <p>输出：链式推理结果对象。
     * <p>边界：达到最大步数或解析失败时返回未完成状态。
     * <p>示例：
     * <pre>{@code
     * ChainOfThoughtResult result = run(question, input, tenantContext, workflowId, seqCounter);
     * }</pre>
     *
     * @param question 问题或主题
     * @param input 上下文输入
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 推理结果
     */
    public ChainOfThoughtResult run(String question,
                                    Map<String, Object> input,
                                    TenantContext tenantContext,
                                    String workflowId,
                                    AtomicLong seqCounter) {
        long startedAt = System.currentTimeMillis();
        // 对问题文本进行截断，避免超长输入影响模型。
        String safeQuestion = truncate(question, maxQuestionChars);
        int maxSteps = Math.max(1, properties.getMaxSteps());
        List<String> stepSummaries = new ArrayList<>();
        int stepsExecuted = 0;
        String finalAnswer = null;
        String stopReason = null;
        double confidence = 0.5;
        boolean completed = false;
        String lastRawRef = null;

        Map<String, Object> startedPayload = new HashMap<>();
        startedPayload.put("question", safeQuestion);
        startedPayload.put("maxSteps", maxSteps);
        startedPayload.put("modelHint", properties.getModelHint());
        publishEvent(tenantContext, workflowId, seqCounter, EventType.COT_STARTED, startedPayload);

        log.info("链式推理开始, tenantId={}, workflowId={}, maxSteps={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                maxSteps);

        try {
            // 逐步推理循环，直到完成或达到最大步数。
            for (int stepIndex = 1; stepIndex <= maxSteps; stepIndex++) {
                stepsExecuted++;
                // 构建当前步骤提示词并发起模型调用。
                List<String> recentSummaries = resolveRecentStepSummaries(stepSummaries);
                String prompt = buildPrompt(safeQuestion, input, recentSummaries, stepIndex, maxSteps);
                ModelRequest request = new ModelRequest(prompt, resolveScene(properties.getModelHint()));
                applyPromptBundle(request, prompt, input);
                if (properties.getTemperatureOverride() != null) {
                    request.setTemperature(properties.getTemperatureOverride());
                }
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("stepIndex", stepIndex);
                metadata.put("maxSteps", maxSteps);
                metadata.put("promptScene", "cot");
                ModelResponse response = modelInvocationService.invoke(
                        request,
                        request.getScene(),
                        tenantContext,
                        workflowId,
                        seqCounter,
                        "cot",
                        metadata
                );
                if (response != null && StringUtils.hasText(response.getRawRef())) {
                    lastRawRef = response.getRawRef();
                }
                // 解析模型输出的决策与摘要。
                String rawContent = response != null ? response.getContent() : null;
                boolean repairAttempted = false;
                boolean repairSuccess = false;
                String parseErrorType = null;
                StepDecision decision = parseDecision(rawContent);
                if (!decision.valid) {
                    parseErrorType = resolveParseErrorType(decision.stopReason);
                    repairAttempted = true;
                    StepDecision repaired = tryRepairDecision(rawContent, safeQuestion, input, recentSummaries, stepIndex,
                            maxSteps);
                    if (repaired.valid) {
                        decision = repaired;
                        repairSuccess = true;
                    } else {
                        log.warn("链式推理修复失败, stepIndex={}", stepIndex);
                    }
                }
                recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                        response != null ? response.getModelId() : null, decision.valid, parseErrorType,
                        repairAttempted, repairSuccess);
                reasoningMetricsPublisher.recordParseResult("cot", decision.valid, parseErrorType);
                reasoningMetricsPublisher.recordRepairResult("cot", repairAttempted, repairSuccess);
                if (StringUtils.hasText(decision.stepSummary)) {
                    stepSummaries.add(truncate(decision.stepSummary, maxStepSummaryChars));
                }
                confidence = normalizeConfidence(decision.confidence, confidence);
                if (properties.isEmitStepEvents()) {
                    Map<String, Object> stepPayload = new HashMap<>();
                    stepPayload.put("stepIndex", stepIndex);
                    stepPayload.put("shouldContinue", decision.shouldContinue);
                    stepPayload.put("confidence", confidence);
                    publishEvent(tenantContext, workflowId, seqCounter, EventType.COT_STEP, stepPayload);
                }
                // 校验决策合法性。
                if (!decision.valid) {
                    stopReason = normalizeStopReason(decision.stopReason, "invalid_response");
                    completed = false;
                    break;
                }
                if (StringUtils.hasText(decision.finalAnswer)) {
                    finalAnswer = decision.finalAnswer.trim();
                }
                // 达到结束条件则退出循环。
                if (!decision.shouldContinue || StringUtils.hasText(finalAnswer)) {
                    completed = true;
                    stopReason = normalizeStopReason(decision.stopReason, "completed");
                    break;
                }
            }
        } catch (Exception ex) {
            stopReason = "error";
            Map<String, Object> stopPayload = new HashMap<>();
            stopPayload.put("stepsCount", stepsExecuted);
            stopPayload.put("stopReason", stopReason);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.COT_STOPPED, stopPayload);
            log.error("链式推理异常, tenantId={}, workflowId={}, steps={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    stepsExecuted,
                    ex);
            throw ex;
        }

        if (!completed && !StringUtils.hasText(stopReason)) {
            stopReason = "max_steps";
        }
        if (!StringUtils.hasText(finalAnswer) && completed && !stepSummaries.isEmpty()) {
            finalAnswer = stepSummaries.get(stepSummaries.size() - 1);
        }
        String sanitizedAnswer = sanitizeFinalAnswer(finalAnswer);
        if (completed && !StringUtils.hasText(sanitizedAnswer)) {
            completed = false;
            stopReason = "invalid_response";
        }

        ChainOfThoughtResult result = new ChainOfThoughtResult();
        result.setFinalAnswer(sanitizedAnswer);
        result.setStepsCount(stepsExecuted);
        result.setConfidence(confidence);
        result.setStopReason(stopReason);
        result.setCompleted(completed);
        result.setRawRef(lastRawRef);

        if (completed) {
            Map<String, Object> completedPayload = new HashMap<>();
            completedPayload.put("stepsCount", stepsExecuted);
            completedPayload.put("confidence", confidence);
            completedPayload.put("stopReason", stopReason);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.COT_COMPLETED, completedPayload);
        } else {
            Map<String, Object> stoppedPayload = new HashMap<>();
            stoppedPayload.put("stepsCount", stepsExecuted);
            stoppedPayload.put("stopReason", stopReason);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.COT_STOPPED, stoppedPayload);
        }

        log.info("链式推理结束, tenantId={}, workflowId={}, steps={}, stopReason={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                stepsExecuted,
                stopReason);
        reasoningMetricsPublisher.recordDuration("cot", System.currentTimeMillis() - startedAt);
        return result;
    }

    @Override
    public boolean supports(String strategyType) {
        if (!StringUtils.hasText(strategyType)) {
            return false;
        }
        return "CHAIN_OF_THOUGHT".equalsIgnoreCase(strategyType)
                || "COT".equalsIgnoreCase(strategyType);
    }

    @Override
    public ReasoningResult execute(ReasoningRequest request) {
        ChainOfThoughtResult result = run(
                request != null ? request.getPrompt() : null,
                request != null ? request.getInput().attributes() : Map.of(),
                request != null ? request.getTenantContext() : null,
                request != null ? request.getWorkflowId() : null,
                request != null ? request.getSeqCounter() : null
        );
        CotPayload payload = new CotPayload(result.getStepsCount(), result.getFinalAnswer());
        return new ReasoningResult(
                "COT",
                result.getFinalAnswer(),
                result.getConfidence(),
                result.getStopReason(),
                result.isCompleted() ? "COMPLETED" : "STOPPED",
                result.getRawRef(),
                payload
        );
    }

    /**
     * 构建链式推理提示词。
     *
     * <p>输入：问题文本、上下文输入与步骤摘要。
     * <p>输出：提示词字符串。
     * <p>边界：序列化失败时使用空上下文。
     * <p>示例：
     * <pre>{@code
     * String prompt = buildPrompt(question, input, summaries, 1, 3);
     * }</pre>
     */
    private String buildPrompt(String question,
                               Map<String, Object> input,
                               List<String> stepSummaries,
                               int stepIndex,
                               int maxSteps) {
        CotContextBuilder.CotContext cotContext = cotContextBuilder.build(question, input, stepSummaries,
                stepIndex, maxSteps);
        String contextJson = cotContext.contextJson();
        return """
            你是链式推理助手（COT helper），严格禁止输出逐字思维链/详细推理过程。
            你只能输出“非常简短的下一步摘要”或“最终答案”。
            
            【输入说明】
            - question 是用户问题。
            - memorySummary 是已压缩的记忆摘要。
            - recentObservation 是最近一次观察/工具结果的“受限摘要”。
            - previousSteps 仅用于判断进展与避免重复；只允许参考每步的 summary/status/errorCode，不得复述或展开其内容。
            - stepIndex/maxSteps 用于控制是否继续；到达 maxSteps 必须停止。
            
            【输出规则】
            - stepSummary：1~2 句，描述下一步要做什么或为什么停止；不得包含逐字推理、长引用、内部草稿、敏感细节。
            - shouldContinue：
              - 若需要更多外部信息/工具或仍未回答问题，则 true（但必须确保 stepIndex < maxSteps）。
              - 若可直接回答或已达到 maxSteps，则 false。
            - finalAnswer：仅当 shouldContinue=false 时填写，否则必须为空串。
            - confidence：依据证据充分度给 0~1，缺信息降低。
            - stopReason：当 shouldContinue=false 必填（如 question_answered / max_steps_reached / insufficient_info_stop）。
            
            【强制限制】
            - 禁止输出 previousSteps 或 recentObservation 的原文/长文本。
            - 禁止编造不存在的工具结果或事实。
            
            输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
            最小示例 JSON：{"stepSummary":"","shouldContinue":false,"finalAnswer":"","confidence":0.5,"stopReason":""}
            
            COT_CONTEXT_JSON:%s
            """.formatted(contextJson);

    }

    private List<String> resolveRecentStepSummaries(List<String> stepSummaries) {
        if (stepSummaries == null || stepSummaries.isEmpty()) {
            return List.of();
        }
        int max = properties != null ? properties.getMaxStepSummaries() : 0;
        if (max <= 0) {
            max = 10;
        }
        if (stepSummaries.size() <= max) {
            return new ArrayList<>(stepSummaries);
        }
        return new ArrayList<>(stepSummaries.subList(stepSummaries.size() - max, stepSummaries.size()));
    }

    private StepDecision tryRepairDecision(String rawContent,
                                           String question,
                                           Map<String, Object> input,
                                           List<String> stepSummaries,
                                           int stepIndex,
                                           int maxSteps) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return StepDecision.invalid("invalid_response");
        }
        CotContextBuilder.CotContext cotContext = cotContextBuilder.build(question, input, stepSummaries,
                stepIndex, maxSteps);
        String contextJson = cotContext.contextJson();
        String repaired = jsonOutputRepairService.repair("cot", rawContent, JsonOutputSchema.COT, contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return StepDecision.invalid("invalid_response");
        }
        return parseDecision(repaired);
    }

    private void recordPromptTrace(Map<String, Object> metadata,
                                   String promptText,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   String modelId,
                                   boolean parseSuccess,
                                   String parseErrorType,
                                   boolean repairAttempted,
                                   boolean repairSuccess) {
        reasoningTraceRecorder.record(
                "cot",
                metadata,
                promptText,
                tenantContext,
                workflowId,
                seqCounter,
                modelId,
                parseSuccess,
                parseErrorType,
                repairAttempted,
                repairSuccess
        );
    }

    private String resolveParseErrorType(String stopReason) {
        if (!StringUtils.hasText(stopReason)) {
            return "json_parse_error";
        }
        if ("empty_response".equals(stopReason)) {
            return "empty_output";
        }
        return "json_parse_error";
    }

    /**
     * 解析模型输出的决策信息。
     */
    private StepDecision parseDecision(String content) {
        CotDecisionParser.CotDecision decision = cotDecisionParser.parse(content);
        return new StepDecision(
                decision.shouldContinue(),
                decision.stepSummary(),
                decision.finalAnswer(),
                decision.confidence(),
                decision.stopReason(),
                decision.valid()
        );
    }

    /**
     * 规整置信度范围到 {@code 0-1}。
     *
     * <p>输入：候选置信度与回退值。
     * <p>输出：规整后的置信度。
     * <p>示例：
     * <pre>{@code
     * double value = normalizeConfidence(candidate, 0.5);
     * }</pre>
     */
    private double normalizeConfidence(Double candidate, double fallback) {
        double value = candidate != null ? candidate : fallback;
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }

    /**
     * 规整停止原因字段。
     *
     * <p>输入：停止原因与回退值。
     * <p>输出：最终停止原因。
     * <p>示例：
     * <pre>{@code
     * String reason = normalizeStopReason(stopReason, "completed");
     * }</pre>
     */
    private String normalizeStopReason(String stopReason, String fallback) {
        if (StringUtils.hasText(stopReason)) {
            return stopReason.trim();
        }
        return fallback;
    }

    /**
     * 清洗最终答案，去除推理痕迹。
     */
    private String sanitizeFinalAnswer(String raw) {
        return cotAnswerSanitizer.sanitize(raw, properties.getMaxFinalAnswerChars());
    }

    /**
     * 根据模型提示选择调用场景。
     *
     * <p>输入：模型提示字符串。
     * <p>输出：模型场景枚举。
     * <p>边界：提示为空时使用默认场景。
     * <p>示例：
     * <pre>{@code
     * ModelScene scene = resolveScene("planner");
     * }</pre>
     */
    private ModelScene resolveScene(String modelHint) {
        if (!StringUtils.hasText(modelHint)) {
            return ModelScene.REFLECT;
        }
        String normalized = modelHint.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "planner" -> ModelScene.PLANNER;
            case "research" -> ModelScene.RESEARCH;
            case "cheap" -> ModelScene.CHEAP;
            case "reflect" -> ModelScene.REFLECT;
            default -> ModelScene.REFLECT;
        };
    }

    /**
     * 发布链式推理事件。
     *
     * <p>输入：租户上下文、工作流标识与事件载荷。
     * <p>输出：无。
     * <p>边界：上下文或标识为空时不发布。
     * <p>示例：
     * <pre>{@code
     * publishEvent(ctx, wfId, seq, EventType.COT_STEP, payload);
     * }</pre>
     */
    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter,
                              EventType type,
                              Map<String, Object> payload) {
        reasoningEventPublisher.publishStrategyEvent(
                tenantContext,
                workflowId,
                seqCounter,
                type,
                "cot",
                payload
        );
    }

    /**
     * 应用提示词装配器，将提示词转换为消息结构。
     *
     * <p>输入：模型请求、提示词与输入上下文。
     * <p>输出：无。
     * <p>边界：装配器为空时直接返回。
     * <p>示例：
     * <pre>{@code
     * applyPromptBundle(request, prompt, input);
     * }</pre>
     */
    private void applyPromptBundle(ModelRequest request, String prompt, Map<String, Object> input) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, LlmTaskContext.empty(), input);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }

    /**
     * 截断文本长度。
     *
     * <p>输入：文本内容与最大长度。
     * <p>输出：截断后的文本。
     * <p>边界：最大长度小于等于零时返回原文本。
     * <p>示例：
     * <pre>{@code
     * String value = truncate(text, 100);
     * }</pre>
     */
    private String truncate(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }

    /**
     * 链式推理步骤决策对象。
     *
     * <p>用途：封装模型输出的步骤决策与答案。
     * <p>示例：{@code StepDecision.invalid("invalid_response")}。
     */
    private record StepDecision(boolean shouldContinue,
                                String stepSummary,
                                String finalAnswer,
                                Double confidence,
                                String stopReason,
                                boolean valid) {

        /**
         * 构建无效决策对象。
         *
         * <p>输入：停止原因。
         * <p>输出：无效决策对象。
         * <p>示例：
         * <pre>{@code
         * StepDecision decision = StepDecision.invalid("empty_response");
         * }</pre>
         *
         * @param stopReason 停止原因
         * @return 无效决策
         */
        private static StepDecision invalid(String stopReason) {
            return new StepDecision(false, null, null, 0.3, stopReason, false);
        }
    }
}

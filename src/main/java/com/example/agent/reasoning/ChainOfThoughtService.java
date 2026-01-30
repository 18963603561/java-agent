package com.example.agent.reasoning;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.streaming.EventStreamService;
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
import org.springframework.context.ApplicationEventPublisher;
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
public class ChainOfThoughtService {

    /**
     * 日志记录器。
     * <p>示例：记录链式推理起止与异常。
     */
    private static final Logger log = LoggerFactory.getLogger(ChainOfThoughtService.class);
    /**
     * 问题文本最大长度。
     * <p>示例：{@code 500}。
     */
    private static final int MAX_QUESTION_CHARS = 500;
    /**
     * 记忆摘要最大长度。
     * <p>示例：{@code 800}。
     */
    private static final int MAX_MEMORY_CHARS = 800;
    /**
     * 观测信息最大长度。
     * <p>示例：{@code 500}。
     */
    private static final int MAX_OBSERVATION_CHARS = 500;
    /**
     * 单步摘要最大长度。
     * <p>示例：{@code 200}。
     */
    private static final int MAX_STEP_SUMMARY_CHARS = 200;
    /**
     * 最终答案标记集合。
     * <p>示例：{@code "final answer"}。
     */
    private static final List<String> FINAL_ANSWER_MARKERS = List.of(
            "final answer",
            "answer:",
            "final:",
            "最终答案",
            "最终结论",
            "结论:",
            "结论：",
            "答案:",
            "答案："
    );
    /**
     * 推理标记集合，用于过滤推理文本。
     * <p>示例：{@code "chain-of-thought"}。
     */
    private static final List<String> REASONING_MARKERS = List.of(
            "chain-of-thought",
            "chain of thought",
            "reasoning",
            "thoughts",
            "analysis",
            "let's think step by step",
            "step by step",
            "step 1",
            "step 2",
            "step 3",
            "步骤1",
            "步骤2",
            "步骤3",
            "步骤一",
            "步骤二",
            "步骤三",
            "思维链",
            "推理",
            "思考过程"
    );

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
    /**
     * 序列化工具。
     * <p>示例：构建上下文 {@code JSON}。
     */
    private final ObjectMapper objectMapper;
    /**
     * 事件发布器。
     * <p>示例：发布链式推理阶段事件。
     */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 事件流服务。
     * <p>示例：生成事件序列号。
     */
    private final EventStreamService eventStreamService;
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
     * @param objectMapper 序列化工具
     * @param eventPublisher 事件发布器
     * @param eventStreamService 事件流服务
     * @param properties 配置对象
     */
    public ChainOfThoughtService(ModelInvocationService modelInvocationService,
                                 PromptAssembler promptAssembler,
                                 ObjectMapper objectMapper,
                                 ApplicationEventPublisher eventPublisher,
                                 EventStreamService eventStreamService,
                                 CotProperties properties) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.properties = properties;
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
        // 对问题文本进行截断，避免超长输入影响模型。
        String safeQuestion = truncate(question, MAX_QUESTION_CHARS);
        int maxSteps = Math.max(1, properties.getMaxSteps());
        List<String> stepSummaries = new ArrayList<>();
        int stepsExecuted = 0;
        String finalAnswer = null;
        String stopReason = null;
        double confidence = 0.5;
        boolean completed = false;

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
                String prompt = buildPrompt(safeQuestion, input, stepSummaries, stepIndex, maxSteps);
                ModelRequest request = new ModelRequest(prompt, resolveScene(properties.getModelHint()));
                applyPromptBundle(request, prompt, input);
                if (properties.getTemperatureOverride() != null) {
                    request.setTemperature(properties.getTemperatureOverride());
                }
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("stepIndex", stepIndex);
                metadata.put("maxSteps", maxSteps);
                ModelResponse response = modelInvocationService.invoke(
                        request,
                        request.getScene(),
                        tenantContext,
                        workflowId,
                        seqCounter,
                        "cot",
                        metadata
                );
                // 解析模型输出的决策与摘要。
                StepDecision decision = parseDecision(response != null ? response.getContent() : null);
                if (StringUtils.hasText(decision.stepSummary)) {
                    stepSummaries.add(truncate(decision.stepSummary, MAX_STEP_SUMMARY_CHARS));
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
        return result;
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
        Map<String, Object> context = new HashMap<>();
        context.put("question", question);
        String memorySummary = extractMemorySummary(input);
        if (StringUtils.hasText(memorySummary)) {
            context.put("memorySummary", memorySummary);
        }
        String observation = extractObservation(input);
        if (StringUtils.hasText(observation)) {
            context.put("recentObservation", observation);
        }
        if (stepSummaries != null && !stepSummaries.isEmpty()) {
            context.put("previousSteps", stepSummaries);
        }
        context.put("stepIndex", stepIndex);
        context.put("maxSteps", maxSteps);
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
                你是链式推理助手，但禁止输出逐字思维链。
                请基于问题给出下一步的简短摘要或最终答案。
                输出要求：仅输出 JSON，字段包含 stepSummary、shouldContinue、finalAnswer、confidence、stopReason。
                约束：stepSummary 必须是简短摘要，不得包含逐字推理，不得包含敏感细节。
                COT_CONTEXT_JSON:%s
                """.formatted(contextJson);
    }

    /**
     * 解析模型输出的决策信息。
     *
     * <p>输入：模型输出内容。
     * <p>输出：决策对象。
     * <p>边界：内容为空或解析失败时返回无效决策。
     * <p>示例：
     * <pre>{@code
     * StepDecision decision = parseDecision(content);
     * }</pre>
     */
    private StepDecision parseDecision(String content) {
        if (!StringUtils.hasText(content)) {
            return StepDecision.invalid("empty_response");
        }
        try {
            String normalized = normalizeJsonPayload(content);
            if (!StringUtils.hasText(normalized)) {
                return StepDecision.invalid("empty_response");
            }
            Map<String, Object> root = objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() {
            });
            boolean shouldContinue = resolveBoolean(root, "shouldContinue", true);
            String stepSummary = resolveString(root, "stepSummary", "summary");
            String finalAnswer = resolveString(root, "finalAnswer", "answer");
            Double confidence = resolveDouble(root, "confidence");
            String stopReason = resolveString(root, "stopReason", null);
            if (!shouldContinue && !StringUtils.hasText(stopReason)) {
                stopReason = "completed";
            }
            return new StepDecision(shouldContinue, stepSummary, finalAnswer, confidence, stopReason, true);
        } catch (Exception ex) {
            log.warn("链式推理输出解析失败, reason={}", ex.getMessage());
            return StepDecision.invalid("invalid_response");
        }
    }

    /**
     * 兼容模型输出中的代码块包裹与噪声内容。
     *
     * <p>输入：模型输出内容。
     * <p>输出：清洗后的 {@code JSON} 文本。
     * <p>边界：内容为空时原样返回。
     * <p>示例：
     * <pre>{@code
     * String json = normalizeJsonPayload(content);
     * }</pre>
     */
    private String normalizeJsonPayload(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd >= 0) {
                trimmed = trimmed.substring(firstLineEnd + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
        }
        trimmed = trimmed.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        return trimmed.trim();
    }

    /**
     * 解析布尔值字段。
     *
     * <p>输入：映射对象、字段名与默认值。
     * <p>输出：布尔值。
     * <p>示例：
     * <pre>{@code
     * boolean value = resolveBoolean(root, "shouldContinue", true);
     * }</pre>
     */
    private boolean resolveBoolean(Map<String, Object> root, String key, boolean defaultValue) {
        if (root == null || !root.containsKey(key)) {
            return defaultValue;
        }
        Object value = root.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }

    /**
     * 解析字符串字段，支持备用字段名。
     *
     * <p>输入：映射对象、主字段名与备用字段名。
     * <p>输出：字符串值或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String value = resolveString(root, "finalAnswer", "answer");
     * }</pre>
     */
    private String resolveString(Map<String, Object> root, String primary, String fallbackKey) {
        if (root == null) {
            return null;
        }
        Object value = root.get(primary);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        Object fallback = root.get(fallbackKey);
        if (fallback instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    /**
     * 解析浮点字段。
     *
     * <p>输入：映射对象与字段名。
     * <p>输出：浮点值或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * Double value = resolveDouble(root, "confidence");
     * }</pre>
     */
    private Double resolveDouble(Map<String, Object> root, String key) {
        if (root == null || !root.containsKey(key)) {
            return null;
        }
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
     *
     * <p>输入：原始答案文本。
     * <p>输出：清洗后的答案文本。
     * <p>边界：答案为空时返回空字符串。
     * <p>示例：
     * <pre>{@code
     * String answer = sanitizeFinalAnswer(raw);
     * }</pre>
     */
    private String sanitizeFinalAnswer(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = raw.trim();
        String extracted = extractAfterFinalMarker(value);
        if (StringUtils.hasText(extracted)) {
            value = extracted;
        }
        value = removeReasoningLines(value);
        if (!StringUtils.hasText(value) && containsReasoningMarker(raw)) {
            value = extractTailSentence(raw);
        }
        int maxChars = Math.max(0, properties.getMaxFinalAnswerChars());
        if (maxChars > 0 && value.length() > maxChars) {
            value = value.substring(0, maxChars);
        }
        return value.trim();
    }

    /**
     * 从最终答案标记后提取文本。
     *
     * <p>输入：原始文本。
     * <p>输出：提取结果或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String extracted = extractAfterFinalMarker(text);
     * }</pre>
     */
    private String extractAfterFinalMarker(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String marker : FINAL_ANSWER_MARKERS) {
            int index = lower.indexOf(marker);
            if (index < 0) {
                continue;
            }
            int start = index + marker.length();
            String candidate = value.substring(start).trim();
            if (candidate.startsWith(":") || candidate.startsWith("：")) {
                candidate = candidate.substring(1).trim();
            }
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 删除包含推理标记的行。
     *
     * <p>输入：原始文本。
     * <p>输出：过滤后的文本。
     * <p>示例：
     * <pre>{@code
     * String cleaned = removeReasoningLines(text);
     * }</pre>
     */
    private String removeReasoningLines(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String[] lines = value.split("\\r?\\n");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (isReasoningLine(trimmed)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(trimmed);
        }
        return builder.toString().trim();
    }

    /**
     * 判断是否为推理标记行。
     *
     * <p>输入：单行文本。
     * <p>输出：是否为推理行。
     * <p>示例：
     * <pre>{@code
     * boolean match = isReasoningLine(line);
     * }</pre>
     */
    private boolean isReasoningLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String marker : REASONING_MARKERS) {
            if (lower.startsWith(marker)) {
                return true;
            }
            if (lower.contains(marker + ":") || lower.contains(marker + "：")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文本是否包含推理标记。
     *
     * <p>输入：文本内容。
     * <p>输出：是否包含推理标记。
     * <p>示例：
     * <pre>{@code
     * boolean hasMarker = containsReasoningMarker(text);
     * }</pre>
     */
    private boolean containsReasoningMarker(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String marker : REASONING_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取文本末尾的句子作为答案。
     *
     * <p>输入：文本内容。
     * <p>输出：末尾句子或原文。
     * <p>示例：
     * <pre>{@code
     * String tail = extractTailSentence(text);
     * }</pre>
     */
    private String extractTailSentence(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String[] parts = value.split("[。.!?\\n]");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i] == null ? "" : parts[i].trim();
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return value.trim();
    }

    /**
     * 从输入中提取记忆摘要。
     *
     * <p>输入：上下文输入映射。
     * <p>输出：记忆摘要字符串或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String summary = extractMemorySummary(input);
     * }</pre>
     */
    private String extractMemorySummary(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object memoryObj = input.get("memory");
        if (memoryObj instanceof Map<?, ?> memoryMap) {
            Object summary = memoryMap.get("summary");
            if (summary instanceof String text) {
                return truncate(text, MAX_MEMORY_CHARS);
            }
        }
        return null;
    }

    /**
     * 从输入中提取观测信息。
     *
     * <p>输入：上下文输入映射。
     * <p>输出：观测字符串或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String observation = extractObservation(input);
     * }</pre>
     */
    private String extractObservation(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object observations = input.get("observations");
        if (observations != null) {
            return truncate(observations.toString(), MAX_OBSERVATION_CHARS);
        }
        Object lastOutput = input.get("lastStepOutput");
        if (lastOutput != null) {
            return truncate(lastOutput.toString(), MAX_OBSERVATION_CHARS);
        }
        return null;
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
        if (tenantContext == null || workflowId == null || type == null) {
            return;
        }
        long seq = seqCounter != null
                ? seqCounter.incrementAndGet()
                : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload != null ? new HashMap<>(payload) : new HashMap<>());
        eventPublisher.publishEvent(event);
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
        PromptBundle bundle = promptAssembler.build(prompt, null, input);
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

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
 */
@Service
public class ChainOfThoughtService {

    private static final Logger log = LoggerFactory.getLogger(ChainOfThoughtService.class);
    private static final int MAX_QUESTION_CHARS = 500;
    private static final int MAX_MEMORY_CHARS = 800;
    private static final int MAX_OBSERVATION_CHARS = 500;
    private static final int MAX_STEP_SUMMARY_CHARS = 200;
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

    private final ModelInvocationService modelInvocationService;
    private final PromptAssembler promptAssembler;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final CotProperties properties;

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
                if (!decision.valid) {
                    stopReason = normalizeStopReason(decision.stopReason, "invalid_response");
                    completed = false;
                    break;
                }
                if (StringUtils.hasText(decision.finalAnswer)) {
                    finalAnswer = decision.finalAnswer.trim();
                }
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

    private StepDecision parseDecision(String content) {
        if (!StringUtils.hasText(content)) {
            return StepDecision.invalid("empty_response");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
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

    private String normalizeStopReason(String stopReason, String fallback) {
        if (StringUtils.hasText(stopReason)) {
            return stopReason.trim();
        }
        return fallback;
    }

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

    private void applyPromptBundle(ModelRequest request, String prompt, Map<String, Object> input) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, null, input);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }

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

    private record StepDecision(boolean shouldContinue,
                                String stepSummary,
                                String finalAnswer,
                                Double confidence,
                                String stopReason,
                                boolean valid) {

        private static StepDecision invalid(String stopReason) {
            return new StepDecision(false, null, null, 0.3, stopReason, false);
        }
    }
}

package com.example.agent.reflection;

import com.example.agent.auth.TenantContext;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.runtime.StepRequest;
import com.example.agent.repair.JsonOutputRepairService;
import com.example.agent.repair.JsonOutputSchema;
import com.example.agent.model.PromptTrace;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 反思服务，负责评估输出质量并给出重试建议。
 */
@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);
    private static final int SUMMARY_MAX_CHARS = 1000;
    private static final String SUMMARY_TRUNCATED_SUFFIX = "...(truncated)";

    private final ReflectionProperties properties;
    private final MetricsPublisher metricsPublisher;
    private final ModelInvocationService modelInvocationService;
    private final ModelToolResolver modelToolResolver;
    private final PromptAssembler promptAssembler;
    private final ObjectMapper objectMapper;
    private final JsonOutputRepairService jsonOutputRepairService;

    public ReflectionService(ReflectionProperties properties,
                             MetricsPublisher metricsPublisher,
                             ModelInvocationService modelInvocationService,
                             ModelToolResolver modelToolResolver,
                             PromptAssembler promptAssembler,
                             ObjectMapper objectMapper,
                             JsonOutputRepairService jsonOutputRepairService) {
        this.properties = properties;
        this.metricsPublisher = metricsPublisher;
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 进行反思评估，使用默认尝试次数。
     *
     * @param step 步骤请求
     * @param output 输出结果
     * @param tenantContext 租户上下文
     * @return 反思结果
     */
    public ReflectionResult reflect(StepRequest step, Map<String, Object> output, TenantContext tenantContext) {
        return reflect(step, output, tenantContext, 1, null, null);
    }

    /**
     * 进行反思评估。
     *
     * @param step 步骤请求
     * @param output 输出结果
     * @param tenantContext 租户上下文
     * @param attempt 当前尝试次数
     * @return 反思结果
     */
    public ReflectionResult reflect(StepRequest step,
                                    Map<String, Object> output,
                                    TenantContext tenantContext,
                                    int attempt) {
        return reflect(step, output, tenantContext, attempt, null, null);
    }

    /**
     * 带运行上下文的反思入口，用于发布 LLM 事件。
     *
     * @param step 步骤请求
     * @param output 输出结果
     * @param tenantContext 租户上下文
     * @param attempt 当前尝试次数
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 反思结果
     */
    public ReflectionResult reflect(StepRequest step,
                                    Map<String, Object> output,
                                    TenantContext tenantContext,
                                    int attempt,
                                    String workflowId,
                                    AtomicLong seqCounter) {
        if (!properties.isEnabled()) {
            ReflectionReport report = new ReflectionReport(0.9, "反思未启用，跳过评估");
            return new ReflectionResult(false, report);
        }

        if (properties.isLlmEnabled()) {
            ReflectionResult llmResult = tryLlmReflection(step, output, tenantContext, attempt, workflowId, seqCounter);
            if (llmResult != null) {
                return llmResult;
            }
        }

        if (!properties.isFallbackEnabled()) {
            throw new IllegalStateException("reflection_fallback_disabled");
        }
        return heuristicReflection(step, output, tenantContext, attempt);
    }

    private ReflectionResult tryLlmReflection(StepRequest step,
                                              Map<String, Object> output,
                                              TenantContext tenantContext,
                                              int attempt,
                                              String workflowId,
                                              AtomicLong seqCounter) {
        try {
            String prompt = buildReflectionPrompt(step, output, attempt);
            ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.REFLECT);
            applyPromptBundle(modelRequest, prompt, step);
            modelToolResolver.applyTooling(modelRequest, null, step != null ? step.getInput() : null);
            Map<String, Object> metadata = new HashMap<>();
            if (step != null && step.getStepType() != null) {
                metadata.put("stepType", step.getStepType());
            }
            metadata.put("attempt", attempt);
            metadata.put("promptScene", "reflect");
            ModelResponse response = modelInvocationService.invoke(
                    modelRequest,
                    ModelScene.REFLECT,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    "reflect",
                    metadata
            );
            if (response == null || response.getContent() == null) {
                return null;
            }
            ReflectionParsingResult parsed;
            String rawContent = response.getContent();
            boolean repairAttempted = false;
            boolean repairSuccess = false;
            String parseErrorType = null;
            try {
                parsed = parseReflection(rawContent);
            } catch (Exception ex) {
                log.warn("反思解析失败, tenantId={}, stepType={}, reason={}",
                        tenantContext.getTenantId(),
                        step != null ? step.getStepType() : null,
                        ex.getMessage());
                parsed = null;
                parseErrorType = "json_parse_error";
            }
            if (parsed == null) {
                if (parseErrorType == null) {
                    parseErrorType = resolveParseErrorType(rawContent);
                }
                repairAttempted = true;
                parsed = tryRepairReflection(rawContent, step, output, attempt);
                if (parsed != null) {
                    repairSuccess = true;
                }
            }
            if (parsed == null) {
                log.warn("反思修复失败, tenantId={}, stepType={}, attempt={}",
                        tenantContext.getTenantId(),
                        step != null ? step.getStepType() : null,
                        attempt);
                recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                        response != null ? response.getModelId() : null, false, parseErrorType,
                        repairAttempted, repairSuccess);
                return null;
            }
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                    response != null ? response.getModelId() : null, true, null, repairAttempted, repairSuccess);
            boolean retry = parsed.retry && attempt < properties.getMaxRetries();
            if (retry) {
                metricsPublisher.increment("reflection.retry.count");
            }
            log.info("反思完成(LLM), tenantId={}, stepType={}, score={}, retry={}, attempt={}",
                    tenantContext.getTenantId(),
                    step != null ? step.getStepType() : null,
                    parsed.score,
                    retry,
                    attempt);
            return new ReflectionResult(retry, new ReflectionReport(parsed.score, parsed.notes));
        } catch (Exception ex) {
            log.warn("反思解析失败, tenantId={}, stepType={}, reason={}",
                    tenantContext.getTenantId(),
                    step != null ? step.getStepType() : null,
                    ex.getMessage());
            return null;
        }
    }

    private ReflectionResult heuristicReflection(StepRequest step,
                                                 Map<String, Object> output,
                                                 TenantContext tenantContext,
                                                 int attempt) {
        EvaluationResult eval = evaluate(step, output);
        boolean retry = eval.score < properties.getConfidenceThreshold()
                && attempt < properties.getMaxRetries();
        if (retry) {
            metricsPublisher.increment("reflection.retry.count");
        }
        log.info("反思完成(规则), tenantId={}, stepType={}, score={}, retry={}, attempt={}",
                tenantContext.getTenantId(),
                step != null ? step.getStepType() : null,
                eval.score,
                retry,
                attempt);
        return new ReflectionResult(retry, new ReflectionReport(eval.score, eval.notes));
    }

    private String buildReflectionPrompt(StepRequest step, Map<String, Object> output, int attempt) {
        Map<String, Object> context = new HashMap<>();
        context.put("stepType", step != null ? step.getStepType() : null);
        context.put("attempt", attempt);
        context.putAll(buildOutputSummaryContext(output));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
                你是步骤执行的质量审查员（reflection reviewer）。
                你的职责不是回答用户问题，而是基于 REFLECTION_CONTEXT_JSON 审查 steps 的执行质量，并判断是否需要重试。
                
                评分与重试必须严格依据以下规则：
                
                【必须 retry 的情况】
                - steps 为空
                - 任意步骤 status=FAILED
                - 任意步骤没有 output 或 output 为空
                - 步骤输出与 query 明显无关
                - 关键工具未被调用（例如应查询却未查询）
                - 输出只有状态没有结果数据
                
                【不应 retry 的情况】
                - 步骤已成功执行且包含有效结果数据
                - 输出完整但结果为空（例如查询无匹配数据）
                - 步骤逻辑合理，工具调用正确，数据充分
                
                评分标准（score 0~1）：
                - 0.0~0.3：严重错误，必须 retry
                - 0.4~0.6：部分信息缺失，建议 retry
                - 0.7~0.9：执行良好，无需 retry
                - 1.0：步骤完整、结果充分、与 query 高度相关
                
                严格禁止：
                - 禁止尝试回答 query
                - 禁止补充不存在的数据
                - 只能依据 steps 的客观输出来判断
                
                输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
                字段约束：
                1) score: number，必须输出。
                2) retry: boolean，必须输出。
                3) notes: string，必须输出，说明判定原因。
                
                最小示例 JSON：{"score":0.5,"retry":false,"notes":""}
                REFLECTION_CONTEXT_JSON:%s
                """.formatted(contextJson);
    }


    private ReflectionParsingResult parseReflection(String content) throws Exception {
        Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });
        Double score = null;
        if (root.get("score") instanceof Number number) {
            score = number.doubleValue();
        }
        if (score == null) {
            return null;
        }
        boolean retry = root.get("retry") instanceof Boolean value && value;
        String notes = root.get("notes") instanceof String value ? value : "llm_reflection";
        return new ReflectionParsingResult(score, retry, notes);
    }

    private ReflectionParsingResult tryRepairReflection(String rawContent,
                                                        StepRequest step,
                                                        Map<String, Object> output,
                                                        int attempt) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return null;
        }
        Map<String, Object> context = new HashMap<>();
        context.put("stepType", step != null ? step.getStepType() : null);
        context.put("attempt", attempt);
        context.putAll(buildOutputSummaryContext(output));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("reflection", rawContent, JsonOutputSchema.REFLECTION,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        try {
            return parseReflection(repaired);
        } catch (Exception ex) {
            log.warn("反思修复解析失败, reason={}", ex.getMessage());
            return null;
        }
    }

    /**
     * 构建仅包含摘要层的输出上下文，避免注入原始输出。反思统一根据摘要进行
     */
    private Map<String, Object> buildOutputSummaryContext(Map<String, Object> output) {
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> outputSummary = extractMap(output, "outputSummary");
        Map<String, Object> outputDigest = extractMap(output, "outputDigest");
        if (outputSummary == null) {
            outputSummary = new HashMap<>();
        }
        Object summaryValue = outputSummary.get("summary");
        String summaryText = summaryValue == null ? null : summaryValue.toString();
        if (!StringUtils.hasText(summaryText)) {
            summaryText = buildDigestSummary(outputDigest);
            if (!StringUtils.hasText(summaryText)) {
                summaryText = "(summary disabled)";
            }
        }
        if (StringUtils.hasText(summaryText)) {
            outputSummary.put("summary", truncateSummary(summaryText, SUMMARY_MAX_CHARS));
        }
        context.put("outputSummary", outputSummary);
        if (outputDigest != null && !outputDigest.isEmpty()) {
            context.put("outputDigest", outputDigest);
        }
        return context;
    }

    private Map<String, Object> extractMap(Map<String, Object> output, String key) {
        if (output == null || key == null) {
            return null;
        }
        Object value = output.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private String buildDigestSummary(Map<String, Object> digest) {
        if (digest == null || digest.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("digest:");
        appendDigestField(builder, "keyCount", digest.get("keyCount"));
        appendDigestField(builder, "keys", digest.get("keys"));
        Object charCount = digest.get("charCount");
        if (charCount != null) {
            boolean truncated = Boolean.TRUE.equals(digest.get("truncated"));
            String field = truncated ? "charCount<=" : "charCount";
            appendDigestField(builder, field, charCount);
        }
        appendDigestField(builder, "truncated", digest.get("truncated"));
        return builder.toString();
    }

    private void appendDigestField(StringBuilder builder, String field, Object value) {
        if (builder == null || value == null) {
            return;
        }
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ':') {
            builder.append(", ");
        } else {
            builder.append(' ');
        }
        builder.append(field).append('=').append(value);
    }

    private String truncateSummary(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= SUMMARY_TRUNCATED_SUFFIX.length()) {
            return text.substring(0, maxChars);
        }
        int endIndex = maxChars - SUMMARY_TRUNCATED_SUFFIX.length();
        if (endIndex <= 0) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, endIndex) + SUMMARY_TRUNCATED_SUFFIX;
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
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace == null) {
            trace = PromptTrace.fromPrompt("reflect", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "reflect", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "missing_field";
    }

    private void applyPromptBundle(ModelRequest modelRequest, String prompt, StepRequest step) {
        if (promptAssembler == null || modelRequest == null) {
            return;
        }
        Map<String, Object> input = step != null ? step.getInput() : null;
        PromptBundle bundle = promptAssembler.build(prompt, null, input);
        if (bundle != null && bundle.getMessages() != null) {
            modelRequest.setMessages(bundle.getMessages());
        }
    }

    private EvaluationResult evaluate(StepRequest step, Map<String, Object> output) {
        List<String> notes = new ArrayList<>();
        double score = 1.0;

        if (output == null || output.isEmpty()) {
            score -= 0.6;
            notes.add("输出为空");
        }

        int outputLength = output == null ? 0 : output.toString().length();
        if (outputLength < properties.getMinOutputChars()) {
            score -= 0.2;
            notes.add("输出过短");
        }

        if (output != null) {
            for (String key : properties.getRequiredKeys()) {
                if (!output.containsKey(key)) {
                    score -= 0.05;
                    notes.add("缺少字段:" + key);
                }
            }

            String outputText = output.toString().toLowerCase();
            for (String keyword : properties.getFailureKeywords()) {
                if (outputText.contains(keyword.toLowerCase())) {
                    score -= 0.2;
                    notes.add("检测到失败关键词:" + keyword);
                    break;
                }
            }
        }

        if (step != null && step.getInput() != null) {
            Object critical = step.getInput().get("critical");
            if (Boolean.TRUE.equals(critical) && score < 0.8) {
                score -= 0.05;
                notes.add("关键步骤需更高质量");
            }
        }

        if (score < 0) {
            score = 0;
        }
        if (score > 1) {
            score = 1;
        }

        if (notes.isEmpty()) {
            notes.add("输出质量满足要求");
        }
        return new EvaluationResult(score, String.join("，", notes));
    }

    private record EvaluationResult(double score, String notes) {
    }

    private record ReflectionParsingResult(double score, boolean retry, String notes) {
    }
}

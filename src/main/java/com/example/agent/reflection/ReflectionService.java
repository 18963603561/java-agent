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

/**
 * 反思服务，负责评估输出质量并给出重试建议。
 */
@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    private final ReflectionProperties properties;
    private final MetricsPublisher metricsPublisher;
    private final ModelInvocationService modelInvocationService;
    private final ModelToolResolver modelToolResolver;
    private final PromptAssembler promptAssembler;
    private final ObjectMapper objectMapper;

    public ReflectionService(ReflectionProperties properties,
                             MetricsPublisher metricsPublisher,
                             ModelInvocationService modelInvocationService,
                             ModelToolResolver modelToolResolver,
                             PromptAssembler promptAssembler,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.metricsPublisher = metricsPublisher;
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
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
            ReflectionParsingResult parsed = parseReflection(response.getContent());
            if (parsed == null) {
                return null;
            }
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
        context.put("output", output);
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
                你是质量审查员，请对步骤输出进行评分并判断是否需要重试。
                输出要求：仅输出 JSON，字段包含 score(0-1)、retry、notes。
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

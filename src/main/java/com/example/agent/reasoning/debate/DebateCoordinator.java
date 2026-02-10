package com.example.agent.reasoning.debate;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.reasoning.common.JsonPayloadNormalizer;
import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.ReasoningParseSupport;
import com.example.agent.reasoning.common.ReasoningStrategy;
import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import com.example.agent.reasoning.common.result.DebatePayload;
import com.example.agent.reasoning.common.telemetry.ReasoningEventPublisher;
import com.example.agent.reasoning.common.telemetry.ReasoningMetricsPublisher;
import com.example.agent.reasoning.common.telemetry.ReasoningTraceRecorder;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 辩论协调器，负责组织辩论提示词、解析模型输出并发布轮次事件。
 */
@Service
public class DebateCoordinator implements ReasoningStrategy {

    private static final Logger log = LoggerFactory.getLogger(DebateCoordinator.class);

    private final int maxConclusionChars;

    private final ModelInvocationService modelInvocationService;
    private final PromptAssembler promptAssembler;
    private final ObjectMapper objectMapper;
    private final JsonOutputRepairService jsonOutputRepairService;
    private final JsonPayloadNormalizer jsonPayloadNormalizer;
    private final ReasoningParseSupport reasoningParseSupport;
    private final ReasoningEventPublisher reasoningEventPublisher;
    private final ReasoningTraceRecorder reasoningTraceRecorder;
    private final ReasoningMetricsPublisher reasoningMetricsPublisher;

    /**
     * 构造辩论协调器。
     *
     * @param modelInvocationService 模型调用服务
     * @param promptAssembler 提示词装配器
     * @param objectMapper JSON 序列化工具
     * @param jsonOutputRepairService JSON 修复服务
     */
    public DebateCoordinator(ModelInvocationService modelInvocationService,
                             PromptAssembler promptAssembler,
                             ObjectMapper objectMapper,
                             JsonOutputRepairService jsonOutputRepairService,
                             JsonPayloadNormalizer jsonPayloadNormalizer,
                             ReasoningParseSupport reasoningParseSupport,
                             ReasoningEventPublisher reasoningEventPublisher,
                             ReasoningTraceRecorder reasoningTraceRecorder,
                             ReasoningMetricsPublisher reasoningMetricsPublisher,
                             ReasoningConfigResolver reasoningConfigResolver) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
        this.jsonPayloadNormalizer = jsonPayloadNormalizer;
        this.reasoningParseSupport = reasoningParseSupport;
        this.reasoningEventPublisher = reasoningEventPublisher;
        this.reasoningTraceRecorder = reasoningTraceRecorder;
        this.reasoningMetricsPublisher = reasoningMetricsPublisher;
        this.maxConclusionChars = reasoningConfigResolver.resolveDebateMaxConclusionChars();
    }

    /**
     * 执行一次辩论并返回结构化结论。
     *
     * @param topic 辩论主题
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 辩论轮次结果
     */
    public DebateRound debate(String topic,
                              TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter) {
        long startedAt = System.currentTimeMillis();
        log.info("辩论开始, topic={}, workflowId={}", topic, workflowId);

        String prompt = buildPrompt(topic);
        ModelRequest request = new ModelRequest(prompt, ModelScene.REFLECT);
        applyPromptBundle(request, prompt);

        Map<String, Object> metadata = new HashMap<>();
        if (StringUtils.hasText(topic)) {
            metadata.put("topic", topic);
        }
        metadata.put("promptScene", "debate");

        ModelResponse response = modelInvocationService.invoke(
                request,
                ModelScene.REFLECT,
                tenantContext,
                workflowId,
                seqCounter,
                "debate",
                metadata
        );

        String rawContent = response != null ? response.getContent() : null;
        ParseResult parseResult = parseConclusion(rawContent);
        boolean repairAttempted = false;
        boolean repairSuccess = false;
        String parseErrorType = null;

        String conclusion = parseResult.conclusion;
        if (!parseResult.success && StringUtils.hasText(rawContent)) {
            parseErrorType = resolveParseErrorType(rawContent);
            repairAttempted = true;
            String repairedConclusion = tryRepairConclusion(rawContent, topic);
            if (StringUtils.hasText(repairedConclusion)) {
                conclusion = repairedConclusion;
                repairSuccess = true;
            }
        }
        reasoningMetricsPublisher.recordParseResult("debate", parseResult.success || repairSuccess, parseErrorType);
        reasoningMetricsPublisher.recordRepairResult("debate", repairAttempted, repairSuccess);

        conclusion = sanitizeConclusion(conclusion);
        if (!StringUtils.hasText(conclusion)) {
            log.warn("辩论结论为空, topic={}, workflowId={}, parseErrorType={}, repairAttempted={}, repairSuccess={}",
                    topic, workflowId, parseErrorType, repairAttempted, repairSuccess);
            conclusion = "结论不足";
        }

        recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                response != null ? response.getModelId() : null,
                StringUtils.hasText(conclusion),
                parseErrorType,
                repairAttempted,
                repairSuccess);

        DebateRound round = new DebateRound();
        round.setRoundId(UUID.randomUUID().toString());
        round.setTopic(topic);
        round.setConclusion(conclusion);
        if (response != null && StringUtils.hasText(response.getRawRef())) {
            round.setRawRef(response.getRawRef());
        }

        publishDebateEvent(tenantContext, workflowId, seqCounter, round);
        log.info("辩论完成, topic={}, workflowId={}, conclusionLength={}",
                topic, workflowId, round.getConclusion().length());
        reasoningMetricsPublisher.recordDuration("debate", System.currentTimeMillis() - startedAt);
        return round;
    }

    @Override
    public boolean supports(String strategyType) {
        return StringUtils.hasText(strategyType) && "DEBATE".equalsIgnoreCase(strategyType);
    }

    @Override
    public ReasoningResult execute(ReasoningRequest request) {
        DebateRound round = debate(
                request != null ? request.getPrompt() : null,
                request != null ? request.getTenantContext() : null,
                request != null ? request.getWorkflowId() : null,
                request != null ? request.getSeqCounter() : null
        );
        DebatePayload payload = new DebatePayload(round.getRoundId(), round.getTopic(), round.getConclusion());
        return new ReasoningResult(
                "DEBATE",
                round.getConclusion(),
                0.6,
                "completed",
                "COMPLETED",
                round.getRawRef(),
                payload
        );
    }

    /**
     * 构建辩论提示词。
     *
     * <p>要求模型只返回一个 JSON 对象，字段必须包含 {@code conclusion}。
     */
    private String buildPrompt(String topic) {
        Map<String, Object> context = new HashMap<>();
        context.put("topic", topic);
        String json;
        try {
            json = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            json = "{}";
        }
        return """
            你是辩论主持人。请基于 DEBATE_CONTEXT_JSON 输出一个可执行的最终结论。

            【结论要求】
            1) 必须做出明确裁决，不允许模糊表述。
            2) 必须包含关键依据的高度概括，避免复述完整推理过程。
            3) 必须指出主要前提或风险点，保持结论可落地。

            【输出格式】
            - 只允许输出单个 JSON 对象。
            - 禁止输出额外文本、Markdown、代码块。
            - JSON 必须包含字段：conclusion（string）。

            最小示例：{"conclusion":""}

            DEBATE_CONTEXT_JSON:%s
            """.formatted(json);
    }

    /**
     * 尝试使用修复服务恢复结论字段。
     */
    private String tryRepairConclusion(String rawContent, String topic) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return null;
        }
        String contextJson;
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("topic", topic);
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }

        String repaired = jsonOutputRepairService.repair(
                "debate",
                rawContent,
                JsonOutputSchema.DEBATE,
                contextJson,
                1
        );
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        ParseResult parseResult = parseConclusion(repaired);
        if (!parseResult.success) {
            return null;
        }
        return parseResult.conclusion;
    }

    /**
     * 记录提示词追踪信息，便于定位解析与修复质量。
     */
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
                "debate",
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

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
    }

    /**
     * 解析模型结论。
     *
     * <p>仅接受 JSON 对象中的 {@code conclusion} 字段。
     */
    private ParseResult parseConclusion(String content) {
        if (!StringUtils.hasText(content)) {
            return ParseResult.failure();
        }
        try {
            String normalized = jsonPayloadNormalizer.normalize(content);
            if (!StringUtils.hasText(normalized)) {
                return ParseResult.failure();
            }
            Map<String, Object> root = objectMapper.readValue(normalized, Map.class);
            String text = reasoningParseSupport.resolveString(root, "conclusion", null);
            if (StringUtils.hasText(text)) {
                return ParseResult.success(text.trim());
            }
            return ParseResult.failure();
        } catch (Exception ex) {
            return ParseResult.failure();
        }
    }

    /**
     * 清洗并截断结论文本，防止噪声外泄。
     */
    private String sanitizeConclusion(String conclusion) {
        if (!StringUtils.hasText(conclusion)) {
            return "";
        }
        String normalized = conclusion.trim();
        normalized = normalized.replaceAll("\\s+", " ");
        if (normalized.length() > maxConclusionChars) {
            normalized = normalized.substring(0, maxConclusionChars);
        }
        return normalized.trim();
    }

    /**
     * 发布辩论完成事件。
     */
    private void publishDebateEvent(TenantContext tenantContext,
                                    String workflowId,
                                    AtomicLong seqCounter,
                                    DebateRound round) {
        if (round == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("roundId", round.getRoundId());
        if (StringUtils.hasText(round.getTopic())) {
            payload.put("topic", round.getTopic());
        }
        payload.put("conclusion", round.getConclusion());
        if (StringUtils.hasText(round.getRawRef())) {
            payload.put("rawRef", round.getRawRef());
        }
        reasoningEventPublisher.publishStrategyEvent(
                tenantContext,
                workflowId,
                seqCounter,
                EventType.DEBATE_ROUND_COMPLETED,
                "debate",
                payload
        );
    }

    private void applyPromptBundle(ModelRequest request, String prompt) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, LlmTaskContext.empty(), null);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }

    /**
     * 解析结果对象。
     */
    private record ParseResult(boolean success, String conclusion) {

        private static ParseResult success(String conclusion) {
            return new ParseResult(true, conclusion);
        }

        private static ParseResult failure() {
            return new ParseResult(false, null);
        }
    }
}

package com.example.agent.reasoning.debate;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.capabilities.llm.ModelInvocationService;
import com.example.agent.capabilities.llm.ModelRequest;
import com.example.agent.capabilities.llm.ModelResponse;
import com.example.agent.capabilities.llm.ModelScene;
import com.example.agent.capabilities.llm.PromptAssembler;
import com.example.agent.capabilities.llm.PromptBundle;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.llm.PromptTrace;
import com.example.agent.streaming.sse.EventStreamService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 辩论协调器，负责辩论流程控制。
 */
@Service
public class DebateCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DebateCoordinator.class);

    private final ModelInvocationService modelInvocationService;
    private final PromptAssembler promptAssembler;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final JsonOutputRepairService jsonOutputRepairService;

    public DebateCoordinator(ModelInvocationService modelInvocationService,
                             PromptAssembler promptAssembler,
                             ObjectMapper objectMapper,
                             ApplicationEventPublisher eventPublisher,
                             EventStreamService eventStreamService,
                             JsonOutputRepairService jsonOutputRepairService) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 执行辩论。
     *
     * @param topic 主题
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 辩论轮次结果
     */
    public DebateRound debate(String topic,
                              TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter) {
        String prompt = buildPrompt(topic);
        ModelRequest request = new ModelRequest(prompt, ModelScene.REFLECT);
        applyPromptBundle(request, prompt);
        Map<String, Object> metadata = new HashMap<>();
        if (topic != null) {
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
        boolean repairAttempted = false;
        boolean repairSuccess = false;
        String parseErrorType = null;
        String conclusion = parseConclusion(rawContent);
        String repairedConclusion = null;
        if (StringUtils.hasText(rawContent)
                && ("no_conclusion".equals(conclusion) || rawContent.equals(conclusion))) {
            parseErrorType = resolveParseErrorType(rawContent);
            repairAttempted = true;
            repairedConclusion = tryRepairConclusion(rawContent, topic);
            if (StringUtils.hasText(repairedConclusion)) {
                repairSuccess = true;
            }
        }
        if (StringUtils.hasText(repairedConclusion)) {
            conclusion = repairedConclusion;
        } else if (!StringUtils.hasText(conclusion)) {
            log.warn("辩论结论修复失败, topic={}", topic);
        }
        recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                response != null ? response.getModelId() : null,
                !"no_conclusion".equals(conclusion) && StringUtils.hasText(conclusion),
                parseErrorType, repairAttempted, repairSuccess);
        DebateRound round = new DebateRound();
        round.setRoundId(UUID.randomUUID().toString());
        round.setTopic(topic);
        round.setConclusion(conclusion);
        if (response != null && StringUtils.hasText(response.getRawRef())) {
            round.setRawRef(response.getRawRef());
        }
        publishDebateEvent(tenantContext, workflowId, seqCounter, round);
        log.info("辩论完成, topic={}, conclusion={}", topic, round.getConclusion());
        return round;
    }

    /**
     * 兼容旧入口。
     *
     * @param topic 主题
     * @return 辩论结果
     */
    public DebateRound debate(String topic) {
        log.info("辩论开始, topic={}", topic);
        DebateRound round = new DebateRound();
        round.setRoundId(UUID.randomUUID().toString());
        round.setTopic(topic);
        round.setConclusion("pending");
        return round;
    }

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
            你是辩论主持人（debate moderator）。你的任务是基于 DEBATE_CONTEXT_JSON 中的辩论内容，输出一段可执行、可落地的辩论结论。
            
            【结论要求】
            1) 必须做出明确裁决：给出“推荐方案/最终立场/折中方案”，避免仅说“各有道理”。
            2) 必须包含关键依据（简短列点即可）：说明为什么选择该结论，提炼 2~4 个最有力的理由。
            3) 必须指出主要风险/前提：用 1~2 句说明结论成立的条件或需要注意的风险。
            4) 禁止逐字复述辩论过程与长引用；只允许高度概括。
            
            【输出要求】
            输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
            
            字段约束：
            1) conclusion: string，必须输出，缺信息填空串。
            
            最小示例 JSON：{"conclusion":""}
            
            DEBATE_CONTEXT_JSON:%s
            """.formatted(json);

    }

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
        String repaired = jsonOutputRepairService.repair("debate", rawContent, JsonOutputSchema.DEBATE,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        String parsed = parseConclusion(repaired);
        if (StringUtils.hasText(parsed)) {
            return parsed;
        }
        return null;
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
            trace = PromptTrace.fromPrompt("debate", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "debate", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
    }

    private String parseConclusion(String content) {
        if (content == null || content.isBlank()) {
            return "no_conclusion";
        }
        try {
            Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            if (root.get("conclusion") instanceof String value) {
                return value;
            }
        } catch (Exception ex) {
            return content;
        }
        return content;
    }

    private void publishDebateEvent(TenantContext tenantContext,
                                    String workflowId,
                                    AtomicLong seqCounter,
                                    DebateRound round) {
        if (tenantContext == null || workflowId == null || round == null) {
            return;
        }
        long seq = seqCounter != null
                ? seqCounter.incrementAndGet()
                : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(EventType.DEBATE_ROUND_COMPLETED);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("roundId", round.getRoundId());
        if (round.getTopic() != null) {
            payload.put("topic", round.getTopic());
        }
        if (round.getConclusion() != null) {
            payload.put("conclusion", round.getConclusion());
        }
        if (StringUtils.hasText(round.getRawRef())) {
            payload.put("rawRef", round.getRawRef());
        }
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }

    private void applyPromptBundle(ModelRequest request, String prompt) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, null, null);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }
}

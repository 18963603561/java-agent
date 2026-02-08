package com.example.agent.reasoning.debate;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
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
 * 杈╄鍗忚皟鍣紝璐熻矗杈╄娴佺▼鎺у埗銆?
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
     * 鎵ц杈╄銆?
     *
     * @param topic 涓婚
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param workflowId 宸ヤ綔娴佹爣璇?
     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?
     * @return 杈╄杞缁撴灉
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
            log.warn("杈╄缁撹淇澶辫触, topic={}", topic);
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
        log.info("杈╄瀹屾垚, topic={}, conclusion={}", topic, round.getConclusion());
        return round;
    }

    /**
     * 鍏煎鏃у叆鍙ｃ€?
     *
     * @param topic 涓婚
     * @return 杈╄缁撴灉
     */
    public DebateRound debate(String topic) {
        log.info("杈╄寮€濮? topic={}", topic);
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
            浣犳槸杈╄涓绘寔浜猴紙debate moderator锛夈€備綘鐨勪换鍔℃槸鍩轰簬 DEBATE_CONTEXT_JSON 涓殑杈╄鍐呭锛岃緭鍑轰竴娈靛彲鎵ц銆佸彲钀藉湴鐨勮京璁虹粨璁恒€?
            
            銆愮粨璁鸿姹傘€?
            1) 蹇呴』鍋氬嚭鏄庣‘瑁佸喅锛氱粰鍑衡€滄帹鑽愭柟妗?鏈€缁堢珛鍦?鎶樹腑鏂规鈥濓紝閬垮厤浠呰鈥滃悇鏈夐亾鐞嗏€濄€?
            2) 蹇呴』鍖呭惈鍏抽敭渚濇嵁锛堢畝鐭垪鐐瑰嵆鍙級锛氳鏄庝负浠€涔堥€夋嫨璇ョ粨璁猴紝鎻愮偧 2~4 涓渶鏈夊姏鐨勭悊鐢便€?
            3) 蹇呴』鎸囧嚭涓昏椋庨櫓/鍓嶆彁锛氱敤 1~2 鍙ヨ鏄庣粨璁烘垚绔嬬殑鏉′欢鎴栭渶瑕佹敞鎰忕殑椋庨櫓銆?
            4) 绂佹閫愬瓧澶嶈堪杈╄杩囩▼涓庨暱寮曠敤锛涘彧鍏佽楂樺害姒傛嫭銆?
            
            銆愯緭鍑鸿姹傘€?
            杈撳嚭蹇呴』鏄崟涓?JSON 瀵硅薄锛屼笉鍏佽浠讳綍棰濆鏂囨湰锛屼笉鍏佽 Markdown/浠ｇ爜鍧椼€?
            
            瀛楁绾︽潫锛?
            1) conclusion: string锛屽繀椤昏緭鍑猴紝缂轰俊鎭～绌轰覆銆?
            
            鏈€灏忕ず渚?JSON锛歿"conclusion":""}
            
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


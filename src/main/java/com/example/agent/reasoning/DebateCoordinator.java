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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

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

    public DebateCoordinator(ModelInvocationService modelInvocationService,
                             PromptAssembler promptAssembler,
                             ObjectMapper objectMapper,
                             ApplicationEventPublisher eventPublisher,
                             EventStreamService eventStreamService) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
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
        ModelResponse response = modelInvocationService.invoke(
                request,
                ModelScene.REFLECT,
                tenantContext,
                workflowId,
                seqCounter,
                "debate",
                metadata
        );
        DebateRound round = new DebateRound();
        round.setRoundId(UUID.randomUUID().toString());
        round.setTopic(topic);
        round.setConclusion(parseConclusion(response != null ? response.getContent() : null));
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
                你是辩论主持人，请给出辩论结论。
                输出要求：仅输出 JSON，字段包含 conclusion。
                DEBATE_CONTEXT_JSON:%s
                """.formatted(json);
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

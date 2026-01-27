package com.example.agent.research;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.streaming.EventStreamService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 深度研究流程，负责组织检索与引用输出。
 */
@Service
public class ResearchPipeline {

    private static final Logger log = LoggerFactory.getLogger(ResearchPipeline.class);

    private final ModelInvocationService modelInvocationService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;

    public ResearchPipeline(ModelInvocationService modelInvocationService,
                            ObjectMapper objectMapper,
                            ApplicationEventPublisher eventPublisher,
                            EventStreamService eventStreamService) {
        this.modelInvocationService = modelInvocationService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
    }

    /**
     * 执行研究流程。
     *
     * @param query 查询问题
     * @return 引用列表
     */
    public List<ResearchCitation> run(String query) {
        log.info("研究流程启动, queryLength={}", query == null ? 0 : query.length());
        return List.of();
    }

    /**
     * 带运行上下文的研究入口，用于发布事件。
     *
     * @param query 查询问题
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 引用列表
     */
    public List<ResearchCitation> run(String query,
                                      TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter) {
        String prompt = buildPrompt(query);
        ModelRequest request = new ModelRequest(prompt, ModelScene.RESEARCH);
        Map<String, Object> metadata = new HashMap<>();
        if (query != null) {
            metadata.put("query", query);
        }
        ModelResponse response = modelInvocationService.invoke(
                request,
                ModelScene.RESEARCH,
                tenantContext,
                workflowId,
                seqCounter,
                "research",
                metadata
        );
        List<ResearchCitation> citations = parseCitations(response != null ? response.getContent() : null);
        if (citations.isEmpty()) {
            citations = buildFallbackCitations(query);
        }
        publishCitationEvents(tenantContext, workflowId, seqCounter, citations);
        log.info("研究流程完成, citations={}", citations.size());
        return citations;
    }

    private String buildPrompt(String query) {
        Map<String, Object> context = new HashMap<>();
        context.put("query", query);
        String json;
        try {
            json = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            json = "{}";
        }
        return """
                你是研究助手，请输出研究引用列表。
                输出要求：仅输出 JSON，字段包含 citations 列表。
                RESEARCH_CONTEXT_JSON:%s
                """.formatted(json);
    }

    private List<ResearchCitation> parseCitations(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            Object citationsObj = root.get("citations");
            if (!(citationsObj instanceof List<?> list)) {
                return List.of();
            }
            List<ResearchCitation> citations = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                ResearchCitation citation = new ResearchCitation();
                citation.setSource(map.get("source") instanceof String value ? value : "unknown");
                citation.setSnippet(map.get("snippet") instanceof String value ? value : "");
                citation.setFetchedAt(Instant.now());
                citations.add(citation);
            }
            return citations;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<ResearchCitation> buildFallbackCitations(String query) {
        ResearchCitation citation = new ResearchCitation();
        citation.setSource("local");
        citation.setSnippet(query == null ? "" : query);
        citation.setFetchedAt(Instant.now());
        return List.of(citation);
    }

    private void publishCitationEvents(TenantContext tenantContext,
                                       String workflowId,
                                       AtomicLong seqCounter,
                                       List<ResearchCitation> citations) {
        if (tenantContext == null || workflowId == null) {
            return;
        }
        for (ResearchCitation citation : citations) {
            long seq = seqCounter != null
                    ? seqCounter.incrementAndGet()
                    : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
            StreamEvent event = new StreamEvent();
            event.setEventId(workflowId + ":" + seq);
            event.setSchemaVersion("v1");
            event.setWorkflowId(workflowId);
            event.setType(EventType.RESEARCH_SOURCE_ADDED);
            event.setTimestamp(Instant.now());
            event.setSeq(seq);
            event.setStreamId(workflowId);
            event.setTenantId(tenantContext.getTenantId());
            Map<String, Object> payload = new HashMap<>();
            if (citation.getSource() != null) {
                payload.put("source", citation.getSource());
            }
            if (citation.getSnippet() != null) {
                payload.put("snippet", citation.getSnippet());
            }
            event.setPayload(payload);
            eventPublisher.publishEvent(event);
        }
    }
}

package com.example.agent.research;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.repair.JsonOutputRepairService;
import com.example.agent.repair.JsonOutputSchema;
import com.example.agent.model.PromptTrace;
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
import org.springframework.util.StringUtils;

/**
 * 深度研究流程，负责组织检索与引用输出。
 */
@Service
public class ResearchPipeline {

    private static final Logger log = LoggerFactory.getLogger(ResearchPipeline.class);

    private final ModelInvocationService modelInvocationService;
    private final PromptAssembler promptAssembler;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final JsonOutputRepairService jsonOutputRepairService;

    public ResearchPipeline(ModelInvocationService modelInvocationService,
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
        return runWithRawRef(query, tenantContext, workflowId, seqCounter).getCitations();
    }

    /**
     * 带原始引用返回的研究入口。
     *
     * @param query 查询问题
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 研究执行结果
     */
    public ResearchRunResult runWithRawRef(String query,
                                           TenantContext tenantContext,
                                           String workflowId,
                                           AtomicLong seqCounter) {
        String prompt = buildPrompt(query);
        ModelRequest request = new ModelRequest(prompt, ModelScene.RESEARCH);
        applyPromptBundle(request, prompt);
        Map<String, Object> metadata = new HashMap<>();
        if (query != null) {
            metadata.put("query", query);
        }
        metadata.put("promptScene", "research");
        ModelResponse response = modelInvocationService.invoke(
                request,
                ModelScene.RESEARCH,
                tenantContext,
                workflowId,
                seqCounter,
                "research",
                metadata
        );
        String rawContent = response != null ? response.getContent() : null;
        String rawRef = response != null ? response.getRawRef() : null;
        boolean repairAttempted = false;
        boolean repairSuccess = false;
        String parseErrorType = null;
        List<ResearchCitation> citations = parseCitations(rawContent);
        if (citations.isEmpty()) {
            parseErrorType = resolveParseErrorType(rawContent);
            repairAttempted = true;
            List<ResearchCitation> repaired = tryRepairCitations(rawContent, query);
            if (!repaired.isEmpty()) {
                citations = repaired;
                repairSuccess = true;
            }
        }
        if (citations.isEmpty()) {
            log.warn("研究引用修复失败, queryLength={}", query == null ? 0 : query.length());
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                    response != null ? response.getModelId() : null, false, parseErrorType,
                    repairAttempted, repairSuccess);
            citations = buildFallbackCitations(query);
        } else {
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                    response != null ? response.getModelId() : null, true, null, repairAttempted, repairSuccess);
        }
        publishCitationEvents(tenantContext, workflowId, seqCounter, citations);
        log.info("研究流程完成, citations={}", citations.size());
        return new ResearchRunResult(citations, rawRef);
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
            你是研究助手（research citation extractor）。
            你的任务：从 RESEARCH_CONTEXT_JSON 中提取“可追溯的研究引用（citations）”列表，用于审计与回放。
            
            【引用定义】
            - citation.source：必须是可定位的来源标识，例如 URL、文档标题+站点、论文标题+作者+年份等；如果上下文没有任何来源信息，则 source 允许为空串，但必须在 snippet 中说明“no_source_provided”。
            - citation.snippet：必须是与 query 相关的证据片段/要点摘要（不是长段原文），控制在 1~2 句，<= 200 字符。
            
            【质量规则】
            1) 只从上下文中“已经出现/已提供”的来源提取，禁止编造来源或杜撰 URL。
            2) 去重：相同 source 只保留一次；若同一 source 有多段证据，合并为更精炼的 snippet。
            3) 排序：按与 query 的相关性从高到低；同等相关则按时间新→旧（若上下文提供时间信息）。
            4) 数量控制：最多输出 10 条；不足则按实际输出。
            5) 合规：snippet 不得复制大段原文，不得超过 25 个英文词或 200 字符（以更严格者为准）；可用转述/摘要。
            
            【输出要求】
            输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
            
            字段约束：
            1) citations: array，必须输出，缺信息填 []。
            2) citations[*].source: string，可输出空串。
            3) citations[*].snippet: string，可输出空串。
            
            最小示例 JSON：{"citations":[]}
            
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

    private List<ResearchCitation> tryRepairCitations(String rawContent, String query) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return List.of();
        }
        String contextJson;
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("query", query);
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("research", rawContent, JsonOutputSchema.RESEARCH,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return List.of();
        }
        return parseCitations(repaired);
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
            trace = PromptTrace.fromPrompt("research", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "research", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
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

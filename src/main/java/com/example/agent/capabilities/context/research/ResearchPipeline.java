package com.example.agent.capabilities.context.research;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.context.runtime.ContextRuntimeKeys;
import com.example.agent.streaming.sse.EventStreamService;
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
 * 研究流水线，负责调用模型产出研究引用并在失败时执行修复与兜底。
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

    /**
     * 构造研究流水线。
     *
     * @param modelInvocationService 模型调用服务
     * @param promptAssembler 提示词装配器
     * @param objectMapper JSON 序列化器
     * @param eventPublisher 事件发布器
     * @param eventStreamService 流事件序号服务
     * @param jsonOutputRepairService JSON 修复服务
     */
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
     * 执行研究并返回引用列表。
     *
     * @param query 研究查询
     * @return 引用列表
     */
    public List<ResearchCitation> run(String query) {
        return runWithRawRef(query, null, null, null).getCitations();
    }

    /**
     * 在租户与工作流上下文下执行研究。
     *
     * @param query 研究查询
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 流序号计数器
     * @return 引用列表
     */
    public List<ResearchCitation> run(String query,
                                      TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter) {
        return runWithRawRef(query, tenantContext, workflowId, seqCounter).getCitations();
    }

    /**
     * 执行研究并返回引用列表与模型原始引用。
     *
     * @param query 研究查询
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 流序号计数器
     * @return 研究结果
     */
    public ResearchRunResult runWithRawRef(String query,
                                           TenantContext tenantContext,
                                           String workflowId,
                                           AtomicLong seqCounter) {
        int queryLength = query == null ? 0 : query.length();
        log.info("研究流水线开始, workflowId={}, queryLength={}", workflowId, queryLength);
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

        ParseOutcome parseOutcome = resolveCitations(rawContent, query, workflowId, queryLength);
        List<ResearchCitation> citations = parseOutcome.getCitations();
        boolean parseSuccess = !citations.isEmpty();
        String modelId = response != null ? response.getModelId() : null;

        if (!parseSuccess) {
            log.warn("研究流水线解析失败并进入兜底, workflowId={}, queryLength={}, parseErrorType={}, repairAttempted={}, repairSuccess={}",
                    workflowId,
                    queryLength,
                    parseOutcome.getParseErrorType(),
                    parseOutcome.isRepairAttempted(),
                    parseOutcome.isRepairSuccess());
            recordPromptTrace(metadata,
                    prompt,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    modelId,
                    false,
                    parseOutcome.getParseErrorType(),
                    parseOutcome.isRepairAttempted(),
                    parseOutcome.isRepairSuccess());
            citations = buildFallbackCitations(query);
        } else {
            recordPromptTrace(metadata,
                    prompt,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    modelId,
                    true,
                    null,
                    parseOutcome.isRepairAttempted(),
                    parseOutcome.isRepairSuccess());
        }

        publishCitationEvents(tenantContext, workflowId, seqCounter, citations);
        log.info("研究流水线完成, workflowId={}, queryLength={}, citations={}, parseSuccess={}",
                workflowId,
                queryLength,
                citations.size(),
                parseSuccess);
        return new ResearchRunResult(citations, rawRef);
    }

    /**
     * 构建研究提示词。
     *
     * @param query 研究查询
     * @return 提示词文本
     */
    private String buildPrompt(String query) {
        Map<String, Object> context = new HashMap<>();
        context.put("query", query);
        String json;
        try {
            json = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            log.warn("研究提示词上下文序列化失败, workflowId={}, queryLength={}",
                    null,
                    query == null ? 0 : query.length(),
                    ex);
            json = "{}";
        }
        return """
            你是一个研究引用抽取器。
            你会从 RESEARCH_CONTEXT_JSON 中读取查询，并返回 citations 数组。

            输出要求：
            - citation.source 使用可靠来源名称或 URL，无法确定时写 unknown。
            - citation.snippet 为与 query 最相关的简短证据片段。
            - 最多返回 10 条，优先高可信来源。

            输出必须是 JSON，不要输出 Markdown 或额外说明。
            JSON 结构：
            1) citations: array
            2) citations[*].source: string
            3) citations[*].snippet: string

            允许空结果：{"citations":[]}

            RESEARCH_CONTEXT_JSON:%s
            """.formatted(json);
    }

    /**
     * 解析研究引用并在失败时尝试结构修复。
     *
     * @param rawContent 模型原始输出
     * @param query 检索查询
     * @param workflowId 工作流标识
     * @param queryLength 查询长度
     * @return 解析结果
     */
    private ParseOutcome resolveCitations(String rawContent,
                                          String query,
                                          String workflowId,
                                          int queryLength) {
        List<ResearchCitation> parsed = parseCitations(rawContent, workflowId, queryLength, "model_output");
        if (!parsed.isEmpty()) {
            return new ParseOutcome(parsed, null, false, false);
        }
        String parseErrorType = resolveParseErrorType(rawContent);
        List<ResearchCitation> repaired = tryRepairCitations(rawContent, query, workflowId, queryLength);
        if (!repaired.isEmpty()) {
            return new ParseOutcome(repaired, parseErrorType, true, true);
        }
        return new ParseOutcome(List.of(), parseErrorType, true, false);
    }

    /**
     * 解析引用 JSON。
     *
     * @param content 待解析内容
     * @param workflowId 工作流标识
     * @param queryLength 查询长度
     * @param stage 当前阶段
     * @return 引用列表
     */
    private List<ResearchCitation> parseCitations(String content,
                                                  String workflowId,
                                                  int queryLength,
                                                  String stage) {
        if (!StringUtils.hasText(content)) {
            log.warn("研究引用解析输入为空, workflowId={}, stage={}, queryLength={}", workflowId, stage, queryLength);
            return List.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            Object citationsObj = root.get(ContextRuntimeKeys.CITATIONS);
            if (!(citationsObj instanceof List<?> list)) {
                log.warn("研究引用解析缺少 citations 字段, workflowId={}, stage={}, queryLength={}",
                        workflowId,
                        stage,
                        queryLength);
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
            log.warn("研究引用解析异常, workflowId={}, stage={}, queryLength={}", workflowId, stage, queryLength, ex);
            return List.of();
        }
    }

    /**
     * 尝试修复非结构化输出。
     *
     * @param rawContent 原始输出
     * @param query 查询
     * @param workflowId 工作流标识
     * @param queryLength 查询长度
     * @return 修复后的引用列表
     */
    private List<ResearchCitation> tryRepairCitations(String rawContent,
                                                      String query,
                                                      String workflowId,
                                                      int queryLength) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return List.of();
        }
        String contextJson;
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("query", query);
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            log.warn("研究引用修复上下文序列化失败, workflowId={}, queryLength={}", workflowId, queryLength, ex);
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("research", rawContent, JsonOutputSchema.RESEARCH,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            log.warn("研究引用修复未返回有效内容, workflowId={}, queryLength={}", workflowId, queryLength);
            return List.of();
        }
        return parseCitations(repaired, workflowId, queryLength, "repair_output");
    }

    /**
     * 记录提示词追踪信息。
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

    /**
     * 解析错误类型。
     *
     * @param rawContent 原始输出
     * @return 错误类型
     */
    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
    }

    /**
     * 构建兜底引用，保证输出结构稳定。
     *
     * @param query 查询文本
     * @return 兜底引用
     */
    private List<ResearchCitation> buildFallbackCitations(String query) {
        ResearchCitation citation = new ResearchCitation();
        citation.setSource("local");
        citation.setSnippet(query == null ? "" : query);
        citation.setFetchedAt(Instant.now());
        return List.of(citation);
    }

    /**
     * 发布研究引用新增事件。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 序号计数器
     * @param citations 引用列表
     */
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

    /**
     * 将提示词装配结果写入请求。
     *
     * @param request 模型请求
     * @param prompt 提示词
     */
    private void applyPromptBundle(ModelRequest request, String prompt) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, null, null);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }

    /**
     * 研究解析阶段结果。
     */
    private static class ParseOutcome {

        /**
         * 当前阶段产出的引用列表。
         */
        private final List<ResearchCitation> citations;

        /**
         * 解析失败类型，成功时可为空。
         */
        private final String parseErrorType;

        /**
         * 是否尝试过修复。
         */
        private final boolean repairAttempted;

        /**
         * 修复是否成功。
         */
        private final boolean repairSuccess;

        /**
         * 构造解析阶段结果。
         *
         * @param citations 引用列表
         * @param parseErrorType 错误类型
         * @param repairAttempted 是否尝试修复
         * @param repairSuccess 是否修复成功
         */
        private ParseOutcome(List<ResearchCitation> citations,
                             String parseErrorType,
                             boolean repairAttempted,
                             boolean repairSuccess) {
            this.citations = citations;
            this.parseErrorType = parseErrorType;
            this.repairAttempted = repairAttempted;
            this.repairSuccess = repairSuccess;
        }

        public List<ResearchCitation> getCitations() {
            return citations;
        }

        public String getParseErrorType() {
            return parseErrorType;
        }

        public boolean isRepairAttempted() {
            return repairAttempted;
        }

        public boolean isRepairSuccess() {
            return repairSuccess;
        }
    }
}

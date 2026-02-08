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
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
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
 * 娣卞害鐮旂┒娴佺▼锛岃礋璐ｇ粍缁囨绱笌寮曠敤杈撳嚭銆?
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
     * 鎵ц鐮旂┒娴佺▼銆?
     *
     * @param query 鏌ヨ闂
     * @return 寮曠敤鍒楄〃
     */
    public List<ResearchCitation> run(String query) {
        log.info("鐮旂┒娴佺▼鍚姩, queryLength={}", query == null ? 0 : query.length());
        return List.of();
    }

    /**
     * 甯﹁繍琛屼笂涓嬫枃鐨勭爺绌跺叆鍙ｏ紝鐢ㄤ簬鍙戝竷浜嬩欢銆?
     *
     * @param query 鏌ヨ闂
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param workflowId 宸ヤ綔娴佹爣璇?
     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?
     * @return 寮曠敤鍒楄〃
     */
    public List<ResearchCitation> run(String query,
                                      TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter) {
        return runWithRawRef(query, tenantContext, workflowId, seqCounter).getCitations();
    }

    /**
     * 甯﹀師濮嬪紩鐢ㄨ繑鍥炵殑鐮旂┒鍏ュ彛銆?
     *
     * @param query 鏌ヨ闂
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param workflowId 宸ヤ綔娴佹爣璇?
     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?
     * @return 鐮旂┒鎵ц缁撴灉
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
            log.warn("鐮旂┒寮曠敤淇澶辫触, queryLength={}", query == null ? 0 : query.length());
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                    response != null ? response.getModelId() : null, false, parseErrorType,
                    repairAttempted, repairSuccess);
            citations = buildFallbackCitations(query);
        } else {
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                    response != null ? response.getModelId() : null, true, null, repairAttempted, repairSuccess);
        }
        publishCitationEvents(tenantContext, workflowId, seqCounter, citations);
        log.info("鐮旂┒娴佺▼瀹屾垚, citations={}", citations.size());
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
            浣犳槸鐮旂┒鍔╂墜锛坮esearch citation extractor锛夈€?
            浣犵殑浠诲姟锛氫粠 RESEARCH_CONTEXT_JSON 涓彁鍙栤€滃彲杩芥函鐨勭爺绌跺紩鐢紙citations锛夆€濆垪琛紝鐢ㄤ簬瀹¤涓庡洖鏀俱€?
            
            銆愬紩鐢ㄥ畾涔夈€?
            - citation.source锛氬繀椤绘槸鍙畾浣嶇殑鏉ユ簮鏍囪瘑锛屼緥濡?URL銆佹枃妗ｆ爣棰?绔欑偣銆佽鏂囨爣棰?浣滆€?骞翠唤绛夛紱濡傛灉涓婁笅鏂囨病鏈変换浣曟潵婧愪俊鎭紝鍒?source 鍏佽涓虹┖涓诧紝浣嗗繀椤诲湪 snippet 涓鏄庘€渘o_source_provided鈥濄€?
            - citation.snippet锛氬繀椤绘槸涓?query 鐩稿叧鐨勮瘉鎹墖娈?瑕佺偣鎽樿锛堜笉鏄暱娈靛師鏂囷級锛屾帶鍒跺湪 1~2 鍙ワ紝<= 200 瀛楃銆?
            
            銆愯川閲忚鍒欍€?
            1) 鍙粠涓婁笅鏂囦腑鈥滃凡缁忓嚭鐜?宸叉彁渚涒€濈殑鏉ユ簮鎻愬彇锛岀姝㈢紪閫犳潵婧愭垨鏉滄挵 URL銆?
            2) 鍘婚噸锛氱浉鍚?source 鍙繚鐣欎竴娆★紱鑻ュ悓涓€ source 鏈夊娈佃瘉鎹紝鍚堝苟涓烘洿绮剧偧鐨?snippet銆?
            3) 鎺掑簭锛氭寜涓?query 鐨勭浉鍏虫€т粠楂樺埌浣庯紱鍚岀瓑鐩稿叧鍒欐寜鏃堕棿鏂扳啋鏃э紙鑻ヤ笂涓嬫枃鎻愪緵鏃堕棿淇℃伅锛夈€?
            4) 鏁伴噺鎺у埗锛氭渶澶氳緭鍑?10 鏉★紱涓嶈冻鍒欐寜瀹為檯杈撳嚭銆?
            5) 鍚堣锛歴nippet 涓嶅緱澶嶅埗澶ф鍘熸枃锛屼笉寰楄秴杩?25 涓嫳鏂囪瘝鎴?200 瀛楃锛堜互鏇翠弗鏍艰€呬负鍑嗭級锛涘彲鐢ㄨ浆杩?鎽樿銆?
            
            銆愯緭鍑鸿姹傘€?
            杈撳嚭蹇呴』鏄崟涓?JSON 瀵硅薄锛屼笉鍏佽浠讳綍棰濆鏂囨湰锛屼笉鍏佽 Markdown/浠ｇ爜鍧椼€?
            
            瀛楁绾︽潫锛?
            1) citations: array锛屽繀椤昏緭鍑猴紝缂轰俊鎭～ []銆?
            2) citations[*].source: string锛屽彲杈撳嚭绌轰覆銆?
            3) citations[*].snippet: string锛屽彲杈撳嚭绌轰覆銆?
            
            鏈€灏忕ず渚?JSON锛歿"citations":[]}
            
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


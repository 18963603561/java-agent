package com.example.agent.orchestration.multiagent;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.streaming.sse.EventStreamService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 澶氭櫤鑳戒綋鍗忚皟鍣紝璐熻矗鍥㈤槦缂栨帓涓庤鑹插垎閰嶃€?
 */
@Service
public class MultiAgentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentCoordinator.class);

    private final AgentProfileProperties profileProperties;
    private final ModelInvocationService modelInvocationService;
    private final ModelToolResolver modelToolResolver;
    private final PromptAssembler promptAssembler;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final JsonOutputRepairService jsonOutputRepairService;

    public MultiAgentCoordinator(AgentProfileProperties profileProperties,
                                 ModelInvocationService modelInvocationService,
                                 ModelToolResolver modelToolResolver,
                                 PromptAssembler promptAssembler,
                                 ObjectMapper objectMapper,
                                 ApplicationEventPublisher eventPublisher,
                                 EventStreamService eventStreamService,
                                 JsonOutputRepairService jsonOutputRepairService) {
        this.profileProperties = profileProperties;
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 鍗忚皟澶氭櫤鑳戒綋鎵ц銆?
     *
     * @param step 姝ラ璇锋眰
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param workflowId 宸ヤ綔娴佹爣璇?
     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?
     * @return 鍗忚皟缁撴灉
     */
    public Map<String, Object> coordinate(StepSpec step,
                                          TenantContext tenantContext,
                                          String workflowId,
                                          AtomicLong seqCounter) {
        Map<String, Object> inputSummary = buildInputSummary(step);
        String prompt = buildPrompt(inputSummary);
        ModelRequest request = new ModelRequest(prompt, ModelScene.PLANNER);
        applyPromptBundle(request, prompt, inputSummary);
        modelToolResolver.applyTooling(request, null, step != null ? step.toExecutionInput() : null);
        Map<String, Object> metadata = new HashMap<>();
        if (step != null && step.getStepType() != null) {
            metadata.put("stepType", step.getStepType());
        }
        metadata.put("promptScene", "multiagent");
        ModelResponse response = modelInvocationService.invoke(
                request,
                ModelScene.PLANNER,
                tenantContext,
                workflowId,
                seqCounter,
                "multi_agent",
                metadata
        );
        String rawContent = response != null ? response.getContent() : null;
        String rawRef = response != null ? response.getRawRef() : null;
        boolean repairAttempted = false;
        boolean repairSuccess = false;
        String parseErrorType = null;
        List<AgentRole> roles = parseRoles(rawContent);
        if (roles.isEmpty()) {
            parseErrorType = resolveParseErrorType(rawContent);
            repairAttempted = true;
            List<AgentRole> repaired = tryRepairRoles(rawContent, inputSummary);
            if (!repaired.isEmpty()) {
                roles = repaired;
                repairSuccess = true;
            }
        }
        if (roles.isEmpty()) {
            log.warn("澶氭櫤鑳戒綋瑙ｆ瀽淇澶辫触, stepType={}", step != null ? step.getStepType() : null);
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                    response != null ? response.getModelId() : null, false, parseErrorType,
                    repairAttempted, repairSuccess);
            roles = buildFallbackRoles();
        } else {
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                    response != null ? response.getModelId() : null, true, null, repairAttempted, repairSuccess);
        }

        publishTeamEvents(tenantContext, workflowId, seqCounter, roles);

        Map<String, Object> result = new HashMap<>();
        result.put("team", roles);
        result.put("summary", "team_size=" + roles.size());
        if (StringUtils.hasText(rawRef)) {
            result.put("rawRef", rawRef);
            result.put("modelRawRef", rawRef);
            result.put("refs", Map.of("modelRawRef", rawRef));
        }
        return result;
    }

    private Map<String, Object> buildInputSummary(StepSpec step) {
        Map<String, Object> summary = new HashMap<>();
        if (step != null) {
            Map<String, Object> input = step.getArguments() != null ? step.getArguments() : Map.of();
            putIfNotBlank(summary, "query", input.get("query"));
            putIfNotBlank(summary, "goal", input.get("goal"));
            Object constraints = normalizeTextOrList(input.get("constraints"));
            if (constraints != null) {
                summary.put("constraints", constraints);
            }
            List<String> tools = new ArrayList<>();
            addToolName(tools, input.get("tool"));
            addToolName(tools, input.get("toolName"));
            Object toolsObj = input.get("tools");
            if (toolsObj instanceof List<?> list) {
                for (Object item : list) {
                    addToolName(tools, item);
                }
            }
            if (!tools.isEmpty()) {
                summary.put("tools", tools);
            }
        }
        if (summary.isEmpty()) {
            summary.put("constraints", "(summary disabled)");
        }
        return summary;
    }

    private Object normalizeTextOrList(Object value) {
        if (value instanceof String text) {
            return StringUtils.hasText(text) ? text : null;
        }
        if (value instanceof List<?> list) {
            List<String> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = item.toString();
                if (StringUtils.hasText(text)) {
                    normalized.add(text);
                }
            }
            return normalized.isEmpty() ? null : normalized;
        }
        return null;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, Object value) {
        if (target == null || key == null || value == null) {
            return;
        }
        String text = value.toString();
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private void addToolName(List<String> tools, Object value) {
        if (tools == null || value == null) {
            return;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text) || tools.contains(text)) {
            return;
        }
        tools.add(text);
    }

    /**
     * 鍏煎鏃у叆鍙ｃ€?
     *
     * @param taskId 浠诲姟鏍囪瘑
     */
    public void coordinate(String taskId) {
        log.info("澶氭櫤鑳戒綋鍗忚皟寮€濮? taskId={}", taskId);
    }

    private String buildPrompt(Map<String, Object> inputSummary) {
        Map<String, Object> context = inputSummary != null
                ? new HashMap<>(inputSummary)
                : new HashMap<>();
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
            浣犳槸澶氭櫤鑳戒綋鍥㈤槦鍗忚皟鍣紙team coordinator锛夈€?
            浣犵殑浠诲姟鏄牴鎹?MULTI_AGENT_CONTEXT_JSON 涓殑 query 涓庝换鍔＄壒寰侊紝璁捐鈥滄渶灏忎笖蹇呰鈥濈殑瑙掕壊涓庤亴璐ｅ垎宸ャ€?
            
            绂佹娉涙硾鍒楀嚭宀椾綅鍚嶇О锛涙瘡涓鑹插繀椤荤洿鎺ユ湇鍔′簬瀹屾垚褰撳墠浠诲姟銆?
            
            銆愮粍闃熻鍒欍€?
            
            1) 瑙掕壊鏁伴噺蹇呴』鏈€灏忓寲锛岄€氬父涓?1~4 涓紱鍙湁鍦ㄤ换鍔℃槑鏄惧鏉傛椂鎵嶅鍔犺鑹层€?
            2) 姣忎釜瑙掕壊鐨勮亴璐ｅ繀椤讳笉鍚屼笖浜掕ˉ锛岀姝㈣亴璐ｉ噸澶嶃€?
            3) 姣忎釜瑙掕壊蹇呴』鑳借В閲娾€滀负浠€涔堣繖涓换鍔￠渶瑕佸畠鈥濄€?
            4) 濡傛灉浠诲姟绠€鍗曪紙甯歌瘑闂瓟/鍗曞伐鍏锋煡璇級锛屽彧鍏佽 1 涓鑹层€?
            5) 濡傛灉娑夊強锛?
               - 瑙勫垝 鈫?闇€瑕?Planner
               - 宸ュ叿璋冪敤 鈫?闇€瑕?Executor
               - 淇℃伅鏁村悎/鎬荤粨 鈫?闇€瑕?Summarizer
               - 鐮旂┒/璧勬枡鏀堕泦 鈫?闇€瑕?Researcher
            6) 绂佹鍑虹幇涓庝换鍔℃棤鍏崇殑瑙掕壊锛堝娉涙硾鐨勨€滃垎鏋愬笀/绠＄悊鍛樷€濓級銆?
            
            銆愯緭鍑鸿姹傘€?
            杈撳嚭蹇呴』鏄崟涓?JSON 瀵硅薄锛屼笉鍏佽浠讳綍棰濆鏂囨湰锛屼笉鍏佽 Markdown/浠ｇ爜鍧椼€?
            
            瀛楁绾︽潫锛?
            1) team: array锛屽繀椤昏緭鍑?
            2) team[*].role: string锛岃鑹插悕绉?
            3) team[*].responsibility: string锛屾槑纭瑙掕壊鍦ㄦ湰浠诲姟涓殑鑱岃矗
            
            鍏佽棰濆瀛楁浣嗕笉瑕佷緷璧栵紝渚嬪 roleId銆乶ame銆乵odelId銆乨escription銆?
            
            鏈€灏忕ず渚?JSON锛?
            {"team":[{"role":"","responsibility":""}]}
            
            MULTI_AGENT_CONTEXT_JSON:%s
            """.formatted(contextJson);

    }

    private List<AgentRole> parseRoles(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            Object teamObj = root.get("team");
            if (!(teamObj instanceof List<?> list)) {
                return List.of();
            }
            List<AgentRole> roles = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                AgentRole role = new AgentRole();
                role.setRoleId(map.get("roleId") instanceof String value ? value : UUID.randomUUID().toString());
                role.setName(map.get("name") instanceof String value ? value : "agent");
                role.setModelId(map.get("modelId") instanceof String value ? value : null);
                role.setDescription(map.get("description") instanceof String value ? value : null);
                roles.add(role);
            }
            return roles;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<AgentRole> tryRepairRoles(String rawContent, Map<String, Object> inputSummary) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return List.of();
        }
        Map<String, Object> context = inputSummary != null
                ? new HashMap<>(inputSummary)
                : new HashMap<>();
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("multiagent", rawContent, JsonOutputSchema.MULTIAGENT,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return List.of();
        }
        return parseRoles(repaired);
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
            trace = PromptTrace.fromPrompt("multiagent", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "multi_agent", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
    }

    private List<AgentRole> buildFallbackRoles() {
        List<AgentRole> roles = new ArrayList<>();
        if (profileProperties.getItems() != null) {
            for (AgentProfile profile : profileProperties.getItems()) {
                AgentRole role = new AgentRole();
                role.setRoleId(profile.getAgentId());
                role.setName(profile.getAgentId());
                role.setModelId(profile.getModelId());
                role.setDescription(profile.getPrompt());
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            AgentRole role = new AgentRole();
            role.setRoleId("default");
            role.setName("default");
            roles.add(role);
        }
        return roles;
    }

    private void publishTeamEvents(TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   List<AgentRole> roles) {
        publishEvent(tenantContext, workflowId, seqCounter, EventType.TEAM_RECRUITED,
                Map.of("teamSize", roles.size()));
        for (AgentRole role : roles) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("roleId", role.getRoleId());
            payload.put("name", role.getName());
            if (role.getModelId() != null) {
                payload.put("modelId", role.getModelId());
            }
            publishEvent(tenantContext, workflowId, seqCounter, EventType.ROLE_ASSIGNED, payload);
        }
    }

    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter,
                              EventType type,
                              Map<String, Object> payload) {
        if (tenantContext == null || workflowId == null) {
            return;
        }
        long seq = seqCounter != null
                ? seqCounter.incrementAndGet()
                : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }

    private void applyPromptBundle(ModelRequest request, String prompt, Map<String, Object> inputSummary) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, null, inputSummary);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }
}


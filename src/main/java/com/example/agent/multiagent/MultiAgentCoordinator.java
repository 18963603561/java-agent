package com.example.agent.multiagent;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.repair.JsonOutputRepairService;
import com.example.agent.repair.JsonOutputSchema;
import com.example.agent.model.PromptTrace;
import com.example.agent.runtime.StepRequest;
import com.example.agent.streaming.EventStreamService;
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
 * 多智能体协调器，负责团队编排与角色分配。
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
     * 协调多智能体执行。
     *
     * @param step 步骤请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 协调结果
     */
    public Map<String, Object> coordinate(StepRequest step,
                                          TenantContext tenantContext,
                                          String workflowId,
                                          AtomicLong seqCounter) {
        Map<String, Object> inputSummary = buildInputSummary(step);
        String prompt = buildPrompt(inputSummary);
        ModelRequest request = new ModelRequest(prompt, ModelScene.PLANNER);
        applyPromptBundle(request, prompt, inputSummary);
        modelToolResolver.applyTooling(request, null, step != null ? step.getInput() : null);
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
            log.warn("多智能体解析修复失败, stepType={}", step != null ? step.getStepType() : null);
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
        return result;
    }

    private Map<String, Object> buildInputSummary(StepRequest step) {
        Map<String, Object> summary = new HashMap<>();
        if (step != null && step.getInput() != null) {
            Map<String, Object> input = step.getInput();
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
     * 兼容旧入口。
     *
     * @param taskId 任务标识
     */
    public void coordinate(String taskId) {
        log.info("多智能体协调开始, taskId={}", taskId);
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
            你是多智能体团队协调器（team coordinator）。
            你的任务是根据 MULTI_AGENT_CONTEXT_JSON 中的 query 与任务特征，设计“最小且必要”的角色与职责分工。
            
            禁止泛泛列出岗位名称；每个角色必须直接服务于完成当前任务。
            
            【组队规则】
            
            1) 角色数量必须最小化，通常为 1~4 个；只有在任务明显复杂时才增加角色。
            2) 每个角色的职责必须不同且互补，禁止职责重复。
            3) 每个角色必须能解释“为什么这个任务需要它”。
            4) 如果任务简单（常识问答/单工具查询），只允许 1 个角色。
            5) 如果涉及：
               - 规划 → 需要 Planner
               - 工具调用 → 需要 Executor
               - 信息整合/总结 → 需要 Summarizer
               - 研究/资料收集 → 需要 Researcher
            6) 禁止出现与任务无关的角色（如泛泛的“分析师/管理员”）。
            
            【输出要求】
            输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
            
            字段约束：
            1) team: array，必须输出
            2) team[*].role: string，角色名称
            3) team[*].responsibility: string，明确该角色在本任务中的职责
            
            允许额外字段但不要依赖，例如 roleId、name、modelId、description。
            
            最小示例 JSON：
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

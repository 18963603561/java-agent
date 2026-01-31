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
        String prompt = buildPrompt(step);
        ModelRequest request = new ModelRequest(prompt, ModelScene.PLANNER);
        applyPromptBundle(request, prompt, step);
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
            List<AgentRole> repaired = tryRepairRoles(rawContent, step);
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

    /**
     * 兼容旧入口。
     *
     * @param taskId 任务标识
     */
    public void coordinate(String taskId) {
        log.info("多智能体协调开始, taskId={}", taskId);
    }

    private String buildPrompt(StepRequest step) {
        Map<String, Object> context = new HashMap<>();
        if (step != null && step.getInput() != null) {
            context.putAll(step.getInput());
        }
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
                你是团队协调器，请给出角色与职责列表。
                输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
                字段约束：
                1) team: array，必须输出，缺信息填 []。
                2) team[*].role: string，必须输出，缺信息填空串。
                3) team[*].responsibility: string，必须输出，缺信息填空串。
                允许额外字段但不要依赖，例如 roleId、name、modelId、description。
                最小示例 JSON：{"team":[{"role":"","responsibility":""}]}
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

    private List<AgentRole> tryRepairRoles(String rawContent, StepRequest step) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return List.of();
        }
        Map<String, Object> context = new HashMap<>();
        if (step != null && step.getInput() != null) {
            context.putAll(step.getInput());
        }
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

    private void applyPromptBundle(ModelRequest request, String prompt, StepRequest step) {
        if (promptAssembler == null || request == null) {
            return;
        }
        Map<String, Object> input = step != null ? step.getInput() : null;
        PromptBundle bundle = promptAssembler.build(prompt, null, input);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }
}

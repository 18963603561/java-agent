package com.example.agent.orchestration.multiagent;

import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 多智能体角色解析器。
 * <p>用途：封装角色 JSON 解析、修复与回退策略，保持协调器主链路简洁。
 */
@Component
public class MultiAgentRoleResolver {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentRoleResolver.class);

    private final ObjectMapper objectMapper;
    private final JsonOutputRepairService jsonOutputRepairService;
    private final AgentProfileProperties profileProperties;

    public MultiAgentRoleResolver(ObjectMapper objectMapper,
                                  JsonOutputRepairService jsonOutputRepairService,
                                  AgentProfileProperties profileProperties) {
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
        this.profileProperties = profileProperties;
    }

    /**
     * 解析并修复角色结果。
     */
    public RoleResolveResult resolve(String rawContent,
                                     Map<String, Object> inputSummary,
                                     String workflowId,
                                     String stepType) {
        ParseContext parseContext = new ParseContext(workflowId, stepType);
        List<AgentRole> roles = parseRoles(rawContent, parseContext, false);
        if (!roles.isEmpty()) {
            return new RoleResolveResult(roles, null, false, false);
        }

        String parseErrorType = resolveParseErrorType(rawContent);
        List<AgentRole> repaired = tryRepairRoles(rawContent, inputSummary, parseContext);
        if (!repaired.isEmpty()) {
            return new RoleResolveResult(repaired, parseErrorType, true, true);
        }
        return new RoleResolveResult(List.of(), parseErrorType, true, false);
    }

    /**
     * 构建回退角色。
     */
    public List<AgentRole> buildFallbackRoles() {
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
            role.setDescription("默认单角色执行");
            roles.add(role);
        }
        return roles;
    }

    private List<AgentRole> parseRoles(String content, ParseContext parseContext, boolean repairedContent) {
        if (!StringUtils.hasText(content)) {
            log.warn("多智能体输出为空, workflowId={}, stepType={}, repairedContent={}",
                    parseContext.workflowId(), parseContext.stepType(), repairedContent);
            return List.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            Object teamObj = root.get("team");
            if (!(teamObj instanceof List<?> list)) {
                log.warn("多智能体输出缺少team数组, workflowId={}, stepType={}, repairedContent={}",
                        parseContext.workflowId(), parseContext.stepType(), repairedContent);
                return List.of();
            }

            List<AgentRole> roles = new ArrayList<>();
            for (Object item : list) {
                AgentRole role = parseRoleItem(item, parseContext, repairedContent);
                if (role != null) {
                    roles.add(role);
                }
            }
            return roles;
        } catch (Exception ex) {
            log.warn("多智能体输出解析失败, workflowId={}, stepType={}, repairedContent={}, errorType={}",
                    parseContext.workflowId(), parseContext.stepType(), repairedContent,
                    ex.getClass().getSimpleName(), ex);
            return List.of();
        }
    }

    private AgentRole parseRoleItem(Object item, ParseContext parseContext, boolean repairedContent) {
        if (!(item instanceof Map<?, ?> map)) {
            log.warn("多智能体team元素类型非法, workflowId={}, stepType={}, repairedContent={}, itemType={}",
                    parseContext.workflowId(), parseContext.stepType(), repairedContent,
                    item == null ? "null" : item.getClass().getSimpleName());
            return null;
        }
        String roleId = readText(map.get("roleId"));
        String name = readText(map.get("name"));
        String modelId = readText(map.get("modelId"));
        String description = readText(map.get("description"));
        if (!StringUtils.hasText(roleId) && StringUtils.hasText(name)) {
            roleId = normalizeRoleId(name);
        }
        if (!StringUtils.hasText(roleId) || !StringUtils.hasText(name) || !StringUtils.hasText(description)) {
            log.warn("多智能体team元素字段不完整，跳过该元素, workflowId={}, stepType={}, repairedContent={}, roleId={}, name={}",
                    parseContext.workflowId(), parseContext.stepType(), repairedContent, roleId, name);
            return null;
        }
        AgentRole role = new AgentRole();
        role.setRoleId(roleId);
        role.setName(name);
        role.setModelId(modelId);
        role.setDescription(description);
        return role;
    }

    private String readText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String normalizeRoleId(String name) {
        String normalized = name == null ? null : name.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        if (!StringUtils.hasText(normalized)) {
            return UUID.randomUUID().toString();
        }
        return normalized;
    }

    private List<AgentRole> tryRepairRoles(String rawContent,
                                           Map<String, Object> inputSummary,
                                           ParseContext parseContext) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return List.of();
        }
        Map<String, Object> context = inputSummary != null ? new HashMap<>(inputSummary) : new HashMap<>();
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            log.warn("多智能体修复上下文序列化失败，使用空上下文", ex);
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("multiagent", rawContent, JsonOutputSchema.MULTIAGENT,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            log.warn("多智能体输出修复失败, workflowId={}, stepType={}",
                    parseContext.workflowId(), parseContext.stepType());
            return List.of();
        }
        return parseRoles(repaired, parseContext, true);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
    }

    /**
     * 角色解析结果。
     */
    public record RoleResolveResult(List<AgentRole> roles,
                                    String parseErrorType,
                                    boolean repairAttempted,
                                    boolean repairSuccess) {
    }

    private record ParseContext(String workflowId, String stepType) {
    }
}


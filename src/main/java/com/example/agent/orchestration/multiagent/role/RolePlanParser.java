package com.example.agent.orchestration.multiagent.role;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 角色规划解析器。
 * <p>用途：将模型输出 JSON 解析为角色列表，保持解析职责集中并可独立测试。</p>
 */
@Component
public class RolePlanParser {

    private static final Logger log = LoggerFactory.getLogger(RolePlanParser.class);

    private final ObjectMapper objectMapper;

    public RolePlanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析角色列表。
     *
     * @param content 模型输出内容
     * @param context 解析上下文
     * @param repairedContent 是否为修复后内容
     * @return 解析结果
     */
    public ParseResult parse(String content, RoleResolveContext context, boolean repairedContent) {
        if (!StringUtils.hasText(content)) {
            log.warn("多智能体输出为空, workflowId={}, stepType={}, repairedContent={}",
                    context.workflowId(),
                    context.stepType(),
                    repairedContent);
            return ParseResult.failed(List.of(), "empty_output");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            Object teamObj = root.get("team");
            if (!(teamObj instanceof List<?> teamList)) {
                log.warn("多智能体输出缺少team数组, workflowId={}, stepType={}, repairedContent={}",
                        context.workflowId(),
                        context.stepType(),
                        repairedContent);
                return ParseResult.failed(List.of(), "missing_team");
            }

            List<AgentRole> roles = new ArrayList<>();
            for (Object item : teamList) {
                AgentRole role = parseRoleItem(item, context, repairedContent);
                if (role != null) {
                    roles.add(role);
                }
            }
            if (roles.isEmpty()) {
                return ParseResult.failed(List.of(), "empty_team");
            }
            return ParseResult.success(roles);
        } catch (Exception ex) {
            log.warn("多智能体输出解析失败, workflowId={}, stepType={}, repairedContent={}, errorType={}",
                    context.workflowId(),
                    context.stepType(),
                    repairedContent,
                    ex.getClass().getSimpleName(),
                    ex);
            return ParseResult.failed(List.of(), "json_parse_error");
        }
    }

    private AgentRole parseRoleItem(Object item, RoleResolveContext context, boolean repairedContent) {
        if (!(item instanceof Map<?, ?> map)) {
            log.warn("多智能体team元素类型非法, workflowId={}, stepType={}, repairedContent={}, itemType={}",
                    context.workflowId(),
                    context.stepType(),
                    repairedContent,
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
                    context.workflowId(),
                    context.stepType(),
                    repairedContent,
                    roleId,
                    name);
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

    /**
     * 角色解析结果。
     */
    public record ParseResult(List<AgentRole> roles, boolean success, String errorType) {

        public static ParseResult success(List<AgentRole> roles) {
            return new ParseResult(roles == null ? List.of() : List.copyOf(roles), true, null);
        }

        public static ParseResult failed(List<AgentRole> roles, String errorType) {
            return new ParseResult(roles == null ? List.of() : List.copyOf(roles), false, errorType);
        }
    }
}


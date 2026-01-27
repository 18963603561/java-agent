package com.example.agent.model;

import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.common.TaskRequest;
import com.example.agent.tools.McpToolDefinition;
import com.example.agent.tools.skill.SkillDefinition;
import com.example.agent.tools.skill.SkillRegistry;
import com.example.agent.tools.skill.SkillRoute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型工具解析器，负责注入工具定义与选择策略。
 */
@Component
public class ModelToolResolver {

    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    public ModelToolResolver(ToolRegistry toolRegistry, SkillRegistry skillRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 将工具定义与选择策略注入模型请求，默认工具选择为 auto。
     *
     * @param request 模型请求
     * @param taskRequest 任务请求
     * @param stepInput 步骤输入
     */
    public void applyTooling(ModelRequest request, TaskRequest taskRequest, Map<String, Object> stepInput) {
        if (request == null) {
            return;
        }
        String skillName = resolveSkillName(taskRequest, stepInput);
        SkillDefinition skillDefinition = resolveSkillDefinition(skillName);
        List<String> allowedTools = resolveAllowedTools(skillDefinition);
        ModelToolChoice skillChoice = resolveToolChoiceFromConstraints(skillDefinition);

        List<ModelToolDefinition> tools = request.getTools();
        boolean hasTools = tools != null && !tools.isEmpty();
        if (!hasTools) {
            tools = resolveTools();
        }
        if (allowedTools != null) {
            // 技能约束优先收敛可用工具，避免无关工具进入模型上下文。
            tools = filterTools(tools, allowedTools);
        }
        if (!hasTools || allowedTools != null) {
            if (tools != null && (!tools.isEmpty() || allowedTools != null)) {
                request.setTools(tools);
            }
        }

        if (skillChoice != null) {
            request.setToolChoice(skillChoice);
        } else if (request.getToolChoice() == null) {
            ModelToolChoice choice = resolveToolChoice(taskRequest, stepInput);
            if (choice != null) {
                request.setToolChoice(choice);
            } else if (request.getTools() != null && !request.getTools().isEmpty()) {
                request.setToolChoice(ModelToolChoice.auto());
            }
        }
    }

    private String resolveSkillName(TaskRequest taskRequest, Map<String, Object> stepInput) {
        String skillName = null;
        if (stepInput != null) {
            Object fromStep = stepInput.get("skill");
            if (!(fromStep instanceof String) || !StringUtils.hasText((String) fromStep)) {
                fromStep = stepInput.get("skillName");
            }
            if (fromStep instanceof String value && StringUtils.hasText(value)) {
                skillName = value;
            }
        }
        if (!StringUtils.hasText(skillName) && taskRequest != null && StringUtils.hasText(taskRequest.getSkillName())) {
            skillName = taskRequest.getSkillName();
        }
        return skillName;
    }

    private SkillDefinition resolveSkillDefinition(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return null;
        }
        List<SkillDefinition> definitions = skillRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        for (SkillDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getName())) {
                continue;
            }
            if (definition.getName().equalsIgnoreCase(skillName)) {
                return definition;
            }
        }
        return null;
    }

    private List<String> resolveAllowedTools(SkillDefinition skillDefinition) {
        if (skillDefinition == null) {
            return null;
        }
        List<String> allowFromConstraints = resolveAllowToolsFromConstraints(skillDefinition.getConstraints());
        if (allowFromConstraints != null) {
            return allowFromConstraints;
        }
        if (skillDefinition.getRoutes() == null || skillDefinition.getRoutes().isEmpty()) {
            return null;
        }
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        for (SkillRoute route : skillDefinition.getRoutes()) {
            if (route != null && StringUtils.hasText(route.getToolName())) {
                tools.add(route.getToolName());
            }
        }
        if (tools.isEmpty()) {
            return null;
        }
        return new ArrayList<>(tools);
    }

    private List<String> resolveAllowToolsFromConstraints(Map<String, Object> constraints) {
        if (constraints == null) {
            return null;
        }
        Object raw = constraints.get("allowTools");
        if (raw instanceof List<?> list) {
            List<String> allowTools = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String value && StringUtils.hasText(value)) {
                    allowTools.add(value);
                } else if (item != null) {
                    String text = item.toString();
                    if (StringUtils.hasText(text)) {
                        allowTools.add(text);
                    }
                }
            }
            return allowTools;
        }
        if (raw instanceof String value) {
            if (StringUtils.hasText(value)) {
                return List.of(value);
            }
            return List.of();
        }
        return null;
    }

    private ModelToolChoice resolveToolChoiceFromConstraints(SkillDefinition skillDefinition) {
        if (skillDefinition == null || skillDefinition.getConstraints() == null) {
            return null;
        }
        Object raw = skillDefinition.getConstraints().get("toolChoice");
        if (raw == null) {
            return null;
        }
        return parseToolChoice(raw);
    }

    private List<ModelToolDefinition> filterTools(List<ModelToolDefinition> tools, List<String> allowedTools) {
        if (tools == null) {
            return List.of();
        }
        if (allowedTools == null) {
            return tools;
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String name : allowedTools) {
            if (StringUtils.hasText(name)) {
                allowed.add(name);
            }
        }
        if (allowed.isEmpty()) {
            return List.of();
        }
        List<ModelToolDefinition> filtered = new ArrayList<>();
        for (ModelToolDefinition tool : tools) {
            if (tool != null && StringUtils.hasText(tool.getName()) && allowed.contains(tool.getName())) {
                filtered.add(tool);
            }
        }
        return filtered;
    }

    private List<ModelToolDefinition> resolveTools() {
        List<McpToolDefinition> definitions = toolRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<ModelToolDefinition> tools = new ArrayList<>();
        for (McpToolDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getName())) {
                continue;
            }
            JsonNode parameters = definition.getInputSchema() != null
                    ? objectMapper.valueToTree(definition.getInputSchema())
                    : null;
            ModelToolDefinition tool = new ModelToolDefinition(
                    definition.getName(),
                    definition.getDescription(),
                    parameters);
            tools.add(tool);
        }
        return tools;
    }

    private ModelToolChoice resolveToolChoice(TaskRequest taskRequest, Map<String, Object> stepInput) {
        ModelToolChoice fromStep = parseToolChoice(stepInput != null ? stepInput.get("toolChoice") : null);
        if (fromStep != null) {
            return fromStep;
        }
        if (taskRequest != null) {
            return taskRequest.getToolChoice();
        }
        return null;
    }

    private ModelToolChoice parseToolChoice(Object raw) {
        if (raw instanceof ModelToolChoice choice) {
            return choice;
        }
        if (raw instanceof String value) {
            return ModelToolChoice.fromString(value);
        }
        if (raw instanceof Map<?, ?> map) {
            String mode = map.get("mode") != null ? map.get("mode").toString() : null;
            if (!StringUtils.hasText(mode) && map.get("type") != null) {
                mode = map.get("type").toString();
            }
            String name = map.get("toolName") != null ? map.get("toolName").toString() : null;
            if (!StringUtils.hasText(name) && map.get("name") != null) {
                name = map.get("name").toString();
            }
            if (StringUtils.hasText(mode) && "specified".equalsIgnoreCase(mode)) {
                return ModelToolChoice.specified(name);
            }
            ModelToolChoice parsed = ModelToolChoice.fromString(mode);
            if (parsed != null && parsed.getMode() == ModelToolChoice.Mode.SPECIFIED) {
                parsed.setToolName(name);
            }
            return parsed;
        }
        return null;
    }
}

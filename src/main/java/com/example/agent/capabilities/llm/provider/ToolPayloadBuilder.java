package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.llm.contract.ModelToolDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工具请求体构造器。
 *
 * <p>用途：统一构建兼容接口与原生接口的工具定义载荷。</p>
 */
@Component
public class ToolPayloadBuilder {

    /**
     * 构建兼容接口工具列表。
     *
     * @param tools 工具定义
     * @return 载荷列表
     */
    public List<Map<String, Object>> buildOpenAiTools(List<ModelToolDefinition> tools) {
        List<Map<String, Object>> payload = new ArrayList<>();
        if (tools == null) {
            return payload;
        }
        for (ModelToolDefinition tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.getName())) {
                continue;
            }
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.getName());
            if (StringUtils.hasText(tool.getDescription())) {
                function.put("description", tool.getDescription());
            }
            if (tool.getParameters() != null) {
                function.put("parameters", tool.getParameters());
            } else {
                Map<String, Object> schema = new HashMap<>();
                schema.put("type", "object");
                schema.put("properties", Map.of());
                function.put("parameters", schema);
            }
            Map<String, Object> item = new HashMap<>();
            item.put("type", "function");
            item.put("function", function);
            payload.add(item);
        }
        return payload;
    }

    /**
     * 构建原生接口工具列表。
     *
     * @param tools 工具定义
     * @return 载荷列表
     */
    public List<Map<String, Object>> buildOllamaTools(List<ModelToolDefinition> tools) {
        List<Map<String, Object>> payload = new ArrayList<>();
        if (tools == null) {
            return payload;
        }
        for (ModelToolDefinition tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.getName())) {
                continue;
            }
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.getName());
            if (StringUtils.hasText(tool.getDescription())) {
                function.put("description", tool.getDescription());
            }
            if (tool.getParameters() != null) {
                function.put("parameters", tool.getParameters());
            }
            Map<String, Object> item = new HashMap<>();
            item.put("type", "function");
            item.put("function", function);
            payload.add(item);
        }
        return payload;
    }

    /**
     * 根据选择策略过滤原生接口工具。
     *
     * @param requestTools 全量工具
     * @param choice 工具选择策略
     * @return 过滤后的工具列表
     */
    public List<ModelToolDefinition> resolveOllamaTools(List<ModelToolDefinition> requestTools, ModelToolChoice choice) {
        if (requestTools == null || requestTools.isEmpty()) {
            return List.of();
        }
        if (choice == null || choice.getMode() == null) {
            return requestTools;
        }
        return switch (choice.getMode()) {
            case NONE -> List.of();
            case SPECIFIED -> filterToolByName(requestTools, choice.getToolName());
            case REQUIRED, AUTO -> requestTools;
        };
    }

    /**
     * 构建工具选择字段。
     *
     * @param toolChoice 工具选择策略
     * @return 可序列化值
     */
    public Object buildToolChoiceValue(ModelToolChoice toolChoice) {
        if (toolChoice == null || toolChoice.getMode() == null) {
            return null;
        }
        return switch (toolChoice.getMode()) {
            case AUTO -> "auto";
            case NONE -> "none";
            case REQUIRED -> "required";
            case SPECIFIED -> {
                if (!StringUtils.hasText(toolChoice.getToolName())) {
                    yield null;
                }
                Map<String, Object> function = new HashMap<>();
                function.put("name", toolChoice.getToolName());
                Map<String, Object> value = new HashMap<>();
                value.put("type", "function");
                value.put("function", function);
                yield value;
            }
        };
    }

    private List<ModelToolDefinition> filterToolByName(List<ModelToolDefinition> tools, String toolName) {
        if (!StringUtils.hasText(toolName) || tools == null || tools.isEmpty()) {
            return tools == null ? List.of() : tools;
        }
        List<ModelToolDefinition> filtered = new ArrayList<>();
        for (ModelToolDefinition tool : tools) {
            if (tool != null && StringUtils.hasText(tool.getName()) && tool.getName().equalsIgnoreCase(toolName)) {
                filtered.add(tool);
            }
        }
        return filtered;
    }
}



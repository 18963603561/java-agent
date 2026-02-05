package com.example.agent.capabilities.tools.registry;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.capabilities.tools.mcp.McpToolDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 工具注册表，负责管理可用工具及其执行逻辑。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolHandler> handlers = new HashMap<>();
    private final Map<String, McpToolDefinition> definitions = new HashMap<>();
    /**
     * 工具定义来源映射，用于按来源覆盖与清理。
     */
    private final Map<String, String> definitionSources = new HashMap<>();

    public ToolRegistry() {
        registerDefaults();
    }

    /**
     * 解析工具名称。
     *
     * @param toolName 工具名称
     * @return 工具名称
     */
    public String resolve(String toolName) {
        return toolName;
    }

    /**
     * 获取工具定义列表。
     *
     * @return 工具定义列表
     */
    public List<McpToolDefinition> listDefinitions() {
        return new ArrayList<>(definitions.values());
    }

    /**
     * 按名称获取工具定义。
     *
     * @param toolName 工具名称
     * @return 工具定义
     */
    public McpToolDefinition getDefinition(String toolName) {
        if (toolName == null) {
            return null;
        }
        return definitions.get(toolName);
    }

    /**
     * 判断是否存在可执行工具。
     *
     * <p>输入：工具名称。
     * <p>输出：是否存在可执行处理器。
     * <p>示例：
     * <pre>{@code
     * boolean available = toolRegistry.hasTool("demo_tool");
     * }</pre>
     *
     * @param toolName 工具名称
     * @return 是否可执行
     */
    public boolean hasTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return handlers.containsKey(toolName);
    }

    /**
     * 注册外部工具定义，供模型侧展示与选择。
     *
     * @param toolDefinitions 工具定义列表
     */
    public void registerDefinitions(List<McpToolDefinition> toolDefinitions) {
        registerDefinitions(toolDefinitions, "local", false);
    }

    /**
     * 注册工具定义并标记来源，支持按来源更新。
     *
     * @param toolDefinitions 工具定义列表
     * @param source 来源标识
     * @param overwrite 是否允许覆盖同来源的工具定义
     */
    public void registerDefinitions(List<McpToolDefinition> toolDefinitions, String source, boolean overwrite) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return;
        }
        String normalizedSource = source == null ? "unknown" : source.trim();
        synchronized (definitions) {
            for (McpToolDefinition definition : toolDefinitions) {
                if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
                    continue;
                }
                String name = definition.getName();
                if (definitions.containsKey(name)) {
                    String existingSource = definitionSources.get(name);
                    if (!shouldOverwrite(existingSource, normalizedSource, overwrite)) {
                        log.warn("工具定义已存在且来源不同, toolName={}, source={}, existingSource={}",
                                name, normalizedSource, existingSource);
                        continue;
                    }
                    definitions.put(name, definition);
                    definitionSources.put(name, normalizedSource);
                    log.info("工具定义已更新, toolName={}, source={}", name, normalizedSource);
                    continue;
                }
                definitions.put(name, definition);
                definitionSources.put(name, normalizedSource);
                log.info("工具定义已注册, toolName={}, source={}", name, normalizedSource);
            }
        }
    }

    /**
     * 移除指定来源下不在保留列表中的工具定义。
     *
     * @param source 来源标识
     * @param keepNames 需要保留的工具名称集合
     */
    public void removeDefinitionsBySourceExcept(String source, java.util.Set<String> keepNames) {
        if (source == null || source.isBlank()) {
            return;
        }
        synchronized (definitions) {
            List<String> removed = new ArrayList<>();
            for (Map.Entry<String, String> entry : definitionSources.entrySet()) {
                String name = entry.getKey();
                String entrySource = entry.getValue();
                if (!source.equals(entrySource)) {
                    continue;
                }
                if (keepNames != null && keepNames.contains(name)) {
                    continue;
                }
                removed.add(name);
            }
            for (String name : removed) {
                definitions.remove(name);
                definitionSources.remove(name);
            }
            if (!removed.isEmpty()) {
                log.info("工具定义已清理, source={}, removed={}", source, removed.size());
            }
        }
    }

    private boolean shouldOverwrite(String existingSource, String newSource, boolean overwrite) {
        if (!overwrite) {
            return false;
        }
        if (existingSource == null) {
            return true;
        }
        return existingSource.equals(newSource);
    }


    /**
     * 执行指定工具。
     *
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 执行结果
     */
    public Map<String, Object> execute(String toolName, Map<String, Object> arguments) {
        ToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "工具不存在");
        }
        return handler.handle(arguments == null ? Map.of() : arguments);
    }

    private void registerDefaults() {
        McpToolDefinition demo = new McpToolDefinition(
                "demo_tool",
                "v1",
                "示例工具",
                Map.of("type", "object"),
                Map.of("type", "object"),
                List.of("demo"));
        definitions.put(demo.getName(), demo);
        definitionSources.put(demo.getName(), "local");
        handlers.put(demo.getName(), args -> Map.of(
                "echo", args,
                "message", "demo_tool_ok"
        ));
    }

    private interface ToolHandler {
        Map<String, Object> handle(Map<String, Object> arguments);
    }
}

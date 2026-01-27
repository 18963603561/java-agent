package com.example.agent.agentcore;

import com.example.agent.common.ErrorCodeException;
import com.example.agent.tools.McpToolDefinition;
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
     * 注册外部工具定义，供模型侧展示与选择。
     *
     * @param toolDefinitions 工具定义列表
     */
    public void registerDefinitions(List<McpToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return;
        }
        for (McpToolDefinition definition : toolDefinitions) {
            if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
                continue;
            }
            if (definitions.containsKey(definition.getName())) {
                log.warn("工具定义已存在, toolName={}", definition.getName());
                continue;
            }
            definitions.put(definition.getName(), definition);
            log.info("工具定义已注册, toolName={}", definition.getName());
        }
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
        handlers.put(demo.getName(), args -> Map.of(
                "echo", args,
                "message", "demo_tool_ok"
        ));
    }

    private interface ToolHandler {
        Map<String, Object> handle(Map<String, Object> arguments);
    }
}

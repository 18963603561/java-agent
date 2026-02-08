package com.example.agent.capabilities.tools.execution.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具调用输出载荷。
 *
 * <p>用途：封装工具实际输出内容，避免执行主流程直接拼装零散 Map 键。</p>
 */
public class ToolInvocationPayload {

    /**
     * 输出数据。
     */
    private final Map<String, Object> values;

    public ToolInvocationPayload() {
        this.values = new HashMap<>();
    }

    public ToolInvocationPayload(Map<String, Object> values) {
        this.values = values == null ? new HashMap<>() : new HashMap<>(values);
    }

    public Map<String, Object> getValues() {
        return values;
    }
}


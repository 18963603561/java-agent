package com.example.agent.capabilities.tools.execution.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具调用参数对象。
 *
 * <p>用途：对工具调用参数进行强类型封装，统一参数读写与后续扩展入口。</p>
 */
public class ToolInvocationArguments {

    /**
     * 调用参数集合。
     */
    private final Map<String, Object> values;

    public ToolInvocationArguments() {
        this.values = new HashMap<>();
    }

    public ToolInvocationArguments(Map<String, Object> values) {
        this.values = values == null ? new HashMap<>() : new HashMap<>(values);
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}


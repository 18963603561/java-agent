package com.example.agent.runtime.structured.structured;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构化数据默认实现。
 *
 * <p>用途：作为泛型化改造过程中的兜底数据类型，
 * 适用于暂未沉淀强类型模型的场景。
 */
public class DefaultStructuredData implements StructuredData {

    /**
     * 默认键值数据。
     */
    private final Map<String, Object> values;

    public DefaultStructuredData(Map<String, Object> values) {
        this.values = immutableCopy(values);
    }

    /**
     * 创建默认数据对象。
     *
     * @param values 输入键值
     * @return 默认数据对象
     */
    public static DefaultStructuredData from(Map<String, Object> values) {
        return new DefaultStructuredData(values);
    }

    /**
     * 获取不可变键值数据。
     *
     * @return 不可变 Map
     */
    public Map<String, Object> getValues() {
        return values;
    }

    @Override
    public Map<String, Object> toMap() {
        return values;
    }

    private Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}


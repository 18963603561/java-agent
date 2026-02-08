package com.example.agent.capabilities.memory.model;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 记忆检索优先级枚举，统一管理优先级归一化与解析逻辑。
 */
public enum RetrievalPriority {

    RECENT,
    SEMANTIC,
    SUMMARY;

    /**
     * 返回系统默认检索优先级顺序。
     *
     * @return 默认优先级列表
     */
    public static List<RetrievalPriority> defaultOrder() {
        return List.of(SEMANTIC, RECENT, SUMMARY);
    }

    /**
     * 将任意输入归一化为枚举值。
     *
     * @param value 输入值
     * @return 归一化后的优先级，无法识别时返回 null
     */
    public static RetrievalPriority parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (RetrievalPriority priority : values()) {
            if (priority.name().equals(normalized)) {
                return priority;
            }
        }
        return null;
    }
}

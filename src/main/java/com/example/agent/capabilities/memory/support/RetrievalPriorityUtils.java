package com.example.agent.capabilities.memory.support;

import com.example.agent.capabilities.memory.model.RetrievalPriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 检索优先级工具类，统一处理优先级顺序归一化与标签格式化。
 */
public final class RetrievalPriorityUtils {

    private RetrievalPriorityUtils() {
    }

    /**
     * 归一化优先级顺序并补齐默认顺序。
     *
     * @param configured 配置优先级
     * @return 归一化后的优先级列表
     */
    public static List<RetrievalPriority> normalizeOrder(List<RetrievalPriority> configured) {
        List<RetrievalPriority> resolved = new ArrayList<>();
        if (configured != null) {
            for (RetrievalPriority value : configured) {
                if (value != null && !resolved.contains(value)) {
                    resolved.add(value);
                }
            }
        }
        for (RetrievalPriority value : RetrievalPriority.defaultOrder()) {
            if (!resolved.contains(value)) {
                resolved.add(value);
            }
        }
        return resolved;
    }

    /**
     * 格式化优先级标签，用于指标维度聚合。
     *
     * @param retrievalPriority 优先级顺序
     * @return 标签字符串
     */
    public static String formatPriorityTag(List<RetrievalPriority> retrievalPriority) {
        if (retrievalPriority == null || retrievalPriority.isEmpty()) {
            return "default";
        }
        List<String> tags = new ArrayList<>();
        for (RetrievalPriority value : retrievalPriority) {
            if (value != null) {
                tags.add(value.name().toLowerCase(Locale.ROOT));
            }
        }
        return tags.isEmpty() ? "default" : String.join(">", tags);
    }
}


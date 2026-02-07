package com.example.agent.runtime.summary;

import com.example.agent.runtime.summary.SummaryComputationModels.SummaryLimits;
import com.example.agent.runtime.summary.SummaryComputationModels.TruncationState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 输入摘要清理服务。
 *
 * <p>用途：对输入映射进行深度、长度、数量控制，避免摘要层注入超大对象。
 * <p>输入：任意输入值与摘要限制。
 * <p>输出：裁剪后的可序列化结构。
 * <p>边界：循环引用会输出占位并标记截断。
 */
@Service
public class SummaryInputSanitizer {

    /**
     * 清理输入值。
     *
     * @param value 输入值
     * @param limits 限制
     * @param truncation 截断状态
     * @param depth 深度
     * @param visited 访问记录
     * @return 清理后值
     */
    public Object sanitizeInputValue(Object value,
                                     SummaryLimits limits,
                                     TruncationState truncation,
                                     int depth,
                                     java.util.IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return null;
        }
        if (depth <= 0) {
            return SummaryDigestService.trimText(String.valueOf(value), limits, truncation);
        }
        if (value instanceof String text) {
            return SummaryDigestService.trimText(text, limits, truncation);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeInputMap(map, limits, truncation, depth, visited);
        }
        if (value instanceof List<?> list) {
            return sanitizeInputList(list, limits, truncation, depth, visited);
        }
        if (value.getClass().isArray()) {
            return sanitizeInputArray(value, limits, truncation, depth, visited);
        }
        return SummaryDigestService.trimText(String.valueOf(value), limits, truncation);
    }

    /**
     * 清理映射。
     *
     * @param map 映射
     * @param limits 限制
     * @param truncation 截断状态
     * @param depth 深度
     * @param visited 访问记录
     * @return 清理后映射
     */
    public Map<String, Object> sanitizeInputMap(Map<?, ?> map,
                                                SummaryLimits limits,
                                                TruncationState truncation,
                                                int depth,
                                                java.util.IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(map, Boolean.TRUE) != null) {
            truncation.markTruncated();
            return Map.of("value", "<circular>");
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        int index = 0;
        int maxItems = limits.getMaxListItems();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            String keyText = entry.getKey() == null ? "null" : String.valueOf(entry.getKey());
            keyText = SummaryDigestService.trimText(keyText, limits, truncation);
            Object cleaned = sanitizeInputValue(entry.getValue(), limits, truncation, depth - 1, visited);
            sanitized.put(keyText, cleaned);
            index++;
        }
        visited.remove(map);
        return sanitized;
    }

    /**
     * 清理列表。
     *
     * @param list 列表
     * @param limits 限制
     * @param truncation 截断状态
     * @param depth 深度
     * @param visited 访问记录
     * @return 清理后列表
     */
    public List<Object> sanitizeInputList(List<?> list,
                                          SummaryLimits limits,
                                          TruncationState truncation,
                                          int depth,
                                          java.util.IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(list, Boolean.TRUE) != null) {
            truncation.markTruncated();
            return List.of("<circular>");
        }
        List<Object> sanitized = new ArrayList<>();
        int index = 0;
        int maxItems = limits.getMaxListItems();
        for (Object item : list) {
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            sanitized.add(sanitizeInputValue(item, limits, truncation, depth - 1, visited));
            index++;
        }
        visited.remove(list);
        return sanitized;
    }

    /**
     * 清理数组。
     *
     * @param array 数组
     * @param limits 限制
     * @param truncation 截断状态
     * @param depth 深度
     * @param visited 访问记录
     * @return 清理后列表
     */
    public List<Object> sanitizeInputArray(Object array,
                                           SummaryLimits limits,
                                           TruncationState truncation,
                                           int depth,
                                           java.util.IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(array, Boolean.TRUE) != null) {
            truncation.markTruncated();
            return List.of("<circular>");
        }
        int length = java.lang.reflect.Array.getLength(array);
        int maxItems = limits.getMaxListItems();
        List<Object> sanitized = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            if (maxItems > 0 && i >= maxItems) {
                truncation.markTruncated();
                break;
            }
            Object item = java.lang.reflect.Array.get(array, i);
            sanitized.add(sanitizeInputValue(item, limits, truncation, depth - 1, visited));
        }
        visited.remove(array);
        return sanitized;
    }
}

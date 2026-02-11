package com.example.agent.orchestration.multiagent.support;

import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 步骤参数读取器。
 * <p>用途：统一解析 StepSpec 参数中的字符串、列表与映射结构，避免各调用点重复实现解析细节。</p>
 */
@Component
public class StepArgumentReader {

    /**
     * 读取字符串参数。
     *
     * @param step 步骤定义
     * @param key 参数键
     * @return 去除首尾空白后的值，不存在时返回 null
     */
    public String readString(StepSpec step, String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        Object value = readRaw(step, key);
        return normalizeText(value);
    }

    /**
     * 读取字符串列表参数。
     *
     * @param step 步骤定义
     * @param key 参数键
     * @return 去重后的字符串列表
     */
    public List<String> readStringList(StepSpec step, String key) {
        if (!StringUtils.hasText(key)) {
            return List.of();
        }
        Object value = readRaw(step, key);
        return normalizeStringList(value);
    }

    /**
     * 读取映射参数。
     *
     * @param step 步骤定义
     * @param key 参数键
     * @return 以字符串键导出的映射，非映射结构时返回空映射
     */
    public Map<String, Object> readMap(StepSpec step, String key) {
        if (!StringUtils.hasText(key)) {
            return Map.of();
        }
        Object value = readRaw(step, key);
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            // 关键逻辑：忽略空键，避免污染调用方语义。
            String normalizedKey = normalizeText(entry.getKey());
            if (!StringUtils.hasText(normalizedKey)) {
                continue;
            }
            result.put(normalizedKey, entry.getValue());
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    /**
     * 归一化任意对象为字符串列表。
     *
     * @param value 原始值
     * @return 去重、去空白后的列表
     */
    public List<String> normalizeStringList(Object value) {
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                // 关键逻辑：逐项归一化并去重，确保配置输入稳定。
                String text = normalizeText(item);
                if (StringUtils.hasText(text) && !result.contains(text)) {
                    result.add(text);
                }
            }
            return result.isEmpty() ? List.of() : List.copyOf(result);
        }
        String single = normalizeText(value);
        if (!StringUtils.hasText(single)) {
            return List.of();
        }
        return List.of(single);
    }

    /**
     * 读取原始参数值。
     */
    private Object readRaw(StepSpec step, String key) {
        if (step == null || step.getArguments() == null) {
            return null;
        }
        return step.getArguments().get(key);
    }

    /**
     * 归一化文本值。
     */
    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}


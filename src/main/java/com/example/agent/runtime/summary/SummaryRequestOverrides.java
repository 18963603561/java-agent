package com.example.agent.runtime.summary;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 摘要请求级覆盖配置。
 *
 * <p>用途：从步骤输入中解析摘要覆盖参数，仅作用于当前请求。</p>
 * <p>输入：步骤输入映射或嵌套 {@code context} 映射。</p>
 * <p>输出：摘要覆盖配置对象。</p>
 */
public final class SummaryRequestOverrides {

    /**
     * 是否启用摘要。
     */
    private final Boolean enabled;

    /**
     * 场景覆盖编码。
     */
    private final String scenario;

    /**
     * 摘要最大字符数。
     */
    private final Integer maxChars;

    /**
     * 列表最大条目数。
     */
    private final Integer maxListItems;

    /**
     * 摘要策略覆盖编码。
     */
    private final String strategy;

    private SummaryRequestOverrides(Boolean enabled,
                                    String scenario,
                                    Integer maxChars,
                                    Integer maxListItems,
                                    String strategy) {
        this.enabled = enabled;
        this.scenario = scenario;
        this.maxChars = maxChars;
        this.maxListItems = maxListItems;
        this.strategy = strategy;
    }

    /**
     * 从摘要构建输入解析覆盖配置。
     *
     * @param input 摘要构建输入
     * @return 覆盖配置或 {@code null}
     */
    public static SummaryRequestOverrides fromInput(StepSummaryBuildInput input) {
        // 判断输入是否为空，空时直接返回 null。
        if (input == null) {
            // 返回空对象，保持解析链路幂等。
            return null;
        }
        // 调用解析方法从步骤输入获取覆盖配置。
        return fromStepInput(input.getStepInput());
    }

    /**
     * 从步骤输入映射解析覆盖配置。
     *
     * @param stepInput 步骤输入映射
     * @return 覆盖配置或 {@code null}
     */
    public static SummaryRequestOverrides fromStepInput(Map<String, Object> stepInput) {
        // 解析覆盖配置映射。
        Map<String, Object> overrides = resolveOverrides(stepInput);
        // 判断覆盖映射是否为空，空时直接返回 null。
        if (overrides == null || overrides.isEmpty()) {
            // 返回空对象，避免空映射误判为覆盖。
            return null;
        }
        // 解析启用开关。
        Boolean enabled = resolveBoolean(overrides.get("enabled"));
        // 解析场景覆盖。
        String scenario = resolveString(overrides.get("scenario"));
        // 解析最大字符数。
        Integer maxChars = resolveInteger(overrides.get("maxChars"));
        // 解析列表最大条目数。
        Integer maxListItems = resolveInteger(overrides.get("maxListItems"));
        // 解析摘要策略覆盖。
        String strategy = resolveString(overrides.get("strategy"));
        // 判断是否全部为空，全部为空则返回 null。
        if (enabled == null
                && scenario == null
                && maxChars == null
                && maxListItems == null
                && strategy == null) {
            // 返回空对象，避免产生空覆盖配置。
            return null;
        }
        // 构建覆盖配置对象并返回。
        return new SummaryRequestOverrides(enabled, scenario, maxChars, maxListItems, strategy);
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String getScenario() {
        return scenario;
    }

    public Integer getMaxChars() {
        return maxChars;
    }

    public Integer getMaxListItems() {
        return maxListItems;
    }

    public String getStrategy() {
        return strategy;
    }

    private static Map<String, Object> resolveOverrides(Map<String, Object> stepInput) {
        // 设计意图：优先读取顶层覆盖，其次回退到 context，避免层级冲突。
        // 判断步骤输入是否为空，空时直接返回 null。
        if (stepInput == null || stepInput.isEmpty()) {
            // 返回空对象，避免空映射误判。
            return null;
        }
        // 解析顶层 summary 覆盖映射。
        Map<String, Object> direct = toStringKeyMap(stepInput.get("summary"));
        // 判断顶层覆盖是否有效，命中则直接返回。
        if (direct != null && !direct.isEmpty()) {
            // 返回顶层覆盖映射。
            return direct;
        }
        // 解析顶层 summaryOverrides 覆盖映射。
        direct = toStringKeyMap(stepInput.get("summaryOverrides"));
        // 判断顶层覆盖是否有效，命中则直接返回。
        if (direct != null && !direct.isEmpty()) {
            // 返回顶层覆盖映射。
            return direct;
        }
        // 解析内层 context 映射。
        Map<String, Object> context = toStringKeyMap(stepInput.get("context"));
        // 判断 context 是否为空，空时直接返回 null。
        if (context == null || context.isEmpty()) {
            // 返回空对象，避免空映射误判。
            return null;
        }
        // 解析 context.summary 覆盖映射。
        Map<String, Object> nested = toStringKeyMap(context.get("summary"));
        // 判断覆盖是否有效，命中则直接返回。
        if (nested != null && !nested.isEmpty()) {
            // 返回内层覆盖映射。
            return nested;
        }
        // 解析 context.summaryOverrides 覆盖映射。
        return toStringKeyMap(context.get("summaryOverrides"));
    }

    private static Map<String, Object> toStringKeyMap(Object value) {
        // 判断值是否为映射且非空，非映射时直接返回 null。
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            // 返回空对象，避免无效覆盖。
            return null;
        }
        // 初始化映射副本，隔离外部修改。
        Map<String, Object> copied = new LinkedHashMap<>();
        // 遍历映射条目，统一键为字符串。
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            // 写入条目键值，保持原始值。
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        // 返回转换后的映射。
        return copied;
    }

    private static Boolean resolveBoolean(Object value) {
        // 判断值是否为布尔类型，命中则直接返回。
        if (value instanceof Boolean boolValue) {
            // 返回布尔值。
            return boolValue;
        }
        // 判断值是否为字符串，字符串时尝试解析。
        if (value instanceof String text) {
            // 去除字符串首尾空白，统一解析口径。
            String trimmed = text.trim();
            // 判断字符串是否为空，空时返回 null。
            if (!StringUtils.hasText(trimmed)) {
                // 返回空对象，表示未提供覆盖。
                return null;
            }
            // 返回布尔解析结果。
            return Boolean.parseBoolean(trimmed);
        }
        // 返回空对象，表示无法解析。
        return null;
    }

    private static Integer resolveInteger(Object value) {
        // 判断值是否为数值类型，命中则直接返回。
        if (value instanceof Number number) {
            // 返回数值整数值。
            return number.intValue();
        }
        // 判断值是否为字符串，字符串时尝试解析。
        if (value instanceof String text) {
            // 去除字符串首尾空白，统一解析口径。
            String trimmed = text.trim();
            // 判断字符串是否为空，空时返回 null。
            if (!StringUtils.hasText(trimmed)) {
                // 返回空对象，表示未提供覆盖。
                return null;
            }
            try {
                // 尝试解析字符串为整数。
                return Integer.parseInt(trimmed);
            } catch (Exception ex) {
                // 异常处理：解析失败时返回 null，避免抛出异常。
                return null;
            }
        }
        // 返回空对象，表示无法解析。
        return null;
    }

    private static String resolveString(Object value) {
        // 判断值是否为字符串，非字符串时返回 null。
        if (!(value instanceof String text)) {
            // 返回空对象，表示未提供覆盖。
            return null;
        }
        // 去除字符串首尾空白，统一解析口径。
        String trimmed = text.trim();
        // 返回标准化后的字符串，空字符串返回 null。
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}

package com.example.agent.runtime.summary;

import org.springframework.util.StringUtils;

/**
 * 步骤级摘要构建策略。
 *
 * <p>用途：统一定义摘要构建策略编码，支持按全局、场景与请求级覆盖路由。</p>
 */
public enum SummaryBuildStrategy {

    /**
     * 语义摘要策略。
     */
    SEMANTIC("semantic"),

    /**
     * 模板摘要策略。
     */
    TEMPLATE("template"),

    /**
     * 模型摘要策略。
     */
    MODEL("model"),

    /**
     * 关闭摘要策略。
     */
    OFF("off");

    /**
     * 策略编码。
     */
    private final String code;

    SummaryBuildStrategy(String code) {
        this.code = code;
    }

    /**
     * 获取策略编码。
     *
     * @return 策略编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 解析策略编码。
     *
     * @param value 原始策略编码
     * @param fallback 兜底策略
     * @return 解析后的策略
     */
    public static SummaryBuildStrategy resolve(String value, SummaryBuildStrategy fallback) {
        // 判断编码是否为空，空时返回兜底策略。
        if (!StringUtils.hasText(value)) {
            // 返回兜底策略，保证解析稳定。
            return fallback != null ? fallback : SEMANTIC;
        }
        // 标准化编码文本，统一比较口径。
        String normalized = value.trim().toLowerCase();
        // 循环遍历策略枚举，匹配编码。
        for (SummaryBuildStrategy strategy : values()) {
            // 判断编码是否匹配，匹配时返回命中策略。
            if (strategy.code.equals(normalized)) {
                // 返回命中策略。
                return strategy;
            }
        }
        // 返回兜底策略，避免非法值导致异常。
        return fallback != null ? fallback : SEMANTIC;
    }
}


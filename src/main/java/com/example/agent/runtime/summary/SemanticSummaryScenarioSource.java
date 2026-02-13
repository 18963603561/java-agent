package com.example.agent.runtime.summary;

/**
 * 语义摘要场景来源枚举。
 *
 * <p>用途：标识场景命中的来源，便于日志与观测治理。</p>
 */
public enum SemanticSummaryScenarioSource {

    /**
     * 默认场景。
     */
    DEFAULT("default"),

    /**
     * 请求覆盖场景。
     */
    OVERRIDE("override"),

    /**
     * 规则推断场景。
     */
    INFERRED("inferred");

    /**
     * 来源编码。
     */
    private final String code;

    SemanticSummaryScenarioSource(String code) {
        this.code = code;
    }

    /**
     * 获取来源编码。
     *
     * @return 来源编码
     */
    public String getCode() {
        return code;
    }
}

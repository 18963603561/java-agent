package com.example.agent.runtime.summary;

/**
 * 摘要策略来源枚举。
 *
 * <p>用途：标识策略命中的来源，便于日志观测与问题定位。</p>
 */
public enum SummaryStrategySource {

    /**
     * 默认策略来源。
     */
    DEFAULT("default"),

    /**
     * 全局配置来源。
     */
    GLOBAL("global"),

    /**
     * 场景策略来源。
     */
    SCENARIO("scenario"),

    /**
     * 请求级覆盖来源。
     */
    OVERRIDE("override");

    /**
     * 来源编码。
     */
    private final String code;

    SummaryStrategySource(String code) {
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


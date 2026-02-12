package com.example.agent.capabilities.context.compression.observability;

/**
 * 压缩指标键常量。
 *
 * <p>用途：统一维护压缩链路指标名，避免散落字符串导致统计口径漂移。</p>
 */
public final class CompressionMetricKeys {

    private CompressionMetricKeys() {
    }

    /**
     * 压缩冷却跳过计数。
     */
    public static final String CONTEXT_COMPRESSION_SKIPPED_TOTAL = "context_compression_skipped_total";

    /**
     * 压缩失败计数。
     */
    public static final String CONTEXT_COMPRESSION_FAILED_TOTAL = "context_compression_failed_total";

    /**
     * 压缩成功计数。
     */
    public static final String CONTEXT_COMPRESSION_SUCCESS_TOTAL = "context_compression_success_total";

    /**
     * 压缩后仍超预算计数。
     */
    public static final String CONTEXT_COMPRESSION_STILL_OVER_BUDGET_TOTAL = "context_compression_still_over_budget_total";

    /**
     * 压缩触发计数。
     */
    public static final String CONTEXT_COMPRESSION_TRIGGER_TOTAL = "context_compression_trigger_total";

    /**
     * 窗口整形计数。
     */
    public static final String CONTEXT_COMPRESSION_SHAPE_TOTAL = "context_compression_shape_total";

    /**
     * 摘要注入计数。
     */
    public static final String CONTEXT_COMPRESSION_SUMMARY_INJECTION_TOTAL = "context_compression_summary_injection_total";

    /**
     * 摘要注入策略计数。
     */
    public static final String CONTEXT_SUMMARY_INJECTION_TOTAL = "context_summary_injection_total";

    /**
     * 压缩阶段观测计数。
     */
    public static final String CONTEXT_COMPRESSION_OBSERVATION_TOTAL = "context_compression_observation_total";

    /**
     * 压缩耗时分布。
     */
    public static final String CONTEXT_COMPRESSION_DURATION_MS = "context_compression_duration_ms";
}


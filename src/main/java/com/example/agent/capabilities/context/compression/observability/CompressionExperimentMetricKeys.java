package com.example.agent.capabilities.context.compression.observability;

/**
 * 压缩实验指标键常量。
 */
public final class CompressionExperimentMetricKeys {

    private CompressionExperimentMetricKeys() {
    }

    /**
     * 双轨开启次数。
     */
    public static final String CONTEXT_COMPRESSION_DUAL_TRACK_TOTAL = "context_compression_dual_track_total";

    /**
     * 对比完成次数。
     */
    public static final String CONTEXT_COMPRESSION_COMPARISON_TOTAL = "context_compression_comparison_total";

    /**
     * 自动回滚次数。
     */
    public static final String CONTEXT_COMPRESSION_ROLLBACK_TOTAL = "context_compression_rollback_total";

    /**
     * 胜出来源分布次数。
     */
    public static final String CONTEXT_COMPRESSION_WINNER_TOTAL = "context_compression_winner_total";
}

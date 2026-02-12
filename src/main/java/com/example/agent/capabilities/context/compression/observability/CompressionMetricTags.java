package com.example.agent.capabilities.context.compression.observability;

/**
 * 压缩指标标签键常量。
 *
 * <p>用途：统一维护压缩链路标签键，保障多维指标字段命名一致。</p>
 */
public final class CompressionMetricTags {

    private CompressionMetricTags() {
    }

    /**
     * 标签键：来源。
     */
    public static final String SOURCE = "source";

    /**
     * 标签键：原因。
     */
    public static final String REASON = "reason";

    /**
     * 标签键：是否命中冷却。
     */
    public static final String COOLDOWN = "cooldown";

    /**
     * 标签键：是否整形。
     */
    public static final String SHAPED = "shaped";

    /**
     * 标签键：是否注入。
     */
    public static final String INJECTED = "injected";

    /**
     * 标签键：阶段。
     */
    public static final String STAGE = "stage";

    /**
     * 标签键：是否成功。
     */
    public static final String SUCCESS = "success";

    /**
     * 标签键：是否降级。
     */
    public static final String FALLBACK = "fallback";

    /**
     * 标签键：是否双轨。
     */
    public static final String DUAL_TRACK = "dual_track";

    /**
     * 标签键：灰度版本。
     */
    public static final String ROLLOUT_VERSION = "rollout_version";

    /**
     * 标签键：主轨来源。
     */
    public static final String PRIMARY_SOURCE = "primary_source";

    /**
     * 标签键：影子轨来源。
     */
    public static final String SHADOW_SOURCE = "shadow_source";

    /**
     * 标签键：胜出来源。
     */
    public static final String WINNER = "winner";

    /**
     * 标签键：回滚原因。
     */
    public static final String ROLLBACK_REASON = "rollback_reason";
}
